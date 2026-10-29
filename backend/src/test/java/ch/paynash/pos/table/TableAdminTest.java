package ch.paynash.pos.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.order.OrderRepository;
import ch.paynash.pos.support.IntegrationTest;

class TableAdminTest extends IntegrationTest {

	@Autowired
	private OrderRepository orders;

	private String admin;

	@BeforeEach
	void loginAsAdmin() throws Exception {
		admin = adminToken();
	}

	@Test
	void createWithAndWithoutName() throws Exception {
		create("12", "Terrace window").andExpect(status().isCreated())
			.andExpect(jsonPath("$.number").value("12"))
			.andExpect(jsonPath("$.name").value("Terrace window"))
			.andExpect(jsonPath("$.active").value(true));
		create(" t5 ", "").andExpect(status().isCreated())
			.andExpect(jsonPath("$.number").value("T5"))
			.andExpect(jsonPath("$.name").doesNotExist());
		as(admin, get("/admin/tables")).andExpect(jsonPath("$[*].number", contains("12", "T5")));
	}

	@Test
	void invalidNumbersAreRejectedOnTheNumberField() throws Exception {
		for (String number : new String[] { "", "12345678901", "T-5", "1 2" }) {
			create(number, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.number").value("Number must be 1 to 10 letters or digits"));
		}
		mvc.perform(post("/admin/tables").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.number").exists());
		create("1", "x".repeat(61)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
		assertThat(count()).isZero();
	}

	@Test
	void duplicateNumbersAreRejectedIgnoringCaseAndIncludingInactiveTables() throws Exception {
		long t5 = id(create("T5", null));
		update(t5, "T5", null, false).andExpect(status().isOk());
		create("t5", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.number").value("This number is already in use"));
		long twelve = id(create("12", null));
		update(twelve, "t5", null, true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.number").value("This number is already in use"));
		assertThat(count()).isEqualTo(2);
	}

	@Test
	void keepingTheOwnNumberIsNotADuplicate() throws Exception {
		long id = id(create("12", "Terrace window"));
		update(id, "12", "Terrace corner", true).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Terrace corner"));
		waiterList().andExpect(jsonPath("$[0].name").value("Terrace corner"));
	}

	@Test
	void renumberingKeepsSentOrders() throws Exception {
		long id = id(create("12", null));
		sentOrder("12");
		update(id, "14", null, true).andExpect(status().isOk()).andExpect(jsonPath("$.number").value("14"));
		waiterList().andExpect(jsonPath("$[*].number", contains("14")));
		as(admin, get("/orders")).andExpect(jsonPath("$[0].tableLabel").value("12"));
	}

	@Test
	void deactivateHidesFromWaitersAndReactivateShowsAgain() throws Exception {
		long id = id(create("T1", null));
		update(id, "T1", null, false).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
		waiterList().andExpect(jsonPath("$.length()").value(0));
		as(admin, get("/admin/tables")).andExpect(jsonPath("$[0].active").value(false));
		update(id, "T1", null, true).andExpect(status().isOk());
		waiterList().andExpect(jsonPath("$[*].number", contains("T1")));
	}

	@Test
	void deleteKeepsSentOrdersAndFreesTheNumber() throws Exception {
		long id = id(create("8", null));
		sentOrder("8");
		as(admin, delete("/admin/tables/" + id)).andExpect(status().isNoContent());
		assertThat(count()).isZero();
		as(admin, get("/orders")).andExpect(jsonPath("$[0].tableLabel").value("8"));
		create("8", null).andExpect(status().isCreated());
	}

	@Test
	void unknownTableIsNotFound() throws Exception {
		as(admin, delete("/admin/tables/999")).andExpect(status().isNotFound());
		update(999, "1", null, true).andExpect(status().isNotFound());
	}

	@Test
	void waiterListShowsActiveTablesInNaturalOrder() throws Exception {
		create("10", null);
		create("T1", null);
		create("2", "Window");
		long three = id(create("3", null));
		update(three, "3", null, false);
		waiterList().andExpect(status().isOk())
			.andExpect(jsonPath("$[*].number", contains("2", "10", "T1")))
			.andExpect(jsonPath("$[0].name").value("Window"))
			.andExpect(jsonPath("$[0].active").doesNotExist());
	}

	@Test
	void waiterCanOnlyReadTheList() throws Exception {
		long id = id(create("1", null));
		String waiter = waiterToken();
		as(waiter, get("/tables")).andExpect(status().isOk());
		as(waiter, get("/admin/tables")).andExpect(status().isForbidden());
		as(waiter, post("/admin/tables").contentType(MediaType.APPLICATION_JSON).content(json("number", "2")))
			.andExpect(status().isForbidden());
		as(waiter, put("/admin/tables/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(json("number", "3", "active", true))).andExpect(status().isForbidden());
		as(waiter, delete("/admin/tables/" + id)).andExpect(status().isForbidden());
		assertThat(jdbc.sql("SELECT number FROM dining_table").query(String.class).list()).containsExactly("1");
	}

	@Test
	void listNeedsASession() throws Exception {
		mvc.perform(get("/tables")).andExpect(status().isUnauthorized());
	}

	// --- helpers

	private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions create(String number, String name) throws Exception {
		return as(admin, post("/admin/tables").contentType(MediaType.APPLICATION_JSON).content(body(number, name, null)));
	}

	private ResultActions update(long id, String number, String name, boolean active) throws Exception {
		return as(admin,
				put("/admin/tables/" + id).contentType(MediaType.APPLICATION_JSON).content(body(number, name, active)));
	}

	private ResultActions waiterList() throws Exception {
		return as(admin, get("/tables"));
	}

	private static String body(String number, String name, Boolean active) {
		return "{\"number\":\"" + number + "\"" + (name == null ? "" : ",\"name\":\"" + name + "\"")
				+ (active == null ? "" : ",\"active\":" + active) + "}";
	}

	private static long id(ResultActions created) throws Exception {
		return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	/** An order sent now for {@code label}, without lines; enough for the history to show its table. */
	private void sentOrder(String label) {
		long staffId = jdbc.sql("SELECT id FROM staff WHERE username = 'boss'").query(Long.class).single();
		orders.insertSent(label, staffId, clock.instant());
	}

	private long count() {
		return jdbc.sql("SELECT count(*) FROM dining_table").query(Long.class).single();
	}

}
