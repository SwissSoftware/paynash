package ch.paynash.pos.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.support.IntegrationTest;

class CustomerControllerTest extends IntegrationTest {

	private String admin;

	private String staff;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		staff = waiterToken();
		settingsService.changeMode(Mode.SALON);
	}

	// --- create and edit

	@Test
	void createWithNameOnly() throws Exception {
		create(staff, json("firstName", "Laura", "lastName", "Meier")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Laura Meier"))
			.andExpect(jsonPath("$.number").isNumber())
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.phone").doesNotExist());
	}

	@Test
	void numbersCountUp() throws Exception {
		int first = number(create(staff, json("lastName", "Meier")));
		int second = number(create(staff, json("lastName", "Keller")));
		assertThat(second).isEqualTo(first + 1);
	}

	@Test
	void invalidEmailIsRejected() throws Exception {
		create(staff, json("firstName", "Laura", "email", "laura@")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").value("Not a valid email address"));
	}

	@Test
	void missingNameIsRejected() throws Exception {
		create(staff, json("phone", "079 555 12 34")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lastName").value("First name or last name is required"));
	}

	@Test
	void tooLongNoteIsRejected() throws Exception {
		create(staff, json("lastName", "Meier", "note", "x".repeat(501))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.note").value("At most 500 characters"));
	}

	@Test
	void staffEditsACustomer() throws Exception {
		long id = id(create(staff, json("firstName", "Laura", "lastName", "Meier")));
		mvc.perform(put("/customers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(staff))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("firstName", "Laura", "lastName", "Meier", "city", "Bern")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.city").value("Bern"));
	}

	@Test
	void customersAreOnlyAvailableInSalonMode() throws Exception {
		settingsService.changeMode(Mode.EVENT);
		mvc.perform(get("/customers").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("wrong_mode"));
	}

	// --- search

	@Test
	void searchByPartOfANameIgnoringCase() throws Exception {
		create(staff, json("firstName", "Laura", "lastName", "Meier"));
		create(staff, json("firstName", "Peter", "lastName", "Meili"));
		create(staff, json("firstName", "Sara", "lastName", "Keller"));
		search(staff, "?q=MEI").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("Laura Meier", "Peter Meili")));
	}

	@Test
	void searchByPhoneIgnoresSpaces() throws Exception {
		create(staff, json("lastName", "Meier", "phone", "079 555 12 34"));
		create(staff, json("lastName", "Keller", "phone", "+41 31 123 45 67"));
		search(staff, "?q=079 555").andExpect(jsonPath("$[*].name", contains("Meier")));
		search(staff, "?q=0795551234").andExpect(jsonPath("$[*].name", contains("Meier")));
	}

	@Test
	void searchByEmailAndNumber() throws Exception {
		create(staff, json("lastName", "Meier", "email", "laura@example.ch"));
		int keller = number(create(staff, json("lastName", "Keller")));
		search(staff, "?q=EXAMPLE.ch").andExpect(jsonPath("$[*].name", contains("Meier")));
		search(staff, "?q=" + keller).andExpect(jsonPath("$[*].name", contains("Keller")));
	}

	@Test
	void inactiveCustomersOnlyForAdmins() throws Exception {
		long meili = id(create(staff, json("firstName", "Peter", "lastName", "Meili")));
		admin("/admin/customers/" + meili + "/deactivate").andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		search(staff, "?q=meili").andExpect(jsonPath("$", hasSize(0)));
		search(staff, "?q=meili&includeInactive=true").andExpect(status().isForbidden());
		search(admin, "?q=meili&includeInactive=true").andExpect(jsonPath("$[*].name", contains("Peter Meili")));
	}

	// --- duplicates

	@Test
	void possibleDuplicatesByNamePhoneAndEmail() throws Exception {
		create(staff, json("firstName", "Laura", "lastName", "Meier", "phone", "079 555 12 34", "email",
				"laura@example.ch"));
		duplicates("?firstName=laura&lastName=MEIER").andExpect(jsonPath("$[*].name", contains("Laura Meier")));
		duplicates("?firstName=Anna&lastName=Huber&phone=0795551234")
			.andExpect(jsonPath("$[*].name", contains("Laura Meier")));
		duplicates("?firstName=Anna&lastName=Huber&email=Laura@Example.ch")
			.andExpect(jsonPath("$[*].name", contains("Laura Meier")));
		duplicates("?firstName=Laura&lastName=Huber").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void creatingAnywayStillWorks() throws Exception {
		create(staff, json("firstName", "Laura", "lastName", "Meier"));
		create(staff, json("firstName", "Laura", "lastName", "Meier")).andExpect(status().isCreated());
		search(staff, "?q=laura").andExpect(jsonPath("$", hasSize(2)));
	}

	// --- history

	@Test
	void historyListsInvoicesNewestFirstWithTheirSum() throws Exception {
		long laura = id(create(staff, json("firstName", "Laura", "lastName", "Meier")));
		long staffId = staffRepository.findByUsername("anna").orElseThrow().id();
		invoice(laura, staffId, 1, "2026-11-01", 12000, "issued", false);
		invoice(laura, staffId, 2, "2026-11-05", 5000, "issued", true);
		invoice(laura, staffId, 3, "2026-11-10", 8000, "cancelled", false);
		mvc.perform(get("/customers/" + laura).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.customer.name").value("Laura Meier"))
			.andExpect(jsonPath("$.invoices[*].number", contains("2026-0003", "2026-0002", "2026-0001")))
			.andExpect(jsonPath("$.invoices[*].status", contains("cancelled", "paid", "open")))
			.andExpect(jsonPath("$.invoices[0].total").value("80.00"))
			.andExpect(jsonPath("$.revenue").value("170.00"));
	}

	// --- delete, anonymize, export

	@Test
	void deleteWithoutOrdersRemovesTheCustomer() throws Exception {
		long id = id(create(staff, json("lastName", "Huber")));
		mvc.perform(delete("/admin/customers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("deleted"));
		mvc.perform(get("/customers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isNotFound());
	}

	@Test
	void deleteWithInvoicesAnonymizesAndKeepsInvoiceDetails() throws Exception {
		long laura = id(create(staff, json("firstName", "Laura", "lastName", "Meier", "phone", "079 555 12 34")));
		long staffId = staffRepository.findByUsername("anna").orElseThrow().id();
		long invoice = invoice(laura, staffId, 1, "2026-11-01", 12000, "issued", false);

		mvc.perform(delete("/admin/customers/" + laura).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("anonymized"));

		mvc.perform(get("/customers/" + laura).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(jsonPath("$.customer.name").value("Deleted customer"))
			.andExpect(jsonPath("$.customer.phone").doesNotExist())
			.andExpect(jsonPath("$.customer.active").value(false))
			.andExpect(jsonPath("$.invoices", hasSize(1)));
		assertThat(jdbc.sql("SELECT customer->>'name' FROM invoice WHERE id = ?").param(invoice).query(String.class)
			.single()).isEqualTo("Laura Meier");
		search(admin, "?q=laura&includeInactive=true").andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void staffCannotDeleteDeactivateOrExport() throws Exception {
		long id = id(create(staff, json("lastName", "Huber")));
		mvc.perform(delete("/admin/customers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isForbidden());
		mvc.perform(post("/admin/customers/" + id + "/deactivate").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/admin/customers/" + id + "/export").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isForbidden());
	}

	@Test
	void exportContainsRecordInvoicesLinesAndPayments() throws Exception {
		long laura = id(create(staff, json("firstName", "Laura", "lastName", "Meier", "email", "laura@example.ch")));
		int number = jdbc.sql("SELECT number FROM customer WHERE id = ?").param(laura).query(Integer.class).single();
		long staffId = staffRepository.findByUsername("anna").orElseThrow().id();
		invoice(laura, staffId, 1, "2026-11-01", 12000, "issued", true);

		mvc.perform(get("/admin/customers/" + laura + "/export").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"Kunde-" + number + ".json\""))
			.andExpect(jsonPath("$.customer.email").value("laura@example.ch"))
			.andExpect(jsonPath("$.invoices[0].number").value("2026-0001"))
			.andExpect(jsonPath("$.invoices[0].customerAsIssued.name").value("Laura Meier"))
			.andExpect(jsonPath("$.invoices[0].lines[0].name").value("Facial"))
			.andExpect(jsonPath("$.invoices[0].lines[0].amount").value("120.00"))
			.andExpect(jsonPath("$.invoices[0].payments[*].method", containsInAnyOrder("twint")));
	}

	// --- helpers

	private ResultActions create(String token, String body) throws Exception {
		return mvc.perform(post("/customers").header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions search(String token, String query) throws Exception {
		return mvc.perform(get("/customers" + query).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions duplicates(String query) throws Exception {
		return mvc.perform(get("/customers/duplicates" + query).header(HttpHeaders.AUTHORIZATION, bearer(staff)));
	}

	private ResultActions admin(String path) throws Exception {
		return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, bearer(admin)));
	}

	private static long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private static int number(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.number");
	}

	/** Writes an order with one line and its invoice directly, as issuing does (spec invoicing). */
	private long invoice(long customer, long staffId, int seq, String date, long total, String status, boolean paid) {
		long category = jdbc.sql("INSERT INTO category (name) VALUES (?) RETURNING id")
			.param("Face " + seq)
			.query(Long.class)
			.single();
		long item = jdbc.sql("""
				INSERT INTO menu_item (category_id, name, type, price_rappen) VALUES (?, 'Facial', 'service', ?)
				RETURNING id""").params(category, total).query(Long.class).single();
		long order = jdbc.sql("INSERT INTO orders (customer_id, sent_by, sent_at) VALUES (?, ?, now()) RETURNING id")
			.params(customer, staffId)
			.query(Long.class)
			.single();
		long line = jdbc.sql("""
				INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
				                        unit_price_snapshot, qty)
				VALUES (?, ?, 'Facial', 'service', 'Face', ?, 1) RETURNING id""")
			.params(order, item, total)
			.query(Long.class)
			.single();
		long invoice = jdbc.sql("""
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen, status, cancel_reason)
				VALUES (?, 2026, ?, ?, ?, ?::date, now(), ?, '{}', '{"name":"Laura Meier"}', false, ?, ?, ?)
				RETURNING id""")
			.params("2026-%04d".formatted(seq), seq, order, customer, date, staffId, total, status,
					"cancelled".equals(status) ? "wrong service" : null)
			.query(Long.class)
			.single();
		jdbc.sql("""
				INSERT INTO invoice_line (invoice_id, position, order_line_id, name, category, qty, unit_price_rappen,
				                          vat_rate_permille, amount_rappen)
				VALUES (?, 1, ?, 'Facial', 'Face', 1, ?, 81, ?)""").params(invoice, line, total, total).update();
		if (paid) {
			jdbc.sql("""
					INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
					VALUES (?, 'twint', ?::date, ?, now())""").params(invoice, date, staffId).update();
		}
		return invoice;
	}

}
