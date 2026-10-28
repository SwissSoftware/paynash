package ch.paynash.pos.order;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

class OrderOverviewTest extends OrderTestSupport {

	private String anna;

	private String boss;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		boss = adminToken();
		cola = item(category("Drinks"), "Cola", ItemType.DRINK, 450);
		pizza = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
	}

	@Test
	void listsOpenOrdersWithTableWaiterTotalAndAge() throws Exception {
		long t12 = openOrder(anna, "12");
		addLine(anna, t12, cola, 2);
		long cancelled = addLine(anna, t12, pizza, 1);
		send(anna, t12);
		postJson(anna, "/orders/" + t12 + "/lines/" + cancelled + "/cancel", json("reason", "x"));
		clock.advance(Duration.ofMinutes(7));
		openOrder(boss, "5");
		clock.advance(Duration.ofMinutes(2));

		as(anna, get("/orders")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].tableLabel", contains("12", "5")))
			.andExpect(jsonPath("$[*].waiter", contains("anna Display", "boss Display")))
			.andExpect(jsonPath("$[*].total", contains("9.00", "0.00")))
			.andExpect(jsonPath("$[*].ageMinutes", contains(9, 2)));
	}

	@Test
	void closedOrdersAreNotListed() throws Exception {
		long t12 = openOrder(anna, "12");
		openOrder(anna, "13");
		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "test order"));

		as(anna, get("/orders")).andExpect(jsonPath("$[*].tableLabel", contains("13")));
	}

	@Test
	void adminVoidsOpenOrderWithReason() throws Exception {
		long t12 = openOrder(anna, "12");
		addLine(anna, t12, cola, 1);

		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "test order")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("voided"))
			.andExpect(jsonPath("$.voidReason").value("test order"));

		// The table is free again, and the voided order cannot be changed.
		postJson(anna, "/orders", json("tableLabel", "12")).andExpect(status().isCreated());
		postJson(anna, "/orders/" + t12 + "/lines", json("menuItemId", cola, "qty", 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.orderStatus").value("voided"));
		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "again")).andExpect(status().isConflict());
	}

	@Test
	void voidNeedsAReason() throws Exception {
		long t12 = openOrder(anna, "12");
		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.reason").exists());
	}

	@Test
	void waiterCannotVoid() throws Exception {
		long t12 = openOrder(anna, "12");
		postJson(anna, "/admin/orders/" + t12 + "/void", json("reason", "test")).andExpect(status().isForbidden());
		as(anna, get("/orders/" + t12)).andExpect(jsonPath("$.status").value("open"));
	}

	@Test
	void voidBasedOnAnOutdatedVersionIsRejected() throws Exception {
		long t12 = openOrder(anna, "12");
		addLine(anna, t12, cola, 1);
		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "test", "version", 0))
			.andExpect(status().isConflict());
		postJson(boss, "/admin/orders/" + t12 + "/void", json("reason", "test", "version", 1))
			.andExpect(status().isOk());
	}

}
