package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.settings.SettingsService;

class IssueInvoiceTest extends InvoiceTestSupport {

	// --- issue (6.1)

	@Test
	void issueCreatesTheInvoiceWithItsContent() throws Exception {
		clock.set("2026-11-13T10:00:00Z");
		long order = confirm(laura, json("menuItemId", facial, "qty", 1, "note", "sensitive skin"), line(tint, 2));
		issue(staff, order).andExpect(status().isCreated())
			.andExpect(jsonPath("$.number").value("2026-0001"))
			.andExpect(jsonPath("$.date").value("2026-11-13"))
			.andExpect(jsonPath("$.status").value("open"))
			.andExpect(jsonPath("$.issuedBy").value("anna Display"))
			.andExpect(jsonPath("$.business.name").value("Kosmetik Aurora"))
			.andExpect(jsonPath("$.business.iban").value("CH9300762011623852957"))
			.andExpect(jsonPath("$.customer.name").value("Laura Meier"))
			.andExpect(jsonPath("$.customer.street").value("Bahnhofstrasse 5"))
			.andExpect(jsonPath("$.lines[*].name", contains("Facial", "Eyebrow tint")))
			.andExpect(jsonPath("$.lines[0].note").value("sensitive skin"))
			.andExpect(jsonPath("$.lines[1].qty").value(2))
			.andExpect(jsonPath("$.lines[1].unitPrice").value("25.00"))
			.andExpect(jsonPath("$.lines[1].amount").value("50.00"))
			.andExpect(jsonPath("$.total").value("170.00"))
			.andExpect(jsonPath("$.vatEnabled").value(false))
			.andExpect(jsonPath("$.vatUid").doesNotExist())
			.andExpect(jsonPath("$.vat", hasSize(0)));
		as(staff, get("/orders/" + order)).andExpect(jsonPath("$.invoice.number").value("2026-0001"))
			.andExpect(jsonPath("$.invoice.status").value("open"));
	}

	@Test
	void cancelledLinesAreNotInvoiced() throws Exception {
		long order = confirm(laura, line(facial, 1), line(tint, 1));
		long tintLine = jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = 'Eyebrow tint'")
			.param(order)
			.query(Long.class)
			.single();
		postJson(boss, "/admin/orders/" + order + "/lines/" + tintLine + "/cancel", json("reason", "not wanted"))
			.andExpect(status().isOk());
		issue(staff, order).andExpect(jsonPath("$.lines[*].name", contains("Facial")))
			.andExpect(jsonPath("$.total").value("120.00"));
	}

	@Test
	void firstInvoiceOfAYearStartsAt0001() throws Exception {
		clock.set("2026-12-31T22:00:00Z"); // 23:00 in Zurich
		staff = login("anna", "4711");
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.number").value("2026-0001"));
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.number").value("2026-0002"));
		clock.set("2027-01-02T09:00:00Z");
		staff = login("anna", "4711");
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.number").value("2027-0001"))
			.andExpect(jsonPath("$.date").value("2027-01-02"));
	}

	@Test
	void invoiceDateFollowsZurichTime() throws Exception {
		clock.set("2026-12-31T23:30:00Z"); // already 1 January in Zurich
		staff = login("anna", "4711");
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.number").value("2027-0001"))
			.andExpect(jsonPath("$.date").value("2027-01-01"));
	}

	@Test
	void secondInvoiceForTheSameOrderIsRejected() throws Exception {
		long order = confirm(laura, line(facial, 1));
		long first = issued(order);
		issue(staff, order).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The order already has invoice 2026-0001"))
			.andExpect(jsonPath("$.invoiceId").value(first));
	}

	@Test
	void voidedOrderIsRejected() throws Exception {
		long order = confirm(laura, line(facial, 1));
		postJson(boss, "/admin/orders/" + order + "/void", json("reason", "wrong customer")).andExpect(status().isOk());
		issue(staff, order).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The order is voided and cannot be invoiced"));
	}

	@Test
	void missingAddressIsRejectedWithTheMessage() throws Exception {
		jdbc.sql("UPDATE installation_settings SET street = NULL").update();
		long order = confirm(laura, line(facial, 1));
		issue(staff, order).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("business address missing, ask an admin to complete the settings"))
			.andExpect(jsonPath("$.missingDetails[0]").value("business address"));
		assertThat(invoiceCount()).isZero();
	}

	@Test
	void doubleRequestCreatesOneInvoice() throws Exception {
		long order = confirm(laura, line(facial, 1));
		for (int i = 0; i < 2; i++) {
			as(staff, post("/orders/" + order + "/invoice").header(IdempotencyFilter.HEADER, "issue-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.number").value("2026-0001"));
		}
		assertThat(invoiceCount()).isOne();
	}

	@Test
	void invoicingIsOnlyAvailableInSalonMode() throws Exception {
		jdbc.sql("UPDATE installation_settings SET mode = 'event'").update();
		settingsService.clearCache();
		issue(staff, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("This function is only available in salon mode"));
	}

	// --- block corrections while invoiced (6.3)

	@Test
	void lineCancelAndVoidAreBlockedWhileInvoiced() throws Exception {
		long order = confirm(laura, line(facial, 1));
		long invoice = issued(order);
		long lineId = jdbc.sql("SELECT id FROM order_line WHERE order_id = ?").param(order).query(Long.class).single();
		postJson(boss, "/admin/orders/" + order + "/lines/" + lineId + "/cancel", json("reason", "wrong"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The order has invoice 2026-0001, cancel the invoice first"));
		postJson(boss, "/admin/orders/" + order + "/void", json("reason", "wrong")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.invoiceId").value(invoice));

		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong service"))
			.andExpect(status().isOk());
		postJson(boss, "/admin/orders/" + order + "/void", json("reason", "wrong")).andExpect(status().isOk());
	}

	// --- final once issued (6.6)

	@Test
	void laterChangesDoNotChangeTheInvoice() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		String before = as(staff, get("/invoices/" + invoice)).andReturn().getResponse().getContentAsString();

		settingsService.updateDetails(new SettingsService.DetailsInput("Aurora Beauty", "Marktgasse 9", "3011",
				"Bern", null, null, null, null, null, true, "CHE-116.281.710 MWST"));
		as(staff, put("/customers/" + laura).contentType(MediaType.APPLICATION_JSON)
			.content(json("firstName", "Laura", "lastName", "Meier-Keller", "street", "Neuweg 2", "postalCode", "3000",
					"city", "Bern")))
			.andExpect(status().isOk());
		jdbc.sql("UPDATE menu_item SET price_rappen = 15000, vat_rate_permille = 0, name = 'Facial deluxe'").update();

		String after = as(staff, get("/invoices/" + invoice)).andReturn().getResponse().getContentAsString();
		assertThat(after).isEqualTo(before);
		as(staff, get("/invoices/" + invoice)).andExpect(jsonPath("$.business.name").value("Kosmetik Aurora"))
			.andExpect(jsonPath("$.customer.name").value("Laura Meier"))
			.andExpect(jsonPath("$.customer.street").value("Bahnhofstrasse 5"))
			.andExpect(jsonPath("$.lines[0].name").value("Facial"))
			.andExpect(jsonPath("$.vatEnabled").value(false));
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.business.name").value("Aurora Beauty"))
			.andExpect(jsonPath("$.customer.name").value("Laura Meier-Keller"))
			.andExpect(jsonPath("$.customer.street").value("Neuweg 2"));
	}

	private long invoiceCount() {
		return jdbc.sql("SELECT count(*) FROM invoice").query(Long.class).single();
	}

}
