package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.support.FakeEposPrinter;

/** Spec ticket-printing, "Reprint" and "Print status, retry, and failure visibility". */
class ReprintTest extends PrintingTestSupport {

	private String waiter;

	private long pizza;

	private FakeEposPrinter kitchen;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		kitchen = fakePrinter();
		printer("Kitchen", kitchen.address(), ItemType.FOOD);
	}

	@Test
	void reprintAfterPaperChangePrintsTheSameTicketMarkedReprint() throws Exception {
		long id = failedKitchenOrder();
		long original = job(id, ItemType.FOOD).id();

		reprint(id, original).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("reprint"))
			.andExpect(jsonPath("$.status").value("pending"))
			.andExpect(jsonPath("$.reprintOf").value(original));
		worker.deliverDue();

		assertThat(kitchen.requests).last().satisfies(r -> assertThat(r).contains("*** REPRINT ***").contains(
				"1 x Margherita"));
		as(waiter, get("/orders/" + id)).andExpect(jsonPath("$.tickets.length()").value(2))
			.andExpect(jsonPath("$.tickets[0].status").value("failed"))
			.andExpect(jsonPath("$.tickets[0].reprinted").value(true))
			.andExpect(jsonPath("$.tickets[1].status").value("printed"))
			.andExpect(jsonPath("$.tickets[1].printer").value("Kitchen"));
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("printed"))
			.andExpect(jsonPath("$[0].printError").doesNotExist());
	}

	@Test
	void printedTicketCanBeReprintedToo() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1));
		worker.deliverDue();

		reprint(id, job(id, ItemType.FOOD).id()).andExpect(status().isCreated());
		worker.deliverDue();

		assertThat(kitchen.requests).hasSize(2);
	}

	@Test
	void reprintOfAReprintIsMarkedOnce() throws Exception {
		long id = failedKitchenOrder();
		reprint(id, job(id, ItemType.FOOD).id());
		worker.deliverDue();
		long reprinted = jobs(id).getLast().id();

		reprint(id, reprinted).andExpect(status().isCreated());

		assertThat(jobs(id).getLast().content()).startsWith("# *** REPRINT ***\n# Table 12");
	}

	@Test
	void reprintGoesToThePrinterNowServingTheType() throws Exception {
		jdbc.sql("DELETE FROM printer").update();
		long id = sendOrder(waiter, "12", line(pizza, 1));
		assertThat(job(id, ItemType.FOOD).lastError()).isEqualTo("no printer configured");
		printer("Kitchen", kitchen.address(), ItemType.FOOD);

		reprint(id, job(id, ItemType.FOOD).id()).andExpect(status().isCreated());
		worker.deliverDue();

		assertThat(kitchen.requests).singleElement();
	}

	@Test
	void pendingTicketCannotBeReprinted() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1));

		reprint(id, job(id, ItemType.FOOD).id()).andExpect(status().isConflict());
	}

	@Test
	void ticketMustBelongToTheOrder() throws Exception {
		long id = failedKitchenOrder();
		long other = sendOrder(waiter, "13", line(pizza, 1));

		reprint(other, job(id, ItemType.FOOD).id()).andExpect(status().isNotFound());
	}

	@Test
	void retriedReprintRequestPrintsOnce() throws Exception {
		long id = failedKitchenOrder();
		long original = job(id, ItemType.FOOD).id();

		for (int i = 0; i < 2; i++) {
			as(waiter, post("/orders/" + id + "/tickets/" + original + "/reprint").header(IdempotencyFilter.HEADER,
					"reprint-1"))
				.andExpect(status().isCreated());
		}

		assertThat(jobs(id)).hasSize(2);
	}

	@Test
	void needsALogin() throws Exception {
		mvc.perform(post("/orders/1/tickets/1/reprint")).andExpect(status().isUnauthorized());
	}

	private long failedKitchenOrder() throws Exception {
		kitchen.answer(FakeEposPrinter.response(false, "EPTR_CUTTER"));
		long id = sendOrder(waiter, "12", line(pizza, 1));
		worker.deliverDue();
		assertThat(job(id, ItemType.FOOD).status()).isEqualTo(PrintJob.Status.FAILED);
		return id;
	}

	private org.springframework.test.web.servlet.ResultActions reprint(long orderId, long ticketId) throws Exception {
		return as(waiter, post("/orders/" + orderId + "/tickets/" + ticketId + "/reprint"));
	}

}
