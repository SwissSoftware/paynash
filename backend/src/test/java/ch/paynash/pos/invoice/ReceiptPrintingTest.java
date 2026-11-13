package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintJobRepository;
import ch.paynash.pos.printing.PrintPurpose;
import ch.paynash.pos.printing.PrintWorker;
import ch.paynash.pos.printing.PrinterRepository;
import ch.paynash.pos.support.FakeEposPrinter;

/** Receipts go through the print outbox with the same guarantees as tickets (spec ticket-printing, design D8). */
class ReceiptPrintingTest extends InvoiceTestSupport {

	@Autowired
	PrinterRepository printers;

	@Autowired
	PrintJobRepository jobs;

	@Autowired
	PrintWorker worker;

	private final List<FakeEposPrinter> fakes = new ArrayList<>();

	@AfterEach
	void stopFakes() {
		fakes.forEach(FakeEposPrinter::close);
	}

	@Test
	void issuingPrintsTheReceiptOnce() throws Exception {
		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		long invoice = id(issue(staff, confirm(laura, line(facial, 1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.receipt.status").value("pending"))
			.andExpect(jsonPath("$.receipt.type").value("receipt")));

		runFor(Duration.ofMinutes(2));

		assertThat(counter.printedTickets()).singleElement().satisfies(r -> {
			assertThat(r).contains("Rechnung 2026-0001").contains("Laura Meier").contains("120.00");
		});
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.receipt.status").value("printed"))
			.andExpect(jsonPath("$.receipt.label").value("COUNTER 0001"));
	}

	@Test
	void paperEndWaitsAndPrintsAfterRecovery() throws Exception {
		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		counter.paperEnd(true);
		long invoice = issued(confirm(laura, line(facial, 1)));
		runFor(Duration.ofMinutes(3));
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.receipt.status").value("pending"))
			.andExpect(jsonPath("$.receipt.waitingReason").value("paper end"));

		counter.paperEnd(false);
		runFor(Duration.ofSeconds(20));

		assertThat(counter.printedTickets()).hasSize(1);
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.receipt.status").value("printed"));
	}

	@Test
	void unclearReceiptIsNotSentAgainUntilACopyIsPrinted() throws Exception {
		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		counter.fault(FakeEposPrinter.Fault.DROP_AFTER_READ);
		long invoice = issued(confirm(laura, line(facial, 1)));
		runFor(Duration.ofMinutes(2));
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.receipt.status").value("unclear"));
		int requests = counter.requests.size();
		runFor(Duration.ofMinutes(2));
		assertThat(counter.requests).hasSize(requests);

		counter.fault(FakeEposPrinter.Fault.NONE);
		as(staff, post("/invoices/" + invoice + "/receipt")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("copy"));
		runFor(Duration.ofSeconds(20));

		assertThat(counter.printedTickets()).last().satisfies(r -> assertThat(r).contains("KOPIE"));
		assertThat(jobs.findByInvoice(invoice)).first().satisfies(j -> {
			assertThat(j.status()).isEqualTo(PrintJob.Status.UNCLEAR);
			assertThat(j.resolution()).isEqualTo(PrintJob.Resolution.REPRINT);
		});
	}

	@Test
	void copyIsMarkedKopieAndCancelledCopyStorniert() throws Exception {
		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		long invoice = issued(confirm(laura, line(facial, 1)));
		runFor(Duration.ofSeconds(10));
		as(staff, post("/invoices/" + invoice + "/receipt")).andExpect(status().isCreated());
		runFor(Duration.ofSeconds(10));
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong service"))
			.andExpect(status().isOk());
		as(staff, post("/invoices/" + invoice + "/receipt")).andExpect(status().isCreated());
		runFor(Duration.ofSeconds(10));

		List<String> printed = counter.printedTickets();
		assertThat(printed).hasSize(3);
		assertThat(printed.get(0)).doesNotContain("KOPIE");
		assertThat(printed.get(1)).contains("KOPIE").doesNotContain("STORNIERT");
		assertThat(printed.get(2)).contains("KOPIE").contains("STORNIERT").contains("wrong service");
	}

	@Test
	void copyIsRejectedWhileTheReceiptIsStillWaiting() throws Exception {
		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		counter.paperEnd(true);
		long invoice = issued(confirm(laura, line(facial, 1)));
		as(staff, post("/invoices/" + invoice + "/receipt")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The receipt is still being printed"));
	}

	@Test
	void noReceiptPrinterStillIssuesAndShowsTheError() throws Exception {
		long invoice = id(issue(staff, confirm(laura, line(facial, 1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.number").value("2026-0001"))
			.andExpect(jsonPath("$.receipt.status").value("failed"))
			.andExpect(jsonPath("$.receipt.error").value("no printer configured")));

		var counter = fake();
		printers.insert("Counter", counter.address(), "local_printer", PrintPurpose.RECEIPT, true);
		as(staff, post("/invoices/" + invoice + "/receipt")).andExpect(status().isCreated());
		runFor(Duration.ofSeconds(10));
		assertThat(counter.printedTickets()).singleElement().satisfies(r -> assertThat(r).contains("KOPIE"));
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.receipt.status").value("printed"));
	}

	private FakeEposPrinter fake() throws Exception {
		var f = new FakeEposPrinter();
		fakes.add(f);
		return f;
	}

	private void runFor(Duration duration) {
		Instant end = clock.instant().plus(duration);
		while (clock.instant().isBefore(end)) {
			worker.deliverDue();
			clock.advance(Duration.ofSeconds(1));
		}
		worker.deliverDue();
	}

}
