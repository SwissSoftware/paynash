package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.EposClient.Result;
import ch.paynash.pos.support.FakeEposPrinter;

/** The ePOS-Print document and how printer answers are understood (design D3). */
class EposClientTest {

	private final EposClient client = new EposClient(Duration.ofSeconds(1), Duration.ofMillis(400));

	private static Printer printer(String address) {
		return new Printer(1, "Bar", address, "local_printer", ItemType.DRINK, true);
	}

	@Test
	void buildsTheEposDocumentWithLargeRowsFeedAndCut() {
		var ticket = new Ticket(List.of(new Ticket.Row(true, "Table 12"), new Ticket.Row(true, "1 x Käse & <Brot>"),
				new Ticket.Row(false, "----")));

		assertThat(EposClient.envelope(ticket)).isEqualTo("""
				<?xml version="1.0" encoding="utf-8"?>
				<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>\
				<epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print">\
				<text lang="en" smooth="true"/>\
				<text dw="true" dh="true"/><text>Table 12&#10;</text><text>1 x Käse &amp; &lt;Brot&gt;&#10;</text>\
				<text dw="false" dh="false"/><text>----&#10;</text>\
				<feed line="3"/><cut type="feed"/></epos-print></s:Body></s:Envelope>""");
	}

	@Test
	void addressesTheServiceWithDeviceIdAndTimeout() {
		assertThat(client.uri(printer("192.168.1.50")))
			.hasToString("http://192.168.1.50/cgi-bin/epos/service.cgi?devid=local_printer&timeout=400");
		assertThat(client.uri(printer("https://bar.local:8443/")))
			.hasToString("https://bar.local:8443/cgi-bin/epos/service.cgi?devid=local_printer&timeout=400");
	}

	@Test
	void understandsRecordedAnswers() {
		assertThat(EposClient.parse(FakeEposPrinter.response(true, ""))).isInstanceOf(Result.Printed.class);
		assertThat(EposClient.parse(FakeEposPrinter.response(false, "EPTR_REC_EMPTY")))
			.isEqualTo(new Result.Failed("paper end", true));
		assertThat(EposClient.parse(FakeEposPrinter.response(false, "EPTR_COVER_OPEN")))
			.isEqualTo(new Result.Failed("cover open", true));
		assertThat(EposClient.parse(FakeEposPrinter.response(false, "DeviceNotFound")))
			.isEqualTo(new Result.Failed("device ID not found on the printer", false));
		assertThat(EposClient.parse(FakeEposPrinter.response(false, "EPTR_CUTTER")))
			.isEqualTo(new Result.Failed("cutter error", false));
		assertThat(EposClient.parse(FakeEposPrinter.response(false, "SOMETHING_NEW")))
			.isEqualTo(new Result.Failed("printer error SOMETHING_NEW", false));
		assertThat(EposClient.parse("<html>not epos</html>")).isEqualTo(new Result.Failed(EposClient.NO_ANSWER, false));
		assertThat(EposClient.parse("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><response>&e;</response>"))
			.isEqualTo(new Result.Failed(EposClient.NO_ANSWER, false));
	}

	@Test
	void unreachablePrinterIsOfflineAndSafeToRetry() {
		assertThat(client.print(printer("127.0.0.1:1"), ticket()))
			.isEqualTo(new Result.Failed(EposClient.OFFLINE, true));
	}

	@Test
	void sendsTheDocumentAndReportsTheAnswer() throws Exception {
		try (var fake = new FakeEposPrinter()) {
			fake.answer(FakeEposPrinter.response(false, "EPTR_REC_EMPTY"));

			assertThat(client.print(printer(fake.address()), ticket())).isEqualTo(new Result.Failed("paper end", true));
			assertThat(client.print(printer(fake.address()), ticket())).isInstanceOf(Result.Printed.class);
			assertThat(fake.requests).hasSize(2).allSatisfy(r -> assertThat(r).contains("<text>Table 1&#10;</text>"));
			assertThat(fake.queries).allSatisfy(q -> assertThat(q).isEqualTo("devid=local_printer&timeout=400"));
		}
	}

	@Test
	void missingAnswerIsUnclearAndNotRetried() throws Exception {
		try (var fake = new FakeEposPrinter()) {
			fake.answer(new FakeEposPrinter.Answer(200, FakeEposPrinter.SUCCESS, Duration.ofSeconds(2)));

			assertThat(client.print(printer(fake.address()), ticket()))
				.isEqualTo(new Result.Failed(EposClient.NO_ANSWER, false));
		}
	}

	@Test
	void eposDisabledIsReported() throws Exception {
		try (var fake = new FakeEposPrinter()) {
			fake.answer(new FakeEposPrinter.Answer(404, "", Duration.ZERO));

			assertThat(client.print(printer(fake.address()), ticket()))
				.isEqualTo(new Result.Failed("ePOS-Print is not enabled on the printer", false));
		}
	}

	private static Ticket ticket() {
		return new Ticket(List.of(new Ticket.Row(true, "Table 1")));
	}

}
