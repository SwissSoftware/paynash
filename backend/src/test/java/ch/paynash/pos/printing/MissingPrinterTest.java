package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

/**
 * Spec ticket-printing, "Route lines by category": unassigned categories are not printed, and a category whose
 * printers are all inactive fails visibly (change printer-category-routing).
 */
class MissingPrinterTest extends PrintingTestSupport {

	@Test
	void categoryWithoutPrinterIsNotPrintedAndNoErrorIsShown() throws Exception {
		String waiter = waiterToken();
		printer("Bar", OFFLINE, ItemType.DRINK);
		long cola = item("Drinks", "Cola", ItemType.DRINK);
		long entry = item("Entrance", "Entry fee", ItemType.FOOD);

		long id = sendOrder(waiter, "12", line(cola, 1), line(entry, 1));

		assertThat(jobs(id)).singleElement().satisfies(j -> {
			assertThat(j.printerName()).isEqualTo("Bar");
			assertThat(j.content()).contains("Cola").doesNotContain("Entry fee");
		});
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("pending"));
	}

	@Test
	void orderWithOnlyUnprintedCategoriesCreatesNoTicket() throws Exception {
		long entry = item("Entrance", "Entry fee", ItemType.FOOD);

		long id = sendOrder(waiterToken(), "12", line(entry, 1));

		assertThat(jobs(id)).isEmpty();
	}

	@Test
	void allAssignedPrintersInactiveFailsOnEachOfThem() throws Exception {
		String waiter = waiterToken();
		long kitchen = printerRepository.insert("Kitchen", OFFLINE, "local_printer", true, false, false);
		long pass = printerRepository.insert("Pass", OFFLINE, "local_printer", true, false, false);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		assign(kitchen, "Pizzas");
		assign(pass, "Pizzas");

		long id = sendOrder(waiter, "12", line(pizza, 1));

		assertThat(jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single()).isOne();
		assertThat(jobs(id)).hasSize(2).allSatisfy(j -> {
			assertThat(j.status()).isEqualTo(PrintJob.Status.FAILED);
			assertThat(j.lastError()).isEqualTo("no active printer");
			assertThat(j.ticketNo()).isNull();
			assertThat(j.attempts()).isZero();
		}).extracting(PrintJob::printerId).containsExactly(kitchen, pass);
		as(waiter, get("/orders/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tickets[0].status").value("failed"))
			.andExpect(jsonPath("$.tickets[0].printer").value("Kitchen"))
			.andExpect(jsonPath("$.tickets[0].error").value("no active printer"));
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("failed"))
			.andExpect(jsonPath("$[0].printError").value("no active printer"));
	}

	@Test
	void onlyTheActiveOfTwoPrintersPrints() throws Exception {
		long kitchen = printerRepository.insert("Kitchen", OFFLINE, "local_printer", true, false, true);
		long pass = printerRepository.insert("Pass", OFFLINE, "local_printer", true, false, false);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		assign(kitchen, "Pizzas");
		assign(pass, "Pizzas");

		long id = sendOrder(waiterToken(), "12", line(pizza, 1));

		assertThat(jobs(id)).singleElement().satisfies(j -> {
			assertThat(j.printerId()).isEqualTo(kitchen);
			assertThat(j.status()).isEqualTo(PrintJob.Status.PENDING);
		});
	}

	@Test
	void printerWithTicketsOffDoesNotCount() throws Exception {
		long kitchen = printerRepository.insert("Kitchen", OFFLINE, "local_printer", false, false, true);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		assign(kitchen, "Pizzas");

		long id = sendOrder(waiterToken(), "12", line(pizza, 1));

		assertThat(jobs(id)).isEmpty();
	}

}
