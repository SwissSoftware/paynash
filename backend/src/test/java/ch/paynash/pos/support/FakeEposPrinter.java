package ch.paynash.pos.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import com.sun.net.httpserver.HttpServer;

/**
 * An Epson printer's ePOS-Print endpoint on localhost. Answers success unless told otherwise and records every
 * request body. Faults can be injected: slow answers, paper end, and connections dropped before or after the ticket
 * was read (harden-printing-for-events D11).
 */
public class FakeEposPrinter implements AutoCloseable {

	public record Answer(int status, String body, Duration delay) {
	}

	/** A broken connection: before the printer read the ticket, or after it read (and maybe printed) it. */
	public enum Fault {

		NONE, DROP_BEFORE_READ, DROP_AFTER_READ

	}

	public static final String SUCCESS = response(true, "");

	public final List<String> requests = new CopyOnWriteArrayList<>();

	public final List<String> queries = new CopyOnWriteArrayList<>();

	/** Requests the printer answered with success, i.e. tickets that came out. */
	private final List<String> printed = new CopyOnWriteArrayList<>();

	private volatile boolean paperEnd;

	private volatile Runnable onRequest = () -> {
	};

	/** Requests the printer may have printed: everything it read except explicit "nothing printed" answers. */
	private final List<String> possiblyPrinted = new CopyOnWriteArrayList<>();

	private final AtomicInteger requestCount = new AtomicInteger();

	/** Decides the fault for the n-th request (1-based). */
	private volatile IntFunction<Fault> faults = n -> Fault.NONE;

	private final ConcurrentLinkedDeque<Answer> answers = new ConcurrentLinkedDeque<>();

	private final HttpServer server;

	public FakeEposPrinter() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/cgi-bin/epos/service.cgi", exchange -> {
			Fault fault = faults.apply(requestCount.incrementAndGet());
			if (fault == Fault.DROP_BEFORE_READ) {
				exchange.close();
				return;
			}
			String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			requests.add(request);
			if (fault == Fault.DROP_AFTER_READ) {
				possiblyPrinted.add(request);
				exchange.close();
				return;
			}
			queries.add(exchange.getRequestURI().getRawQuery());
			onRequest.run();
			Answer answer = paperEnd ? new Answer(200, response(false, "EPTR_REC_EMPTY"), Duration.ZERO)
					: answers.isEmpty() ? new Answer(200, SUCCESS, Duration.ZERO) : answers.poll();
			if (answer.status() == 200 && answer.body().contains("success=\"true\"")) {
				printed.add(request);
			}
			if (!(answer.body().contains("EPTR_REC_EMPTY") || answer.body().contains("EPTR_COVER_OPEN"))) {
				possiblyPrinted.add(request);
			}
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

	/** While on, every request is answered with paper end. */
	public void paperEnd(boolean on) {
		paperEnd = on;
	}

	/** Runs when a request arrives, before it is answered. */
	public void onRequest(Runnable action) {
		onRequest = action;
	}

	public List<String> printedTickets() {
		return List.copyOf(printed);
	}

	public List<String> possiblyPrintedTickets() {
		return List.copyOf(possiblyPrinted);
	}

	/** Sets the fault for every following request, by its number since the printer started (1-based). */
	public void faults(IntFunction<Fault> plan) {
		faults = plan;
	}

	public void fault(Fault fault) {
		faults = n -> fault;
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
