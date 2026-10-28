package ch.paynash.pos.menu;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class WaiterMenuTest extends MenuTestSupport {

	private String waiter;

	private long pizzas;

	private long drinks;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		waiter = waiterToken();
		drinks = createCategory("Drinks");
		pizzas = createCategory("Pizzas");
		createCategory("Empty");
		createItem(drinks, "Cola", "drink", "4.50");
		createItem(drinks, "Beer", "drink", "6.00");
		createItem(pizzas, "Margherita", "food", "18.00");
	}

	@Test
	void showsActiveItemsGroupedAndSorted() throws Exception {
		mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categories[*].name", contains("Drinks", "Pizzas")))
			.andExpect(jsonPath("$.categories[0].items[*].name", contains("Cola", "Beer")))
			.andExpect(jsonPath("$.categories[0].items[0].type").value("drink"))
			.andExpect(jsonPath("$.categories[0].items[0].price").value("4.50"))
			.andExpect(jsonPath("$.categories[1].items[0].type").value("food"))
			.andExpect(jsonPath("$.categories[1].items[0].imageUrl").doesNotExist());
	}

	@Test
	void deactivatedItemIsMissing() throws Exception {
		long tiramisu = createItem(pizzas, "Tiramisu", "food", "9.00");
		mvc.perform(put("/admin/items/" + tiramisu).header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("categoryId", pizzas, "name", "Tiramisu", "type", "food", "price", "9.00", "active", false)));

		mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andExpect(jsonPath("$.categories[1].items[*].name", contains("Margherita")));
	}

	@Test
	void unchangedMenuAnswers304() throws Exception {
		String etag = mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andExpect(header().exists(HttpHeaders.ETAG))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.ETAG);

		mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter))
			.header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());
	}

	@Test
	void changedMenuGetsANewEtag() throws Exception {
		String before = mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.ETAG);
		createItem(pizzas, "Funghi", "food", "20.00");

		String after = mvc.perform(get("/menu").header(HttpHeaders.AUTHORIZATION, bearer(waiter))
			.header(HttpHeaders.IF_NONE_MATCH, before))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.ETAG);
		assertThat(after).isNotEqualTo(before);
	}

	@Test
	void menuRequiresLogin() throws Exception {
		mvc.perform(get("/menu")).andExpect(status().isUnauthorized());
	}

}
