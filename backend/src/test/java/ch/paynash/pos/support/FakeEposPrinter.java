package ch.paynash.pos.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * An Epson printer's ePOS-Print endpoint on localhost. Answers success unless told otherwise and records every
 * request body.
 */
public class FakeEposPrinter implements AutoCloseable {

	public record Answer(int status, String body, Duration delay) {
	}

	public static final String SUCCESS = response(true, "");

	public final List<String> requests = new CopyOnWriteArrayList<>();

	public final List<String> queries = new CopyOnWriteArrayList<>();

	private final ConcurrentLinkedDeque<Answer> answers = new ConcurrentLinkedDeque<>();

	private final HttpServer server;

	public FakeEposPrinter() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/cgi-bin/epos/service.cgi", exchange -> {
			requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			queries.add(exchange.getRequestURI().getRawQuery());
			Answer answer = answers.isEmpty() ? new Answer(200, SUCCESS, Duration.ZERO) : answers.poll();
			try {
				Thread.sleep(answer.delay());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
			exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
			catch (IOException ex) {
				// The client gave up waiting.
			}
		});
		server.start();
	}

	/** host:port, as entered in the printer configuration. */
	public String address() {
		return "127.0.0.1:" + server.getAddress().getPort();
	}

	public void answer(String body) {
		answers.add(new Answer(200, body, Duration.ZERO));
	}

	public void answer(Answer answer) {
		answers.add(answer);
	}

	/** An ePOS-Print response as the TM-m30III sends it. */
	public static String response(boolean success, String code) {
		return """
				<?xml version="1.0" encoding="utf-8"?>
				<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>\
				<response success="%s" code="%s" status="251658262" battery="0" \
				xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print"></response></s:Body></s:Envelope>"""
			.formatted(success, code);
	}

	@Override
	public void close() {
		server.stop(0);
	}

}
