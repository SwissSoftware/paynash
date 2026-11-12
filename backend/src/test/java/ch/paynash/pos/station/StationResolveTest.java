package ch.paynash.pos.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintingTestSupport;
import ch.paynash.pos.support.FakeEposPrinter;

/** Spec print-station, "Resolve unclear tickets" and "Reprint from the station". */
class StationResolveTest extends PrintingTestSupport {

	private String waiter;

	private String barStation;

	private FakeEposPrinter bar;

	private long kitchen;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		bar = fakePrinter();
		long barId = printer("Bar", bar.address(), ItemType.DRINK);
		kitchen = printer("Kitchen", OFFLINE, ItemType.FOOD);
		createStation("station-bar", "5555", barId);
		barStation = login("station-bar", "5555");
		cola = item("Drinks", "Cola", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
	}

	@Test
	void wasPrintedResolvesWithoutPrintingAgain() throws Exception {
		long id = unclearBarOrder();
		PrintJob ticket = job(id, ItemType.DRINK);

		resolve(ticket.id(), "printed").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("printed"));
		worker.deliverDue();

		assertThat(bar.requests).hasSize(1);
		PrintJob resolved = job(id, ItemType.DRINK);
		assertThat(resolved.status()).isEqualTo(PrintJob.Status.PRINTED);
		assertThat(resolved.resolution()).isEqualTo(PrintJob.Resolution.PRINTED);
		assertThat(resolved.needsResolution()).isFalse();
		assertThat(jdbc.sql("SELECT resolved_by_station FROM print_job WHERE id = ?").param(ticket.id())
			.query(Long.class)
			.single()).isNotNull();
		as(barStation, get("/station/printers")).andExpect(jsonPath("$[0].unclear").value(0));
	}

	@Test
	void reprintPrintsOnceMarkedReprintWithTheOriginalNumber() throws Exception {
		long id = unclearBarOrder();
		PrintJob ticket = job(id, ItemType.DRINK);

		resolve(ticket.id(), "reprint").andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("reprint"))
			.andExpect(jsonPath("$.reprintOf").value(ticket.id()));
		worker.deliverDue();

		assertThat(bar.printedTickets()).singleElement()
			.satisfies(r -> assertThat(r).contains("*** REPRINT ***").contains("BAR 0001"));
		assertThat(jobRepository.find(ticket.id()).orElseThrow().resolution()).isEqualTo(PrintJob.Resolution.REPRINT);
		assertThat(jobs(id).getLast().ticketNo()).isEqualTo(1);
	}

	@Test
	void ofTwoConcurrentResolutionsOnlyTheFirstTakesEffect() throws Exception {
		long id = unclearBarOrder();
		long ticket = job(id, ItemType.DRINK).id();
		Callable<Integer> attempt = () -> resolve(ticket, "reprint").andReturn().getResponse().getStatus();

		List<Integer> statuses;
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			var results = pool.invokeAll(List.of(attempt, attempt));
			statuses = List.of(results.get(0).get(), results.get(1).get());
		}

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(jobs(id)).filteredOn(j -> j.kind() == PrintJob.Kind.REPRINT).hasSize(1);
		resolve(ticket, "printed").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The ticket was already resolved"));
	}

	@Test
	void retriedRequestWithTheSameKeyReprintsOnce() throws Exception {
		long id = unclearBarOrder();
		long ticket = job(id, ItemType.DRINK).id();

		for (int i = 0; i < 2; i++) {
			as(barStation, post("/station/tickets/" + ticket + "/resolve").header("Idempotency-Key", "k-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("resolution", "reprint"))).andExpect(status().isOk());
		}

		assertThat(jobs(id)).filteredOn(j -> j.kind() == PrintJob.Kind.REPRINT).hasSize(1);
	}

	@Test
	void onlyUnclearTicketsOfTheStationsPrintersCanBeResolved() throws Exception {
		long printed = sendOrder(waiter, "1", line(cola, 1));
		worker.deliverDue();
		resolve(job(printed, ItemType.DRINK).id(), "printed").andExpect(status().isConflict());

		long food = sendOrder(waiter, "2", line(pizza, 1));
		resolve(job(food, ItemType.FOOD).id(), "printed").andExpect(status().isForbidden());
		resolve(999_999, "printed").andExpect(status().isNotFound());
		as(barStation, post("/station/tickets/" + job(printed, ItemType.DRINK).id() + "/resolve")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("resolution", "maybe"))).andExpect(status().isBadRequest());
	}

	@Test
	void stationReprintsTicketsOfItsPrinterThatAreNotWaiting() throws Exception {
		long id = sendOrder(waiter, "1", line(cola, 1));
		long ticket = job(id, ItemType.DRINK).id();
		as(barStation, post("/station/tickets/" + ticket + "/reprint")).andExpect(status().isConflict());
		worker.deliverDue();

		as(barStation, post("/station/tickets/" + ticket + "/reprint")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("reprint"));
		worker.deliverDue();

		assertThat(bar.printedTickets()).hasSize(2);
		long food = sendOrder(waiter, "2", line(pizza, 1));
		as(barStation, post("/station/tickets/" + job(food, ItemType.FOOD).id() + "/reprint"))
			.andExpect(status().isForbidden());
	}

	@Test
	void waiterReprintOfAnUnclearTicketResolvesIt() throws Exception {
		long id = unclearBarOrder();
		long ticket = job(id, ItemType.DRINK).id();

		postJson(waiter, "/orders/" + id + "/tickets/" + ticket + "/reprint", "").andExpect(status().isCreated());

		PrintJob original = jobRepository.find(ticket).orElseThrow();
		assertThat(original.resolution()).isEqualTo(PrintJob.Resolution.REPRINT);
		assertThat(original.needsResolution()).isFalse();
	}

	private long unclearBarOrder() throws Exception {
		bar.answer(FakeEposPrinter.response(false, "EPTR_CUTTER"));
		long id = sendOrder(waiter, "12", line(cola, 1));
		worker.deliverDue();
		assertThat(job(id, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.UNCLEAR);
		return id;
	}

	private ResultActions resolve(long ticketId, String resolution) throws Exception {
		return as(barStation, post("/station/tickets/" + ticketId + "/resolve").contentType(MediaType.APPLICATION_JSON)
			.content(json("resolution", resolution)));
	}

}
