package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;

class CancelAndVoidTest extends OrderTestSupport {

	private String anna;

	private String boss;

	private long order;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		boss = adminToken();
		long colaItem = item(category("Drinks"), "Cola", ItemType.DRINK, 450);
		long margherita = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
		order = sendOrder(anna, "12", line(colaItem, 2), line(margherita, 1));
		cola = lineId(order, "Cola");
		pizza = lineId(order, "Margherita");
	}

	// --- cancel line

	@Test
	void adminCancelsLineWhichStaysButNoLongerCounts() throws Exception {
		cancel(boss, pizza, json("reason", "guest changed mind")).andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[1].status").value("cancelled"))
			.andExpect(jsonPath("$.lines[1].cancelReason").value("guest changed mind"))
			.andExpect(jsonPath("$.lines[1].lineTotal").value("0.00"))
			.andExpect(jsonPath("$.total").value("9.00"))
			.andExpect(jsonPath("$.status").value("sent"));

		assertThat(printing.cancellations).singleElement().satisfies(line -> {
			assertThat(line.name()).isEqualTo("Margherita");
			assertThat(line.type()).isEqualTo(ItemType.FOOD);
			assertThat(line.cancelReason()).isEqualTo("guest changed mind");
		});
	}

	@Test
	void waiterCannotCancel() throws Exception {
		cancel(anna, pizza, json("reason", "x")).andExpect(status().isForbidden());
		assertThat(printing.cancellations).isEmpty();
	}

	@Test
	void cancelReasonIsRequired() throws Exception {
		cancel(boss, cola, json("reason", " ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.reason").value("A reason is required"));
		cancel(boss, cola, "{}").andExpect(status().isBadRequest());
		cancel(boss, cola, json("reason", "r".repeat(141))).andExpect(status().isBadRequest());
		assertThat(printing.cancellations).isEmpty();
	}

	@Test
	void lineCannotBeCancelledTwice() throws Exception {
		cancel(boss, cola, json("reason", "spilled")).andExpect(status().isOk());
		cancel(boss, cola, json("reason", "spilled")).andExpect(status().isConflict());
		assertThat(printing.cancellations).hasSize(1);
	}

	@Test
	void unknownLineAnswers404() throws Exception {
		cancel(boss, 999999, json("reason", "x")).andExpect(status().isNotFound());
	}

	// --- void

	@Test
	void voidCancelsTheRemainingLinesAndPrintsOnlyThose() throws Exception {
		cancel(boss, cola, json("reason", "spilled"));

		voidOrder(boss, json("reason", "wrong table")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("voided"))
			.andExpect(jsonPath("$.voidReason").value("wrong table"))
			.andExpect(jsonPath("$.total").value("0.00"))
			.andExpect(jsonPath("$.lines[*].status", contains("cancelled", "cancelled")))
			.andExpect(jsonPath("$.lines[*].cancelReason", contains("spilled", "wrong table")));

		assertThat(printing.voids).singleElement()
			.satisfies(p -> assertThat(p.lines()).extracting(OrderLine::name).containsExactly("Margherita"));
	}

	@Test
	void voidedOrderCannotBeChangedAnymore() throws Exception {
		voidOrder(boss, json("reason", "test order")).andExpect(status().isOk());

		voidOrder(boss, json("reason", "again")).andExpect(status().isConflict());
		cancel(boss, cola, json("reason", "x")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.orderStatus").value("voided"));
		assertThat(printing.voids).hasSize(1);
		assertThat(printing.cancellations).isEmpty();
	}

	@Test
	void waiterCannotVoid() throws Exception {
		voidOrder(anna, json("reason", "test")).andExpect(status().isForbidden());
		as(anna, get("/orders/" + order)).andExpect(jsonPath("$.status").value("sent"));
	}

	@Test
	void voidNeedsAReason() throws Exception {
		voidOrder(boss, json("reason", "")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.reason").exists());
	}

	@Test
	void voidBasedOnAnOutdatedVersionIsRejected() throws Exception {
		cancel(boss, cola, json("reason", "spilled"));
		voidOrder(boss, json("reason", "test", "version", 0)).andExpect(status().isConflict());
		voidOrder(boss, json("reason", "test", "version", 1)).andExpect(status().isOk());
	}

	private ResultActions cancel(String token, long line, String body) throws Exception {
		return postJson(token, "/admin/orders/" + order + "/lines/" + line + "/cancel", body);
	}

	private ResultActions voidOrder(String token, String body) throws Exception {
		return postJson(token, "/admin/orders/" + order + "/void", body);
	}

}
