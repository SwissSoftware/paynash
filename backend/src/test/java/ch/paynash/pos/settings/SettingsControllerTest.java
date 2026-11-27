package ch.paynash.pos.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

/** Business details (spec venue-mode) and features, templates, terms (spec business-features). */
class SettingsControllerTest extends IntegrationTest {

	private static final String RESTAURANT_WITHOUT_INVOICES = """
			{"tables":true,"stations":true,"customers":true,"invoices":false,"sales-report":false}""";

	@Autowired
	TransactionTemplate tx;

	@Test
	void everyLoggedInUserReadsTheSettings() throws Exception {
		String waiter = waiterToken();
		mvc.perform(get("/settings").header(HttpHeaders.AUTHORIZATION, bearer(waiter)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.template").value("event"))
			.andExpect(jsonPath("$.features.tables").value(true))
			.andExpect(jsonPath("$.features.stations").value(true))
			.andExpect(jsonPath("$.features.customers").value(false))
			.andExpect(jsonPath("$.features.invoices").value(false))
			.andExpect(jsonPath("$.features['sales-report']").value(false))
			.andExpect(jsonPath("$.terms.waiter").value("Waiter"))
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
	void invoicesRequireTheAddress() throws Exception {
		String admin = adminToken();
		template(admin, "salon").andExpect(status().isOk());
		save(admin, "{\"businessName\":\"Aurora\",\"vatEnabled\":false}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.street").value("Street is required while invoices are on"))
			.andExpect(jsonPath("$.errors.postalCode").exists())
			.andExpect(jsonPath("$.errors.city").exists());
	}

	@Test
	void waiterCannotChangeSettings() throws Exception {
		String waiter = waiterToken();
		save(waiter, "{\"businessName\":\"Aurora\",\"vatEnabled\":false}").andExpect(status().isForbidden());
		features(waiter, RESTAURANT_WITHOUT_INVOICES).andExpect(status().isForbidden());
		terms(waiter, "Staff", "Services", "Confirm").andExpect(status().isForbidden());
		template(waiter, "salon").andExpect(status().isForbidden());
	}

	// --- features (spec business-features)

	@Test
	void adminSwitchesFeaturesAndTheTemplateBecomesCustom() throws Exception {
		String admin = adminToken();
		features(admin, RESTAURANT_WITHOUT_INVOICES).andExpect(status().isOk())
			.andExpect(jsonPath("$.features.customers").value(true))
			.andExpect(jsonPath("$.template").value("custom"));
		assertThat(settingsService.features()).containsExactlyInAnyOrder(Feature.TABLES, Feature.STATIONS,
				Feature.CUSTOMERS);
	}

	/** Spec business-features, "Sales report needs invoices": scenario "Invoices without customers". */
	@Test
	void invoicesWithoutCustomersAreAccepted() throws Exception {
		String admin = adminToken();
		template(admin, "counter").andExpect(status().isOk());
		features(admin, """
				{"tables":false,"stations":false,"customers":false,"invoices":true,"sales-report":true}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.features.customers").value(false));
		assertThat(settingsService.features()).containsExactlyInAnyOrder(Feature.INVOICES, Feature.SALES_REPORT);
	}

	@Test
	void invoicesCannotBeSwitchedOffWhileTheReportIsOn() throws Exception {
		String admin = adminToken();
		template(admin, "salon").andExpect(status().isOk());
		features(admin, """
				{"tables":false,"stations":false,"customers":true,"invoices":false,"sales-report":true}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Switch off sales-report first, because it needs invoices"));
	}

	@Test
	void salesReportNeedsInvoices() throws Exception {
		String admin = adminToken();
		features(admin, """
				{"tables":true,"stations":true,"customers":true,"invoices":false,"sales-report":true}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The feature sales-report needs invoices; switch on invoices as well"))
			.andExpect(jsonPath("$.errors['sales-report']").exists());
	}

	@Test
	void everyFeatureMustBeListed() throws Exception {
		String admin = adminToken();
		features(admin, "{\"tables\":true,\"coffee\":true}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.coffee").value("Unknown feature"))
			.andExpect(jsonPath("$.errors.stations").value("Required"));
	}

	@Test
	void featuresCanChangeAfterOrdersExist() throws Exception {
		String admin = adminToken();
		long staff = jdbc.sql("SELECT id FROM staff").query(Long.class).single();
		jdbc.sql("INSERT INTO orders (table_label, sent_by, sent_at) VALUES ('12', ?, now())").param(staff).update();
		template(admin, "salon").andExpect(status().isOk());
		assertThat(settingsService.features()).containsExactlyInAnyOrder(Feature.CUSTOMERS, Feature.INVOICES,
				Feature.SALES_REPORT);
		assertThat(jdbc.sql("SELECT table_label FROM orders").query(String.class).single()).isEqualTo("12");
	}

	// --- templates and terms

	@Test
	void templatesSetFeaturesAndTerms() throws Exception {
		String admin = adminToken();
		template(admin, "salon").andExpect(status().isOk())
			.andExpect(jsonPath("$.template").value("salon"))
			.andExpect(jsonPath("$.features.tables").value(false))
			.andExpect(jsonPath("$.features.invoices").value(true))
			.andExpect(jsonPath("$.terms.waiter").value("Staff"))
			.andExpect(jsonPath("$.terms.items").value("Services"))
			.andExpect(jsonPath("$.terms.send").value("Confirm"));
		template(admin, "counter").andExpect(status().isOk())
			.andExpect(jsonPath("$.features.customers").value(false))
			.andExpect(jsonPath("$.features.tables").value(false));
		template(admin, "restaurant").andExpect(status().isOk())
			.andExpect(jsonPath("$.features.tables").value(true))
			.andExpect(jsonPath("$.features['sales-report']").value(true));
		assertThat(settingsService.features()).isEqualTo(EnumSet.allOf(Feature.class));
	}

	@Test
	void customCannotBeApplied() throws Exception {
		String admin = adminToken();
		template(admin, "custom").andExpect(status().isBadRequest());
	}

	@Test
	void adjustingATemplateMakesItCustom() throws Exception {
		String admin = adminToken();
		template(admin, "salon").andExpect(status().isOk());
		features(admin, """
				{"tables":true,"stations":false,"customers":true,"invoices":true,"sales-report":true}""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.template").value("custom"))
			.andExpect(jsonPath("$.terms.waiter").value("Staff"));
	}

	@Test
	void adminRenamesTerms() throws Exception {
		String admin = adminToken();
		terms(admin, " Team ", "Leistungen", "Bestätigen").andExpect(status().isOk())
			.andExpect(jsonPath("$.terms.waiter").value("Team"))
			.andExpect(jsonPath("$.terms.send").value("Bestätigen"))
			.andExpect(jsonPath("$.template").value("custom"));
	}

	@Test
	void emptyOrLongTermsAreRejected() throws Exception {
		String admin = adminToken();
		terms(admin, "Staff", " ", "x".repeat(31)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.items").value("Required"))
			.andExpect(jsonPath("$.errors.send").value("At most 30 characters"));
		assertThat(settingsService.get().terms()).isEqualTo(Terms.DEFAULT);
	}

	/** A feature change waits for an order being sent, so an order never sees half-changed features (design D3). */
	@Test
	void featureChangeWaitsForAnOrderBeingSent() throws Exception {
		long staff = createStaff("anna", Role.WAITER, "4711");
		var orderLocked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var send = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
			settingsService.lockFeaturesForOrder();
			jdbc.sql("INSERT INTO orders (table_label, sent_by, sent_at) VALUES ('12', ?, now())")
				.param(staff)
				.update();
			orderLocked.countDown();
			await(release);
		}));
		assertThat(orderLocked.await(5, TimeUnit.SECONDS)).isTrue();
		var change = CompletableFuture.supplyAsync(() -> settingsService.applyTemplate(Template.SALON));
		Thread.sleep(300);
		assertThat(change).isNotDone();
		release.countDown();
		send.get(5, TimeUnit.SECONDS);
		assertThat(change.get(5, TimeUnit.SECONDS).template()).isEqualTo(Template.SALON);
	}

	private ResultActions save(String token, String body) throws Exception {
		return mvc.perform(put("/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions features(String token, String body) throws Exception {
		return mvc.perform(put("/admin/settings/features").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions terms(String token, String waiter, String items, String send) throws Exception {
		return mvc.perform(put("/admin/settings/terms").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("waiter", waiter, "items", items, "send", send)));
	}

	private ResultActions template(String token, String template) throws Exception {
		return mvc.perform(post("/admin/settings/template").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("template", template)));
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
