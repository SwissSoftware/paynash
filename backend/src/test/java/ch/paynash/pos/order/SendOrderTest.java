package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.menu.ItemType;

class SendOrderTest extends OrderTestSupport {

	private String anna;

	private long drinks;

	private long cola;

	private long margherita;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		drinks = category("Drinks");
		cola = item(drinks, "Cola", ItemType.DRINK, 450);
		margherita = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
	}

	@Test
	void sendSavesTheOrderAsSentWithSnapshots() throws Exception {
		clock.set("2026-10-29T18:30:00Z");
		send(anna, "t12", line(cola, 2),
				json("menuItemId", margherita, "qty", 1, "note", "extra spicy"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tableLabel").value("T12"))
			.andExpect(jsonPath("$.status").value("sent"))
			.andExpect(jsonPath("$.waiter").value("anna Display"))
			.andExpect(jsonPath("$.sentAt").value("2026-10-29T18:30:00Z"))
			.andExpect(jsonPath("$.lines[*].name", contains("Cola", "Margherita")))
			.andExpect(jsonPath("$.lines[*].status", contains("sent", "sent")))
			.andExpect(jsonPath("$.lines[0].category").value("Drinks"))
			.andExpect(jsonPath("$.lines[0].unitPrice").value("4.50"))
			.andExpect(jsonPath("$.lines[0].lineTotal").value("9.00"))
			.andExpect(jsonPath("$.lines[1].type").value("food"))
			.andExpect(jsonPath("$.lines[1].note").value("extra spicy"))
			.andExpect(jsonPath("$.total").value("27.00"));
	}

	@Test
	void allLinesAreHandedToTicketPrintingOnce() throws Exception {
		long order = sendOrder(anna, "12", line(cola, 2), line(margherita, 1));

		assertThat(printing.sent).singleElement().satisfies(p -> {
			assertThat(p.order().id()).isEqualTo(order);
			assertThat(p.order().tableLabel()).isEqualTo("12");
			assertThat(p.lines()).extracting(OrderLine::name).containsExactly("Cola", "Margherita");
		});
	}

	@Test
	void aTableCanHaveSeveralOrdersAndEachPrintsOnlyItsOwnLines() throws Exception {
		long first = sendOrder(anna, "12", line(cola, 2), line(margherita, 1));
		long second = sendOrder(anna, "12", line(cola, 1));

		assertThat(second).isNotEqualTo(first);
		assertThat(printing.sent).hasSize(2);
		assertThat(printing.sent.get(1).lines()).singleElement().satisfies(l -> {
			assertThat(l.name()).isEqualTo("Cola");
			assertThat(l.qty()).isEqualTo(1);
		});
	}

	@Test
	void laterMenuChangesDoNotChangeASentOrder() throws Exception {
		long order = sendOrder(anna, "12", line(cola, 1));
		menu.updateItem(cola, drinks, "Coca-Cola", ItemType.DRINK, 500, null, true);

		as(anna, get("/orders/" + order))
			.andExpect(jsonPath("$.lines[0].name").value("Cola"))
			.andExpect(jsonPath("$.lines[0].unitPrice").value("4.50"));
	}

	@Test
	void emptyOrderIsRejected() throws Exception {
		send(anna, "12").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.lines").exists());
		postJson(anna, "/orders", json("tableLabel", "12")).andExpect(status().isBadRequest());
		assertThat(orderCount()).isZero();
		assertThat(printing.sent).isEmpty();
	}

	@Test
	void tableLabelMustBe1To10LettersOrDigits() throws Exception {
		send(anna, "", line(cola, 1)).andExpect(status().isBadRequest());
		send(anna, "12-a", line(cola, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tableLabel").value("Table must be 1 to 10 letters or digits"));
		send(anna, "12345678901", line(cola, 1)).andExpect(status().isBadRequest());
		send(anna, "1234567890", line(cola, 1)).andExpect(status().isCreated());
	}

	@Test
	void quantityMustBe1To99AndNoteAtMost140() throws Exception {
		send(anna, "12", line(cola, 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['lines[0].qty']").value("Quantity must be 1 to 99"));
		send(anna, "12", line(cola, 100)).andExpect(status().isBadRequest());
		send(anna, "12", json("menuItemId", cola, "qty", 1, "note", "n".repeat(141)))
			.andExpect(status().isBadRequest());
		assertThat(orderCount()).isZero();

		send(anna, "12", line(cola, 99), json("menuItemId", cola, "qty", 1, "note", "  "))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lines[1].note").doesNotExist());
	}

	@Test
	void inactiveItemsRejectTheWholeOrder() throws Exception {
		menu.updateItem(cola, drinks, "Cola", ItemType.DRINK, 450, null, false);

		send(anna, "12", line(margherita, 1), line(cola, 1), line(cola, 2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Cola no longer available"))
			.andExpect(jsonPath("$.inactiveMenuItemIds", contains((int) cola)));
		assertThat(orderCount()).isZero();
		assertThat(printing.sent).isEmpty();
	}

	@Test
	void unknownItemIsRejected() throws Exception {
		send(anna, "12", line(999999, 1)).andExpect(status().isBadRequest());
		assertThat(orderCount()).isZero();
	}

	@Test
	void repeatedSendWithTheSameKeySavesOneOrder() throws Exception {
		for (int i = 0; i < 2; i++) {
			as(anna, post("/orders").header(IdempotencyFilter.HEADER, "send-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(sendBody("12", line(cola, 1)))).andExpect(status().isCreated());
		}
		assertThat(orderCount()).isEqualTo(1);
		assertThat(printing.sent).hasSize(1);
	}

	private long orderCount() {
		return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
	}

}
