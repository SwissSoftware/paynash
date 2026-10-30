package ch.paynash.pos.printing;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
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

	/** Whether retrying is safe: only when the printer cannot have printed anything. */
	public sealed interface Result {

		record Printed() implements Result {
		}

		record Failed(String error, boolean retryable) implements Result {
		}

	}

	static final String OFFLINE = "printer offline";

	static final String NO_ANSWER = "no answer from printer; check the ticket and reprint if needed";

	/** Errors where the printer did not start printing, so a retry cannot duplicate the ticket. */
	private static final Map<String, Result.Failed> CODES = Map.ofEntries(
			Map.entry("EPTR_REC_EMPTY", new Result.Failed("paper end", true)),
			Map.entry("EPTR_COVER_OPEN", new Result.Failed("cover open", true)),
			Map.entry("EPTR_CUTTER", new Result.Failed("cutter error", false)),
			Map.entry("EPTR_MECHANICAL", new Result.Failed("mechanical error", false)),
			Map.entry("EPTR_AUTOMATICAL", new Result.Failed("printer error (automatic recovery)", false)),
			Map.entry("EPTR_UNRECOVERABLE", new Result.Failed("printer error (unrecoverable)", false)),
			Map.entry("DeviceNotFound", new Result.Failed("device ID not found on the printer", false)),
			Map.entry("SchemaError", new Result.Failed("invalid print data", false)),
			Map.entry("PrintSystemError", new Result.Failed("print system error", false)),
			Map.entry("EX_BADPORT", new Result.Failed("printer port error", false)),
			Map.entry("EX_TIMEOUT", new Result.Failed("printer timeout; check the ticket and reprint if needed", false)),
			Map.entry("EX_SPOOLER", new Result.Failed("printer spooler error", false)),
			Map.entry("EX_ENPC_TIMEOUT", new Result.Failed("printer timeout; check the ticket and reprint if needed", false)));

	private final HttpClient http;

	private final Duration eposTimeout;

	public EposClient(@Value("${pos.printing.connect-timeout:3s}") Duration connectTimeout,
			@Value("${pos.printing.epos-timeout:10s}") Duration eposTimeout) {
		this.http = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();
		this.eposTimeout = eposTimeout;
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
			return new Result.Failed(OFFLINE, true);
		}
		catch (HttpTimeoutException ex) {
			return new Result.Failed(NO_ANSWER, false);
		}
		catch (IOException ex) {
			// The connection broke after it was opened: the ticket may or may not have printed.
			return ex.getCause() instanceof ConnectException || ex.getCause() instanceof UnresolvedAddressException
					? new Result.Failed(OFFLINE, true) : new Result.Failed(NO_ANSWER, false);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new Result.Failed(NO_ANSWER, false);
		}
		if (response.statusCode() == 404) {
			return new Result.Failed("ePOS-Print is not enabled on the printer", false);
		}
		if (response.statusCode() != 200) {
			return new Result.Failed("printer answered HTTP " + response.statusCode(), false);
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
			return new Result.Failed(NO_ANSWER, false);
		}
		if ("true".equals(answer.getAttribute("success"))) {
			return new Result.Printed();
		}
		String code = answer.getAttribute("code");
		return CODES.getOrDefault(code, new Result.Failed("printer error " + code, false));
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
