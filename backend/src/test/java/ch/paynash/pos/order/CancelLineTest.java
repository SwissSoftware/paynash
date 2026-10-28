package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;

class CancelLineTest extends OrderTestSupport {

	private String anna;

	private long order;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		long colaItem = item(category("Drinks"), "Cola", ItemType.DRINK, 450);
		long margherita = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
		order = openOrder(anna, "12");
		cola = addLine(anna, order, colaItem, 2);
		pizza = addLine(anna, order, margherita, 1);
		send(anna, order);
	}

	@Test
	void cancelledLineStaysButNoLongerCounts() throws Exception {
		cancel(pizza, json("reason", "guest changed mind")).andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[1].status").value("cancelled"))
			.andExpect(jsonPath("$.lines[1].cancelReason").value("guest changed mind"))
			.andExpect(jsonPath("$.lines[1].lineTotal").value("0.00"))
			.andExpect(jsonPath("$.total").value("9.00"));
	}

	@Test
	void cancellationIsHandedToTicketPrinting() throws Exception {
		cancel(pizza, json("reason", "guest changed mind"));

		assertThat(printing.cancellations).singleElement().satisfies(line -> {
			assertThat(line.name()).isEqualTo("Margherita");
			assertThat(line.type()).isEqualTo(ItemType.FOOD);
			assertThat(line.qty()).isEqualTo(1);
			assertThat(line.cancelReason()).isEqualTo("guest changed mind");
		});
	}

	@Test
	void reasonIsRequired() throws Exception {
		cancel(cola, json("reason", " ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.reason").value("A reason is required"));
		cancel(cola, "{}").andExpect(status().isBadRequest());
		cancel(cola, json("reason", "r".repeat(141))).andExpect(status().isBadRequest());
		assertThat(printing.cancellations).isEmpty();
	}

	@Test
	void unsentOrAlreadyCancelledLinesCannotBeCancelled() throws Exception {
		long unsent = addLine(anna, order, jdbc.sql("SELECT id FROM menu_item WHERE name = 'Cola'")
			.query(Long.class)
			.single(), 1);
		cancel(unsent, json("reason", "x")).andExpect(status().isConflict());

		cancel(cola, json("reason", "spilled")).andExpect(status().isOk());
		cancel(cola, json("reason", "spilled")).andExpect(status().isConflict());
		assertThat(printing.cancellations).hasSize(1);
	}

	private ResultActions cancel(long line, String body) throws Exception {
		return postJson(anna, "/orders/" + order + "/lines/" + line + "/cancel", body);
	}

}
