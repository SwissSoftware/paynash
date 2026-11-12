package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.support.FakeEposPrinter;
import ch.paynash.pos.support.FakeEposPrinter.Fault;

/**
 * Every printer fault ends in the status that keeps the guarantee "no ticket lost or printed twice unnoticed" (spec
 * ticket-printing, "Printers on WiFi"; design D1, D11): nothing printed waits, maybe printed is unclear and never sent
 * again, configuration errors fail.
 */
@TestPropertySource(properties = "pos.printing.epos-timeout=300ms")
class PrinterFaultTest extends PrintingTestSupport {

	private String waiter;

	private long cola;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		cola = item("Drinks", "Cola", ItemType.DRINK);
	}

	@Test
	void unreachablePrinterWaits() throws Exception {
		printer("Bar", OFFLINE, ItemType.DRINK);
		assertDelivery(null, PrintJob.Status.PENDING, "printer offline");
	}

	@Test
	void connectionDroppedBeforeTheTicketWasReadIsUnclear() throws Exception {
		// The server cannot tell this from a drop after reading, so it must not risk a second ticket.
		assertDelivery(fake -> fake.fault(Fault.DROP_BEFORE_READ), PrintJob.Status.UNCLEAR, EposClient.NO_ANSWER);
	}

	@Test
	void connectionDroppedAfterTheTicketWasReadIsUnclear() throws Exception {
		assertDelivery(fake -> fake.fault(Fault.DROP_AFTER_READ), PrintJob.Status.UNCLEAR, EposClient.NO_ANSWER);
	}

	@Test
	void slowAnswerIsUnclear() throws Exception {
		assertDelivery(fake -> fake.answer(new FakeEposPrinter.Answer(200, FakeEposPrinter.SUCCESS,
				Duration.ofSeconds(1))), PrintJob.Status.UNCLEAR, EposClient.NO_ANSWER);
	}

	@Test
	void paperEndWaits() throws Exception {
		assertDelivery(fake -> fake.paperEnd(true), PrintJob.Status.PENDING, "paper end");
	}

	@Test
	void coverOpenWaits() throws Exception {
		assertDelivery(fake -> fake.answer(FakeEposPrinter.response(false, "EPTR_COVER_OPEN")),
				PrintJob.Status.PENDING, "cover open");
	}

	@Test
	void serverErrorIsUnclear() throws Exception {
		assertDelivery(fake -> fake.answer(new FakeEposPrinter.Answer(500, "", Duration.ZERO)),
				PrintJob.Status.UNCLEAR, "printer answered HTTP 500" + EposClient.CHECK);
	}

	@Test
	void wrongDeviceIdFails() throws Exception {
		assertDelivery(fake -> fake.answer(FakeEposPrinter.response(false, "DeviceNotFound")),
				PrintJob.Status.FAILED, "device ID not found on the printer");
	}

	/**
	 * Sends one drink ticket with the fault set up, runs the worker, and checks the status. An unclear ticket must not
	 * reach the printer again even after minutes.
	 */
	private void assertDelivery(Consumer<FakeEposPrinter> setUp, PrintJob.Status expected, String message)
			throws Exception {
		FakeEposPrinter bar = null;
		if (setUp != null) {
			bar = fakePrinter();
			printer("Bar", bar.address(), ItemType.DRINK);
			setUp.accept(bar);
		}
		long id = sendOrder(waiter, "12", line(cola, 1));

		worker.deliverDue();

		PrintJob job = job(id, ItemType.DRINK);
		assertThat(job.status()).isEqualTo(expected);
		assertThat(expected == PrintJob.Status.PENDING ? job.waitingReason() : job.lastError()).isEqualTo(message);
		if (bar != null && expected != PrintJob.Status.PENDING) {
			int requests = bar.requests.size();
			bar.fault(Fault.NONE);
			clock.advance(Duration.ofMinutes(5));
			worker.deliverDue();
			assertThat(bar.requests).as("never sent again automatically").hasSize(requests);
		}
	}

}
