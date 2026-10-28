package ch.paynash.pos.menu;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ItemAdminTest extends MenuTestSupport {

	private long hotDrinks;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		hotDrinks = createCategory("Hot drinks");
	}

	@Test
	void createDrinkItem() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Espresso", "type", "drink", "price", "4.50", "description",
				"Double shot"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Espresso"))
			.andExpect(jsonPath("$.type").value("drink"))
			.andExpect(jsonPath("$.price").value("4.50"))
			.andExpect(jsonPath("$.categoryId").value(hotDrinks))
			.andExpect(jsonPath("$.description").value("Double shot"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.imageUrl").doesNotExist());
	}

	@Test
	void priceIsNormalisedToTwoDecimals() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "Tea", "type", "drink", "price", "4.5"))
			.andExpect(jsonPath("$.price").value("4.50"));
		postItem(json("categoryId", hotDrinks, "name", "Water", "type", "drink", "price", "0"))
			.andExpect(jsonPath("$.price").value("0.00"));
	}

	@Test
	void negativePriceIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "-1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").value("Price must be at least 0.00 with at most two decimals"));
	}

	@Test
	void moreThanTwoDecimalsIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "4.505"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").exists());
	}

	@Test
	void missingPriceIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.price").value("Price is required"));
	}

	@Test
	void missingTypeIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "price", "1.00")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.type").value("Type is required (food or drink)"));
	}

	@Test
	void unknownTypeIsRejected() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "dessert", "price", "1.00"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void nameIsRequiredAndAtMost80Characters() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", " ", "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());
		postItem(json("categoryId", hotDrinks, "name", "x".repeat(81), "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());
		postItem(json("categoryId", hotDrinks, "name", "x".repeat(80), "type", "drink", "price", "1.00"))
			.andExpect(status().isCreated());
	}

	@Test
	void descriptionIsAtMost200Characters() throws Exception {
		postItem(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "1.00", "description",
				"d".repeat(201)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.description").exists());
	}

	@Test
	void unknownCategoryIsRejected() throws Exception {
		postItem(json("categoryId", 999999, "name", "X", "type", "drink", "price", "1.00"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.categoryId").value("Category does not exist"));
	}

	@Test
	void deactivateAndReactivateInsteadOfDelete() throws Exception {
		long id = createItem(hotDrinks, "Tiramisu", "food", "9.00");
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", true))
			.andExpect(jsonPath("$.active").value(true));
		mvc.perform(delete("/admin/items/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void editChangesPriceAndMovesBetweenCategories() throws Exception {
		long cold = createCategory("Cold drinks");
		createItem(cold, "Cola", "drink", "4.50");
		long id = createItem(hotDrinks, "Ice tea", "drink", "4.00");
		update(id, json("categoryId", cold, "name", "Ice tea", "type", "drink", "price", "5.00", "active", true))
			.andExpect(jsonPath("$.price").value("5.00"))
			.andExpect(jsonPath("$.categoryId").value(cold))
			.andExpect(jsonPath("$.displayOrder").value(1));
	}

	@Test
	void reorderItemsWithinCategory() throws Exception {
		long a = createItem(hotDrinks, "A", "drink", "1.00");
		long b = createItem(hotDrinks, "B", "drink", "1.00");
		mvc.perform(put("/admin/categories/" + hotDrinks + "/items/order")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[%d,%d]}".formatted(b, a)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("B", "A")));
	}

	@Test
	void adminListIncludesInactiveItems() throws Exception {
		long id = createItem(hotDrinks, "Tiramisu", "food", "9.00");
		update(id, json("categoryId", hotDrinks, "name", "Tiramisu", "type", "food", "price", "9.00", "active", false));
		mvc.perform(get("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$[0].name").value("Tiramisu"))
			.andExpect(jsonPath("$[0].active").value(false));
	}

	@Test
	void waiterCannotManageItems() throws Exception {
		String waiter = waiterToken();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(waiter))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("categoryId", hotDrinks, "name", "X", "type", "drink", "price", "1.00")))
			.andExpect(status().isForbidden());
	}

	private ResultActions postItem(String body) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/items")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions update(long id, String body) throws Exception {
		return mvc.perform(put("/admin/items/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

}
