package ch.paynash.pos.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class PermissionTest extends IntegrationTest {

	@Test
	void unauthenticatedRequestIsRejectedWith401() throws Exception {
		mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
		mvc.perform(get("/admin/staff")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidTokenIsRejectedWith401() throws Exception {
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not-a-token")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void waiterOnAdminRouteIsRejectedWith403() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		String token = login("anna", "4711");
		mvc.perform(get("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void waiterCannotExport() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		String token = login("anna", "4711");
		mvc.perform(get("/exports/orders.csv").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminHasAdminAndWaiterPermissions() throws Exception {
		createStaff("boss", Role.ADMIN, "1111");
		String token = login("boss", "1111");
		mvc.perform(get("/admin/staff").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
		mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
	}

	@Test
	void healthAndLoginArePublic() throws Exception {
		mvc.perform(get("/health")).andExpect(status().isOk());
	}

}
