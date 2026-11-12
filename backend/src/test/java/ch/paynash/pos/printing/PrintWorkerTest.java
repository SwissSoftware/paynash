package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.support.FakeEposPrinter;

/**
 * Delivery of queued tickets against a fake printer (spec ticket-printing, "Print status, retry, and failure
 * visibility", "Wait for the printer instead of failing", "Crash-safe delivery", and "No duplicate production
 * tickets").
 */
@TestPropertySource(properties = "pos.printing.epos-timeout=300ms")
class PrintWorkerTest extends PrintingTestSupport {

	private String waiter;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		cola = item("Drinks", "Cola", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
	}

	@Test
	void printedTicketIsSentExactlyOnce() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		long id = sendOrder(waiter, "12", line(cola, 2));

		worker.deliverDue();
		clock.advance(Duration.ofMinutes(5));
		worker.deliverDue();

		assertThat(bar.requests).singleElement().satisfies(r -> assertThat(r).contains("2 x Cola"));
		PrintJob job = job(id, ItemType.DRINK);
		assertThat(job.status()).isEqualTo(PrintJob.Status.PRINTED);
		assertThat(job.attempts()).isEqualTo(1);
	}

	@Test
	void offlinePrinterIsWaitedForWithoutLimitAndPrintsOnceReachable() throws Exception {
		long kitchenId = printer("Kitchen", OFFLINE, ItemType.FOOD);
		long id = sendOrder(waiter, "12", line(pizza, 1));

		runFor(Duration.ofMinutes(10));

		PrintJob job = job(id, ItemType.FOOD);
		assertThat(job.status()).isEqualTo(PrintJob.Status.PENDING);
		assertThat(job.waitingReason()).isEqualTo("printer offline");
		assertThat(job.attempts()).isGreaterThan(30);
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("pending"))
			.andExpect(jsonPath("$[0].printError").value("printer offline"));

		var kitchen = fakePrinter();
		printerRepository.update(kitchenId, "Kitchen", kitchen.address(), "local_printer", ItemType.FOOD, true);
		runFor(Duration.ofSeconds(20));

		assertThat(kitchen.requests).hasSize(1);
		assertThat(job(id, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.PRINTED);
		assertThat(job(id, ItemType.FOOD).waitingReason()).isNull();
	}

	@Test
	void retriesDoNotStartBeforeTheirTime() throws Exception {
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		long id = sendOrder(waiter, "12", line(pizza, 1));

		worker.deliverDue();
		worker.deliverDue();

		assertThat(job(id, ItemType.FOOD).attempts()).isEqualTo(1);
		assertThat(job(id, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.PENDING);
	}

	@Test
	void paperEndIsRetriedAndPrintsOnceThePaperIsBack() throws Exception {
		var kitchen = fakePrinter();
		printer("Kitchen", kitchen.address(), ItemType.FOOD);
		kitchen.answer(FakeEposPrinter.response(false, "EPTR_REC_EMPTY"));
		kitchen.answer(FakeEposPrinter.response(false, "EPTR_REC_EMPTY"));
		long id = sendOrder(waiter, "12", line(pizza, 1));

		runUntilDone(id, ItemType.FOOD);

		assertThat(kitchen.requests).hasSize(3);
		assertThat(job(id, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.PRINTED);
	}

	@Test
	void longPaperChangePrintsAllWaitingTicketsInOrderWithoutFailing() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		bar.paperEnd(true);
		List<Long> orders = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			orders.add(sendOrder(waiter, "T" + i, line(cola, i)));
			runFor(Duration.ofSeconds(45));
		}
		assertThat(orders).allSatisfy(o -> assertThat(job(o, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PENDING));
		// Only the first ticket is tried; the others wait behind it.
		assertThat(job(orders.get(0), ItemType.DRINK).waitingReason()).isEqualTo("paper end");
		assertThat(bar.requests).allSatisfy(r -> assertThat(r).contains("Table T1"));

		bar.paperEnd(false);
		runFor(Duration.ofSeconds(20));

		assertThat(orders).allSatisfy(o -> assertThat(job(o, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PRINTED));
		List<String> printed = bar.printedTickets();
		assertThat(printed).hasSize(5);
		for (int i = 0; i < 5; i++) {
			assertThat(printed.get(i)).contains("Table T" + (i + 1));
		}
	}

	@Test
	void wrongDeviceIdFailsAtOnce() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		bar.answer(FakeEposPrinter.response(false, "DeviceNotFound"));
		long id = sendOrder(waiter, "12", line(cola, 1));

		worker.deliverDue();

		assertThat(job(id, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.FAILED);
		assertThat(job(id, ItemType.DRINK).lastError()).isEqualTo("device ID not found on the printer");
	}

	@Test
	void ticketLeftInPrintingAtStartupBecomesUnclearAndIsNotSentAgain() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		long id = sendOrder(waiter, "12", line(cola, 1));
		long jobId = job(id, ItemType.DRINK).id();
		// The process stopped after the ticket was handed to the printer but before the answer was recorded.
		jobRepository.startAttempt(jobId, clock.instant());

		worker.recoverInterrupted();
		worker.deliverDue();
		clock.advance(Duration.ofMinutes(5));
		worker.deliverDue();

		assertThat(bar.requests).isEmpty();
		PrintJob job = job(id, ItemType.DRINK);
		assertThat(job.status()).isEqualTo(PrintJob.Status.UNCLEAR);
		assertThat(job.lastError()).isEqualTo(PrintWorker.INTERRUPTED);
		assertThat(job.needsResolution()).isTrue();
	}

	@Test
	void ticketIsMarkedPrintingWhileThePrinterHasIt() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		long id = sendOrder(waiter, "12", line(cola, 1));
		bar.onRequest(() -> statusSeenByPrinter = job(id, ItemType.DRINK).status());

		worker.deliverDue();

		assertThat(statusSeenByPrinter).isEqualTo(PrintJob.Status.PRINTING);
		assertThat(job(id, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PRINTED);
	}

	private volatile PrintJob.Status statusSeenByPrinter;

	@Test
	void unclearOutcomeIsNotRetried() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		bar.answer(new FakeEposPrinter.Answer(200, FakeEposPrinter.SUCCESS, Duration.ofSeconds(1)));
		long id = sendOrder(waiter, "12", line(cola, 1));

		worker.deliverDue();
		clock.advance(Duration.ofMinutes(5));
		worker.deliverDue();

		assertThat(bar.requests).hasSize(1);
		PrintJob job = job(id, ItemType.DRINK);
		assertThat(job.status()).isEqualTo(PrintJob.Status.UNCLEAR);
		assertThat(job.lastError()).isEqualTo(EposClient.NO_ANSWER);
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("unclear"));
	}

	@Test
	void ticketsOfOnePrinterPrintInOrderAndAStuckPrinterDoesNotHoldUpTheOther() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		long first = sendOrder(waiter, "1", line(cola, 1), line(pizza, 1));
		long second = sendOrder(waiter, "2", line(cola, 2));

		worker.deliverDue();

		assertThat(bar.requests).hasSize(2);
		assertThat(bar.requests.get(0)).contains("Table 1");
		assertThat(bar.requests.get(1)).contains("Table 2");
		assertThat(job(first, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.PENDING);
		assertThat(job(second, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PRINTED);
	}

	@Test
	void laterTicketWaitsWhileTheOneBeforeIsRetried() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		bar.answer(FakeEposPrinter.response(false, "EPTR_COVER_OPEN"));
		long first = sendOrder(waiter, "1", line(cola, 1));
		long second = sendOrder(waiter, "2", line(cola, 1));

		worker.deliverDue();
		assertThat(bar.requests).hasSize(1);
		assertThat(job(second, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PENDING);

		clock.advance(Duration.ofSeconds(2));
		worker.deliverDue();

		assertThat(bar.requests).hasSize(3);
		assertThat(bar.requests.get(1)).contains("Table 1");
		assertThat(bar.requests.get(2)).contains("Table 2");
		assertThat(job(first, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PRINTED);
	}

	@Test
	void pausesGrowUpToSixteenSeconds() {
		assertThat(PrintWorker.pause(1)).hasSeconds(2);
		assertThat(PrintWorker.pause(2)).hasSeconds(4);
		assertThat(PrintWorker.pause(3)).hasSeconds(8);
		assertThat(PrintWorker.pause(4)).hasSeconds(16);
		assertThat(PrintWorker.pause(9)).hasSeconds(16);
	}

	/** Runs the worker, moving the clock to each next attempt, until the ticket is printed or failed. */
	private Instant runUntilDone(long orderId, ItemType type) {
		for (int i = 0; i < 100; i++) {
			worker.deliverDue();
			PrintJob job = job(orderId, type);
			if (job.status() != PrintJob.Status.PENDING) {
				return clock.instant();
			}
			clock.set(job.nextAttemptAt().toString());
		}
		throw new AssertionError("ticket still pending");
	}

	/** Runs the worker for {@code duration} of clock time, one second at a time. */
	private void runFor(Duration duration) {
		Instant end = clock.instant().plus(duration);
		while (clock.instant().isBefore(end)) {
			worker.deliverDue();
			clock.advance(Duration.ofSeconds(1));
		}
		worker.deliverDue();
	}

}
