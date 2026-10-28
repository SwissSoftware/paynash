package ch.paynash.pos.order;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;

class OrderLineTest extends OrderTestSupport {

	private String anna;

	private long drinks;

	private long cola;

	private long margherita;

	private long order;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		drinks = category("Drinks");
		cola = item(drinks, "Cola", ItemType.DRINK, 450);
		margherita = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
		order = openOrder(anna, "12");
	}

	@Test
	void addMixedItemsWithSnapshotsAndRunningTotal() throws Exception {
		add(json("menuItemId", cola, "qty", 2)).andExpect(status().isCreated());
		add(json("menuItemId", margherita, "qty", 1, "note", "extra spicy")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.lines[*].name", contains("Cola", "Margherita")))
			.andExpect(jsonPath("$.lines[0].type").value("drink"))
			.andExpect(jsonPath("$.lines[0].category").value("Drinks"))
			.andExpect(jsonPath("$.lines[0].unitPrice").value("4.50"))
			.andExpect(jsonPath("$.lines[0].qty").value(2))
			.andExpect(jsonPath("$.lines[0].lineTotal").value("9.00"))
			.andExpect(jsonPath("$.lines[0].status").value("unsent"))
			.andExpect(jsonPath("$.lines[0].note").doesNotExist())
			.andExpect(jsonPath("$.lines[1].type").value("food"))
			.andExpect(jsonPath("$.lines[1].note").value("extra spicy"))
			.andExpect(jsonPath("$.total").value("27.00"))
			.andExpect(jsonPath("$.hasUnsentLines").value(true));
	}

	@Test
	void priceAndNameChangeAfterAddingDoNotChangeTheLine() throws Exception {
		addLine(anna, order, cola, 1);
		menu.updateItem(cola, drinks, "Coca-Cola", ItemType.DRINK, 500, null, true);

		add(json("menuItemId", cola, "qty", 1)).andExpect(jsonPath("$.lines[0].name").value("Cola"))
			.andExpect(jsonPath("$.lines[0].unitPrice").value("4.50"))
			.andExpect(jsonPath("$.lines[1].name").value("Coca-Cola"))
			.andExpect(jsonPath("$.lines[1].unitPrice").value("5.00"))
			.andExpect(jsonPath("$.total").value("9.50"));
	}

	@Test
	void quantityMustBe1To99() throws Exception {
		add(json("menuItemId", cola, "qty", 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.qty").value("Quantity must be 1 to 99"));
		add(json("menuItemId", cola, "qty", 100)).andExpect(status().isBadRequest());
		add(json("menuItemId", cola)).andExpect(status().isBadRequest());
		add(json("menuItemId", cola, "qty", 99)).andExpect(status().isCreated());
	}

	@Test
	void noteIsAtMost140CharactersAndBlankMeansNone() throws Exception {
		add(json("menuItemId", cola, "qty", 1, "note", "n".repeat(141))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.note").exists());
		add(json("menuItemId", cola, "qty", 1, "note", "n".repeat(140))).andExpect(status().isCreated());
		add(json("menuItemId", cola, "qty", 1, "note", "  ")).andExpect(jsonPath("$.lines[1].note").doesNotExist());
	}

	@Test
	void inactiveItemIsRejected() throws Exception {
		menu.updateItem(cola, drinks, "Cola", ItemType.DRINK, 450, null, false);
		add(json("menuItemId", cola, "qty", 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.menuItemInactive").value(true))
			.andExpect(jsonPath("$.detail").value("Cola is no longer available"));
	}

	@Test
	void unknownItemIsRejected() throws Exception {
		add(json("menuItemId", 999999, "qty", 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.menuItemId").value("Menu item does not exist"));
	}

	@Test
	void changeQuantityAndNoteOfUnsentLine() throws Exception {
		long line = addLine(anna, order, cola, 1);
		update(line, json("qty", 3, "note", "no ice", "version", 0)).andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[0].qty").value(3))
			.andExpect(jsonPath("$.lines[0].note").value("no ice"))
			.andExpect(jsonPath("$.lines[0].version").value(1))
			.andExpect(jsonPath("$.total").value("13.50"));
		update(line, json("qty", 100, "version", 1)).andExpect(status().isBadRequest());
	}

	@Test
	void changeBasedOnAnOutdatedVersionIsRejected() throws Exception {
		long line = addLine(anna, order, cola, 1);
		update(line, json("qty", 2, "version", 0)).andExpect(status().isOk());
		update(line, json("qty", 5, "version", 0)).andExpect(status().isConflict());
		as(anna, get("/orders/" + order)).andExpect(jsonPath("$.lines[0].qty").value(2));
	}

	@Test
	void removeUnsentLine() throws Exception {
		long line = addLine(anna, order, cola, 1);
		addLine(anna, order, margherita, 1);
		as(anna, delete("/orders/" + order + "/lines/" + line)).andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[*].name", contains("Margherita")));
	}

	@Test
	void sentLineCannotBeChangedOrRemoved() throws Exception {
		long line = addLine(anna, order, cola, 1);
		send(anna, order).andExpect(status().isOk());
		update(line, json("qty", 2, "version", 1)).andExpect(status().isConflict());
		as(anna, delete("/orders/" + order + "/lines/" + line)).andExpect(status().isConflict());
	}

	@Test
	void unknownOrderOrLineAnswers404() throws Exception {
		postJson(anna, "/orders/999999/lines", json("menuItemId", cola, "qty", 1)).andExpect(status().isNotFound());
		update(999999, json("qty", 1, "version", 0)).andExpect(status().isNotFound());
	}

	private ResultActions add(String body) throws Exception {
		return postJson(anna, "/orders/" + order + "/lines", body);
	}

	private ResultActions update(long line, String body) throws Exception {
		return as(anna, put("/orders/" + order + "/lines/" + line).contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
