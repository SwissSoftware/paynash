package ch.paynash.pos.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.support.IntegrationTest;

class StaffAdminControllerTest extends IntegrationTest {

	private long adminId;

	private String adminToken;

	@BeforeEach
	void loginAsAdmin() throws Exception {
		adminId = createStaff("boss", Role.ADMIN, "1111");
		adminToken = login("boss", "1111");
	}

	@Test
	void createdWaiterCanLogIn() throws Exception {
		createWaiter("anna", "4711").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.username").value("anna"))
			.andExpect(jsonPath("$.role").value("waiter"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.pin").doesNotExist());
		login("anna", "4711");
	}

	@Test
	void pinIsStoredHashed() throws Exception {
		createWaiter("anna", "4711");
		String hash = jdbc.sql("SELECT pin_hash FROM staff WHERE username = 'anna'").query(String.class).single();
		assertThat(hash).startsWith("$argon2id$");
	}

	@Test
	void duplicateUsernameIsAValidationErrorAndCreatesNothing() throws Exception {
		createWaiter("anna", "4711").andExpect(status().isCreated());
		createWaiter("ANNA", "1234").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").value("Username already exists"));
		assertThat(jdbc.sql("SELECT count(*) FROM staff").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void invalidInputIsRejectedPerField() throws Exception {
		mvc.perform(post("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "a b", "displayName", "", "role", "waiter", "pin", "12")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").exists())
			.andExpect(jsonPath("$.errors.displayName").exists())
			.andExpect(jsonPath("$.errors.pin").value("PIN must be 4 to 8 digits"));
	}

	@Test
	void unknownRoleIsRejected() throws Exception {
		mvc.perform(post("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "x", "displayName", "X", "role", "chef", "pin", "1234")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void waiterCannotCreateOrEditAccounts() throws Exception {
		long annaId = createStaff("anna", Role.WAITER, "4711");
		String waiterToken = login("anna", "4711");
		mvc.perform(post("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(waiterToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "bob", "displayName", "Bob", "role", "waiter", "pin", "1234")))
			.andExpect(status().isForbidden());
		mvc.perform(put("/admin/staff/" + annaId).header(HttpHeaders.AUTHORIZATION, bearer(waiterToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("displayName", "Anna", "role", "admin", "active", true)))
			.andExpect(status().isForbidden());
	}

	@Test
	void listShowsAllAccounts() throws Exception {
		createWaiter("anna", "4711");
		mvc.perform(get("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[*].username").value(org.hamcrest.Matchers.containsInAnyOrder("boss", "anna")));
	}

	@Test
	void editChangesNameAndRoleAndCanReactivate() throws Exception {
		long annaId = createStaff("anna", Role.WAITER, "4711");
		update(annaId, "Anna M.", "admin", false).andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Anna M."))
			.andExpect(jsonPath("$.active").value(false));
		update(annaId, "Anna M.", "admin", true).andExpect(status().isOk());
		String token = login("anna", "4711");
		mvc.perform(get("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
	}

	@Test
	void resetPinReplacesOldPinAndClearsLockout() throws Exception {
		long annaId = createStaff("anna", Role.WAITER, "4711");
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("username", "anna", "pin", "0000")));
		}
		mvc.perform(post("/admin/staff/" + annaId + "/pin").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("pin", "8642"))).andExpect(status().isNoContent());

		login("anna", "8642");
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "anna", "pin", "4711"))).andExpect(status().isUnauthorized());
	}

	@Test
	void lastActiveAdminCannotBeDeactivatedOrDemoted() throws Exception {
		update(adminId, "Boss", "admin", false).andExpect(status().isConflict());
		update(adminId, "Boss", "waiter", true).andExpect(status().isConflict());
		createStaff("second", Role.ADMIN, "2222");
		update(adminId, "Boss", "waiter", true).andExpect(status().isOk());
	}

	@Test
	void unknownIdGives404() throws Exception {
		update(999_999, "X", "waiter", true).andExpect(status().isNotFound());
	}

	private ResultActions createWaiter(String username, String pin) throws Exception {
		return mvc.perform(post("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", username, "displayName", username, "role", "waiter", "pin", pin)));
	}

	private ResultActions update(long id, String displayName, String role, boolean active) throws Exception {
		return mvc.perform(put("/admin/staff/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("displayName", displayName, "role", role, "active", active)));
	}

}
