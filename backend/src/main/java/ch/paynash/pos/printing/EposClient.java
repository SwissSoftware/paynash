package ch.paynash.pos.printing;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Sends a ticket to an Epson printer with ePOS-Print XML over HTTP (design D3): {@code POST
 * /cgi-bin/epos/service.cgi?devid=...&timeout=...} with a SOAP envelope, and reads {@code success} and {@code code}
 * from the answer.
 */
@Component
public class EposClient {

	/**
	 * What happened to a ticket (design D1). {@link NotPrinted}: the printer certainly printed nothing and will be able
	 * to later, so waiting is safe. {@link Unclear}: it may have printed, so it must not be sent again automatically.
	 * {@link Failed}: it certainly did not print and cannot without a configuration change.
	 */
	public sealed interface Result {

		/** The error to show, or null when printed. */
		String error();

		record Printed() implements Result {

			@Override
			public String error() {
				return null;
			}

		}

		record NotPrinted(String error) implements Result {
		}

		record Unclear(String error) implements Result {
		}

		record Failed(String error) implements Result {
		}

	}

	static final String OFFLINE = "printer offline";

	static final String NO_ANSWER = "no answer from printer; check the ticket and reprint if needed";

	static final String CHECK = "; check the ticket and reprint if needed";

	private static final Map<String, Result> CODES = Map.ofEntries(
			// The printer did not start printing and recovers once staff fix it.
			Map.entry("EPTR_REC_EMPTY", new Result.NotPrinted("paper end")),
			Map.entry("EPTR_COVER_OPEN", new Result.NotPrinted("cover open")),
			// The printer may have printed part or all of the ticket.
			Map.entry("EPTR_CUTTER", new Result.Unclear("cutter error" + CHECK)),
			Map.entry("EPTR_MECHANICAL", new Result.Unclear("mechanical error" + CHECK)),
			Map.entry("EPTR_AUTOMATICAL", new Result.Unclear("printer error (automatic recovery)" + CHECK)),
			Map.entry("EPTR_UNRECOVERABLE", new Result.Unclear("printer error (unrecoverable)" + CHECK)),
			Map.entry("PrintSystemError", new Result.Unclear("print system error" + CHECK)),
			Map.entry("EX_BADPORT", new Result.Unclear("printer port error" + CHECK)),
			Map.entry("EX_TIMEOUT", new Result.Unclear("printer timeout" + CHECK)),
			Map.entry("EX_SPOOLER", new Result.Unclear("printer spooler error" + CHECK)),
			Map.entry("EX_ENPC_TIMEOUT", new Result.Unclear("printer timeout" + CHECK)),
			// Rejected before printing because of the configuration or the data.
			Map.entry("DeviceNotFound", new Result.Failed("device ID not found on the printer")),
			Map.entry("SchemaError", new Result.Failed("invalid print data")));

	private final HttpClient http;

	private final Duration eposTimeout;

	private final Duration connectTimeout;

	public EposClient(@Value("${pos.printing.connect-timeout:3s}") Duration connectTimeout,
			@Value("${pos.printing.epos-timeout:10s}") Duration eposTimeout) {
		this.http = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		this.eposTimeout = eposTimeout;
		this.connectTimeout = connectTimeout;
	}

	/**
	 * Whether the printer accepts a connection, without printing anything (design D4, baseline). Used to watch idle
	 * printers.
	 */
	public boolean reachable(Printer printer) {
		URI uri = uri(printer);
		int port = uri.getPort() != -1 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80;
		try (var socket = new Socket()) {
			socket.connect(new InetSocketAddress(uri.getHost(), port), (int) connectTimeout.toMillis());
			return true;
		}
		catch (IOException | IllegalArgumentException ex) {
			return false;
		}
	}

	public Result print(Printer printer, Ticket ticket) {
		HttpRequest request = HttpRequest.newBuilder(uri(printer))
			// The printer answers within its own timeout; the margin covers the network.
			.timeout(eposTimeout.plus(eposTimeout.dividedBy(2)))
			.header("Content-Type", "text/xml; charset=utf-8")
			.header("SOAPAction", "\"\"")
			.POST(HttpRequest.BodyPublishers.ofString(envelope(ticket), StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (HttpConnectTimeoutException | ConnectException | UnresolvedAddressException ex) {
			return new Result.NotPrinted(OFFLINE);
		}
		catch (HttpTimeoutException ex) {
			return new Result.Unclear(NO_ANSWER);
		}
		catch (IOException ex) {
			// The connection broke after it was opened: the ticket may or may not have printed.
			return ex.getCause() instanceof ConnectException || ex.getCause() instanceof UnresolvedAddressException
					? new Result.NotPrinted(OFFLINE) : new Result.Unclear(NO_ANSWER);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new Result.Unclear(NO_ANSWER);
		}
		if (response.statusCode() == 404) {
			return new Result.Failed("ePOS-Print is not enabled on the printer");
		}
		if (response.statusCode() != 200) {
			return new Result.Unclear("printer answered HTTP " + response.statusCode() + CHECK);
		}
		return parse(response.body());
	}

	static Result parse(String body) {
		Element answer;
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setNamespaceAware(true);
			NodeList found = factory.newDocumentBuilder()
				.parse(new InputSource(new StringReader(body)))
				.getElementsByTagNameNS("*", "response");
			answer = found.getLength() == 0 ? null : (Element) found.item(0);
		}
		catch (Exception ex) {
			answer = null;
		}
		if (answer == null) {
			return new Result.Unclear(NO_ANSWER);
		}
		if ("true".equals(answer.getAttribute("success"))) {
			return new Result.Printed();
		}
		String code = answer.getAttribute("code");
		return CODES.getOrDefault(code, new Result.Unclear("printer error " + code + CHECK));
	}

	URI uri(Printer printer) {
		String base = printer.address().trim();
		if (!base.startsWith("http://") && !base.startsWith("https://")) {
			base = "http://" + base;
		}
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return URI.create(base + "/cgi-bin/epos/service.cgi?devid="
				+ URLEncoder.encode(printer.deviceId(), StandardCharsets.UTF_8) + "&timeout=" + eposTimeout.toMillis());
	}

	/** The ePOS-Print document: text rows, large rows in double width and height, then feed and cut. */
	static String envelope(Ticket ticket) {
		var sb = new StringBuilder("""
				<?xml version="1.0" encoding="utf-8"?>
				<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>\
				<epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print">\
				<text lang="en" smooth="true"/>""");
		Boolean large = null;
		for (Ticket.Row row : ticket.rows()) {
			if (large == null || large != row.large()) {
				large = row.large();
				sb.append("<text dw=\"").append(large).append("\" dh=\"").append(large).append("\"/>");
			}
			sb.append("<text>").append(escape(row.text())).append("&#10;</text>");
		}
		return sb.append("<feed line=\"3\"/><cut type=\"feed\"/></epos-print></s:Body></s:Envelope>").toString();
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}

}
