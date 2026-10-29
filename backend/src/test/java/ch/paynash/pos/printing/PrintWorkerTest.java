package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.support.FakeEposPrinter;

/**
 * Delivery of queued tickets against a fake printer (spec ticket-printing, "Print status, retry, and failure
 * visibility" and "No duplicate production tickets").
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
	void offlinePrinterIsRetriedForAtLeastAMinuteThenFails() throws Exception {
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		long id = sendOrder(waiter, "12", line(pizza, 1));
		Instant start = clock.instant();

		Instant failedAt = runUntilDone(id, ItemType.FOOD);

		PrintJob job = job(id, ItemType.FOOD);
		assertThat(job.status()).isEqualTo(PrintJob.Status.FAILED);
		assertThat(job.lastError()).isEqualTo("printer offline");
		assertThat(Duration.between(start, failedAt)).isGreaterThanOrEqualTo(Duration.ofSeconds(60));
		assertThat(job.attempts()).isGreaterThan(3);
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("failed"))
			.andExpect(jsonPath("$[0].printError").value("printer offline"));
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
	void paperEndThatStaysIsReportedAsPaperEnd() throws Exception {
		var kitchen = fakePrinter();
		printer("Kitchen", kitchen.address(), ItemType.FOOD);
		for (int i = 0; i < 50; i++) {
			kitchen.answer(FakeEposPrinter.response(false, "EPTR_REC_EMPTY"));
		}
		long id = sendOrder(waiter, "12", line(pizza, 1));

		runUntilDone(id, ItemType.FOOD);

		assertThat(job(id, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.FAILED);
		assertThat(job(id, ItemType.FOOD).lastError()).isEqualTo("paper end");
	}

	@Test
	void unclearOutcomeFailsWithoutRetrying() throws Exception {
		var bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		bar.answer(new FakeEposPrinter.Answer(200, FakeEposPrinter.SUCCESS, Duration.ofSeconds(1)));
		long id = sendOrder(waiter, "12", line(cola, 1));

		worker.deliverDue();
		clock.advance(Duration.ofMinutes(5));
		worker.deliverDue();

		assertThat(bar.requests).hasSize(1);
		PrintJob job = job(id, ItemType.DRINK);
		assertThat(job.status()).isEqualTo(PrintJob.Status.FAILED);
		assertThat(job.lastError()).isEqualTo(EposClient.NO_ANSWER);
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

}
