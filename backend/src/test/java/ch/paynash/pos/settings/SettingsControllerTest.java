package ch.paynash.pos.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class SettingsControllerTest extends IntegrationTest {

	@Autowired
	TransactionTemplate tx;

	@Test
	void everyLoggedInUserReadsTheSettings() throws Exception {
		String waiter = waiterToken();
		mvc.perform(get("/settings").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("event"))
			.andExpect(jsonPath("$.vatEnabled").value(false));
	}

	@Test
	void adminSavesBusinessDetails() throws Exception {
		String admin = adminToken();
		save(admin, """
				{"businessName":"Kosmetik Aurora","street":"Bahnhofstrasse 1","postalCode":"3000","city":"Bern",
				 "phone":"031 123 45 67","iban":"CH93 0076 2011 6238 5295 7","vatEnabled":true,
				 "vatUid":"che-116.281.710 mwst"}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.businessName").value("Kosmetik Aurora"))
			.andExpect(jsonPath("$.iban").value("CH9300762011623852957"))
			.andExpect(jsonPath("$.vatUid").value("CHE-116.281.710 MWST"));
	}

	@Test
	void invalidIbanAndUidAreRejected() throws Exception {
		String admin = adminToken();
		save(admin, """
				{"businessName":"Aurora","iban":"CH00 1234","vatEnabled":true,"vatUid":"CHE-116.281.711 MWST"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.iban").exists())
			.andExpect(jsonPath("$.errors.vatUid").exists());
		assertThat(settingsService.get().vatEnabled()).isFalse();
	}

	@Test
	void vatWithoutUidIsRejected() throws Exception {
		String admin = adminToken();
		save(admin, "{\"businessName\":\"Aurora\",\"vatEnabled\":true}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.vatUid").value("A VAT number is required when VAT is turned on"));
	}

	@Test
	void salonModeRequiresTheAddress() throws Exception {
		String admin = adminToken();
		changeMode(admin, "salon").andExpect(status().isOk());
		save(admin, "{\"businessName\":\"Aurora\",\"vatEnabled\":false}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.street").exists())
			.andExpect(jsonPath("$.errors.postalCode").exists())
			.andExpect(jsonPath("$.errors.city").exists());
	}

	@Test
	void waiterCannotChangeSettings() throws Exception {
		String waiter = waiterToken();
		save(waiter, "{\"businessName\":\"Aurora\",\"vatEnabled\":false}").andExpect(status().isForbidden());
		changeMode(waiter, "salon").andExpect(status().isForbidden());
	}

	@Test
	void modeChangesWithoutOrders() throws Exception {
		String admin = adminToken();
		changeMode(admin, "salon").andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("salon"));
		assertThat(settingsService.mode()).isEqualTo(Mode.SALON);
		mvc.perform(get("/settings").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(jsonPath("$.mode").value("salon"));
	}

	@Test
	void modeChangeIsRejectedOnceAnOrderExists() throws Exception {
		String admin = adminToken();
		long staff = jdbc.sql("SELECT id FROM staff").query(Long.class).single();
		jdbc.sql("INSERT INTO orders (table_label, sent_by, sent_at) VALUES ('12', ?, now())").param(staff).update();
		changeMode(admin, "salon").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The installation already has orders, so its mode can no longer change"));
		assertThat(settingsService.mode()).isEqualTo(Mode.EVENT);
	}

	@Test
	void modeChangeWaitsForAnOrderBeingSentAndThenFails() throws Exception {
		long staff = createStaff("anna", Role.WAITER, "4711");
		var orderLocked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var send = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
			settingsService.lockModeForOrder();
			jdbc.sql("INSERT INTO orders (table_label, sent_by, sent_at) VALUES ('12', ?, now())")
				.param(staff)
				.update();
			orderLocked.countDown();
			await(release);
		}));
		assertThat(orderLocked.await(5, TimeUnit.SECONDS)).isTrue();
		var change = CompletableFuture.supplyAsync(() -> {
			try {
				settingsService.changeMode(Mode.SALON);
				return "changed";
			}
			catch (ApiException ex) {
				return ex.getMessage();
			}
		});
		Thread.sleep(300);
		assertThat(change).isNotDone();
		release.countDown();
		send.get(5, TimeUnit.SECONDS);
		assertThat(change.get(5, TimeUnit.SECONDS)).contains("already has orders");
		assertThat(settingsService.mode()).isEqualTo(Mode.EVENT);
	}

	private ResultActions save(String token, String body) throws Exception {
		return mvc.perform(put("/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions changeMode(String token, String mode) throws Exception {
		return mvc.perform(put("/admin/settings/mode").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("mode", mode)));
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
