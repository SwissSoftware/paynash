package ch.paynash.pos.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class AuthControllerTest extends IntegrationTest {

	@Test
	void successfulLoginReturnsTokenAndStaff() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		attemptLogin("anna", "4711").andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andExpect(jsonPath("$.staff.username").value("anna"))
			.andExpect(jsonPath("$.staff.displayName").value("anna Display"))
			.andExpect(jsonPath("$.staff.role").value("waiter"));
	}

	@Test
	void usernameIsCaseInsensitive() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		attemptLogin("Anna", "4711").andExpect(status().isOk());
	}

	@Test
	void wrongPinAndUnknownUserGiveTheSameGenericError() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		attemptLogin("anna", "9999").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials"));
		attemptLogin("nobody", "4711").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials"));
	}

	@Test
	void fiveFailuresLockTheAccountForFiveMinutes() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		for (int i = 0; i < 5; i++) {
			attemptLogin("anna", "0000").andExpect(status().isUnauthorized());
		}
		attemptLogin("anna", "4711").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials"));

		clock.advance(Duration.ofMinutes(4));
		attemptLogin("anna", "4711").andExpect(status().isUnauthorized());

		clock.advance(Duration.ofMinutes(1).plusSeconds(1));
		attemptLogin("anna", "4711").andExpect(status().isOk());
	}

	@Test
	void successfulLoginResetsTheFailureCounter() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		for (int i = 0; i < 4; i++) {
			attemptLogin("anna", "0000");
		}
		attemptLogin("anna", "4711").andExpect(status().isOk());
		for (int i = 0; i < 4; i++) {
			attemptLogin("anna", "0000");
		}
		attemptLogin("anna", "4711").andExpect(status().isOk());
	}

	@Test
	void sessionExpiresAfterTwelveHours() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		String token = login("anna", "4711");

		clock.advance(Duration.ofHours(12).minusMinutes(1));
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());

		clock.advance(Duration.ofMinutes(1));
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsTheLoggedInStaff() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		String token = login("anna", "4711");
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("anna"));
	}

	@Test
	void logoutEndsTheSessionImmediately() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		String token = login("anna", "4711");
		mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void deactivationRevokesExistingSessionsAndBlocksLogin() throws Exception {
		createStaff("boss", Role.ADMIN, "1111");
		long annaId = createStaff("anna", Role.WAITER, "4711");
		String adminToken = login("boss", "1111");
		String annaToken = login("anna", "4711");

		mvc.perform(put("/admin/staff/" + annaId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("displayName", "Anna", "role", "waiter", "active", false))).andExpect(status().isOk());

		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(annaToken)))
			.andExpect(status().isUnauthorized());
		attemptLogin("anna", "4711").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials"));
	}

	private ResultActions attemptLogin(String username, String pin) throws Exception {
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(json("username", username, "pin", pin)));
	}

}
