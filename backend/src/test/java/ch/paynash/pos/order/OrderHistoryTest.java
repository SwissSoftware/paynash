package ch.paynash.pos.order;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

class OrderHistoryTest extends OrderTestSupport {

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
	void listsTodaysOrdersNewestFirstWithTableWaiterTimeTotalAndState() throws Exception {
		clock.set("2026-10-29T17:00:00Z");
		long t12 = sendOrder(anna, "12", line(cola, 2), line(pizza, 1));
		postJson(boss, "/admin/orders/" + t12 + "/lines/" + lineId(t12, "Margherita") + "/cancel",
				json("reason", "x"));
		clock.set("2026-10-29T17:10:00Z");
		long t5 = sendOrder(boss, "5", line(pizza, 1));
		postJson(boss, "/admin/orders/" + t5 + "/void", json("reason", "wrong table"));

		as(anna, get("/orders")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].tableLabel", contains("5", "12")))
			.andExpect(jsonPath("$[*].waiter", contains("boss Display", "anna Display")))
			.andExpect(jsonPath("$[*].sentAt", contains("2026-10-29T17:10:00Z", "2026-10-29T17:00:00Z")))
			.andExpect(jsonPath("$[*].total", contains("0.00", "9.00")))
			.andExpect(jsonPath("$[*].status", contains("voided", "sent")));
	}

	@Test
	void todayAndOtherDaysFollowTheRestaurantTimeZone() throws Exception {
		// 23:30 UTC on 28 Oct is 00:30 on 29 Oct in Zurich (CET, UTC+1).
		sendAt("2026-10-28T22:30:00Z", "1");
		sendAt("2026-10-28T23:30:00Z", "2");
		sendAt("2026-10-29T22:59:00Z", "3");
		sendAt("2026-10-29T23:00:00Z", "4");

		clock.set("2026-10-29T12:00:00Z");
		anna = login("anna", "4711");
		as(anna, get("/orders")).andExpect(jsonPath("$[*].tableLabel", contains("3", "2")));
		as(anna, get("/orders?date=2026-10-28")).andExpect(jsonPath("$[*].tableLabel", contains("1")));
		as(anna, get("/orders?date=2026-10-30")).andExpect(jsonPath("$[*].tableLabel", contains("4")));
		as(anna, get("/orders?date=2026-10-01")).andExpect(jsonPath("$", empty()));
	}

	/** Logs in again at the new time, because sessions expire after 12 hours. */
	private void sendAt(String instant, String table) throws Exception {
		clock.set(instant);
		anna = login("anna", "4711");
		sendOrder(anna, table, line(cola, 1));
	}

	@Test
	void invalidDateIsRejected() throws Exception {
		as(anna, get("/orders?date=29.10.2026")).andExpect(status().isBadRequest());
	}

	@Test
	void detailShowsLinesWithTheirStatus() throws Exception {
		long order = sendOrder(anna, "12", line(cola, 2), line(pizza, 1));
		postJson(boss, "/admin/orders/" + order + "/lines/" + lineId(order, "Margherita") + "/cancel",
				json("reason", "guest changed mind"));

		as(anna, get("/orders/" + order)).andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[*].status", contains("sent", "cancelled")))
			.andExpect(jsonPath("$.lines[1].cancelReason").value("guest changed mind"))
			.andExpect(jsonPath("$.lines[1].lineTotal").value("0.00"))
			.andExpect(jsonPath("$.total").value("9.00"));
	}

	@Test
	void unknownOrderAnswers404() throws Exception {
		as(anna, get("/orders/999999")).andExpect(status().isNotFound());
	}

}
