package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import ch.paynash.pos.staff.Role;

class OpenOrderTest extends OrderTestSupport {

	private String anna;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
	}

	@Test
	void opensNewEmptyOrderAssignedToWaiter() throws Exception {
		postJson(anna, "/orders", json("tableLabel", "12")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.tableLabel").value("12"))
			.andExpect(jsonPath("$.status").value("open"))
			.andExpect(jsonPath("$.waiter").value("anna Display"))
			.andExpect(jsonPath("$.openedAt").exists())
			.andExpect(jsonPath("$.total").value("0.00"))
			.andExpect(jsonPath("$.lines").isEmpty());
	}

	@Test
	void tableWithOpenOrderReturnsTheExistingOrder() throws Exception {
		long first = openOrder(anna, "12");
		createStaff("ben", Role.WAITER, "2222");
		String ben = login("ben", "2222");

		postJson(ben, "/orders", json("tableLabel", "12")).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(first))
			.andExpect(jsonPath("$.waiter").value("anna Display"));
		assertThat(jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void tableLabelIsCaseInsensitiveAndTrimmed() throws Exception {
		long first = openOrder(anna, "a1");
		postJson(anna, "/orders", json("tableLabel", " A1 ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(first))
			.andExpect(jsonPath("$.tableLabel").value("A1"));
	}

	@Test
	void tableLabelMustBe1To10Alphanumerics() throws Exception {
		for (String bad : new String[] { "", " ", "12345678901", "1 2", "T-1", "Tisch#" }) {
			postJson(anna, "/orders", json("tableLabel", bad)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.tableLabel").exists());
		}
		postJson(anna, "/orders", json("tableLabel", "1234567890")).andExpect(status().isCreated());
	}

	@Test
	void openingRequiresLogin() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(json("tableLabel", "1")))
			.andExpect(status().isUnauthorized());
	}

}
