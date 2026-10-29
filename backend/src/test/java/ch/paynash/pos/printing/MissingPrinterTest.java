package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

/** Spec ticket-printing, "Printer configuration", scenario "Missing routing". */
class MissingPrinterTest extends PrintingTestSupport {

	@Test
	void orderIsSavedAndTheTicketFailsAtOnce() throws Exception {
		String waiter = waiterToken();
		printer("Bar", OFFLINE, ItemType.DRINK);
		long cola = item("Drinks", "Cola", ItemType.DRINK);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);

		long id = sendOrder(waiter, "12", line(cola, 1), line(pizza, 1));

		assertThat(jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single()).isOne();
		PrintJob food = job(id, ItemType.FOOD);
		assertThat(food.status()).isEqualTo(PrintJob.Status.FAILED);
		assertThat(food.lastError()).isEqualTo("no printer configured");
		assertThat(food.printerId()).isNull();
		assertThat(food.attempts()).isZero();
		assertThat(job(id, ItemType.DRINK).status()).isEqualTo(PrintJob.Status.PENDING);
		as(waiter, get("/orders/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tickets[?(@.type == 'food')].status").value("failed"))
			.andExpect(jsonPath("$.tickets[?(@.type == 'food')].error").value("no printer configured"));
		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("failed"))
			.andExpect(jsonPath("$[0].printError").value("no printer configured"));
	}

	@Test
	void inactivePrinterDoesNotCount() throws Exception {
		String waiter = waiterToken();
		printerRepository.insert("Kitchen", OFFLINE, "local_printer", ItemType.FOOD, false);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);

		long id = sendOrder(waiter, "12", line(pizza, 1));

		assertThat(job(id, ItemType.FOOD).lastError()).isEqualTo("no printer configured");
	}

}
