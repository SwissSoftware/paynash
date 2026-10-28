package ch.paynash.pos.menu;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class CategoryAdminTest extends MenuTestSupport {

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
	}

	@Test
	void createdCategoriesAreAppendedInOrder() throws Exception {
		createCategory("Hot drinks");
		createCategory("Pizzas");
		mvc.perform(get("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("Hot drinks", "Pizzas")))
			.andExpect(jsonPath("$[*].displayOrder", contains(0, 1)));
	}

	@Test
	void duplicateOrBlankNameIsRejected() throws Exception {
		createCategory("Pizzas");
		mvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "PIZZAS"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
		mvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", " "))).andExpect(status().isBadRequest());
	}

	@Test
	void renameCategory() throws Exception {
		long id = createCategory("Pizza");
		mvc.perform(put("/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "Pizzas"))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Pizzas"));
	}

	@Test
	void reorderCategories() throws Exception {
		long a = createCategory("A");
		long b = createCategory("B");
		long c = createCategory("C");
		mvc.perform(put("/admin/categories/order").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[%d,%d,%d]}".formatted(c, a, b)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("C", "A", "B")));
	}

	@Test
	void reorderMustListEveryCategoryOnce() throws Exception {
		long a = createCategory("A");
		createCategory("B");
		mvc.perform(put("/admin/categories/order").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"ids\":[%d,%d]}".formatted(a, a))).andExpect(status().isBadRequest());
	}

	@Test
	void deleteEmptyCategory() throws Exception {
		long id = createCategory("Old");
		mvc.perform(delete("/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void deleteNonEmptyCategoryIsRejectedWithItemNames() throws Exception {
		long id = createCategory("Pizzas");
		createItem(id, "Margherita", "food", "18.00");
		long funghi = createItem(id, "Funghi", "food", "20.00");
		mvc.perform(put("/admin/items/" + funghi).header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("categoryId", id, "name", "Funghi", "type", "food", "price", "20.00", "active", false)));

		mvc.perform(delete("/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The category still contains items: Margherita, Funghi"))
			.andExpect(jsonPath("$.items", contains("Margherita", "Funghi")));
	}

	@Test
	void waiterCannotManageCategories() throws Exception {
		String waiter = waiterToken();
		mvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(waiter))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", "X"))).andExpect(status().isForbidden());
	}

}
