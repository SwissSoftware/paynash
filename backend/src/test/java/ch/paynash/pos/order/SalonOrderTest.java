package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.settings.Mode;

/**
 * Orders in salon mode: for a customer, only services, no tickets (add-salon-mode, specs order-taking and
 * ticket-printing).
 */
class SalonOrderTest extends OrderTestSupport {

	private String boss;

	private String staff;

	private long facial;

	private long tint;

	private long laura;

	@BeforeEach
	void setUp() throws Exception {
		boss = adminToken();
		staff = waiterToken();
		settingsService.changeMode(Mode.SALON);
		long face = category("Face");
		facial = menu.insertItem(face, "Facial", ItemType.SERVICE, 12000, null, true, 60, 81);
		tint = menu.insertItem(face, "Eyebrow tint", ItemType.SERVICE, 2500, null, true, null, 26);
		laura = customer("Laura", "Meier");
	}

	// --- confirm (5.1)

	@Test
	void confirmSavesTheOrderForTheCustomerWithVatSnapshots() throws Exception {
		confirm(staff, laura, json("menuItemId", facial, "qty", 1, "note", "sensitive skin"), line(tint, 2))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("sent"))
			.andExpect(jsonPath("$.tableLabel").doesNotExist())
			.andExpect(jsonPath("$.customerId").value(laura))
			.andExpect(jsonPath("$.customerName").value("Laura Meier"))
			.andExpect(jsonPath("$.waiter").value("anna Display"))
			.andExpect(jsonPath("$.lines[*].type", contains("service", "service")))
			.andExpect(jsonPath("$.lines[0].note").value("sensitive skin"))
			.andExpect(jsonPath("$.total").value("170.00"))
			.andExpect(jsonPath("$.invoice").doesNotExist());
		assertThat(jdbc.sql("SELECT vat_rate_snapshot FROM order_line ORDER BY id").query(Integer.class).list())
			.containsExactly(81, 26);
	}

	@Test
	void customerIsRequired() throws Exception {
		postJson(staff, "/orders", "{\"lines\":[" + line(facial, 1) + "]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.customerId").value("Customer is required"));
		assertThat(orderCount()).isZero();
	}

	@Test
	void tableIsRejectedInSalonMode() throws Exception {
		send(staff, "12", line(facial, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tableLabel").value("Salon orders have no table"));
	}

	@Test
	void inactiveCustomerIsRejectedAndNamed() throws Exception {
		as(boss, post("/admin/customers/" + laura + "/deactivate")).andExpect(status().isOk());
		confirm(staff, laura, line(facial, 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Laura Meier is no longer active"))
			.andExpect(jsonPath("$.inactiveCustomerId").value(laura));
		assertThat(orderCount()).isZero();
	}

	@Test
	void onlyServicesCanBeConfirmed() throws Exception {
		long cola = item(category("Drinks"), "Cola", ItemType.DRINK, 450);
		confirm(staff, laura, line(cola, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lines").value("Cola is not a service"));
	}

	@Test
	void doubleTapConfirmSavesOneOrder() throws Exception {
		for (int i = 0; i < 2; i++) {
			as(staff, post("/orders").header(IdempotencyFilter.HEADER, "confirm-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(laura, line(facial, 1)))).andExpect(status().isCreated());
		}
		assertThat(orderCount()).isEqualTo(1);
	}

	@Test
	void eventModeRejectsCustomersAndServices() throws Exception {
		settingsService.changeMode(Mode.EVENT);
		confirm(staff, laura, line(facial, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.customerId").value("Orders in event mode have no customer"));
		send(staff, "12", line(facial, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.lines").value("Facial is a service and can only be sold in salon mode"));
	}

	// --- no tickets (5.2)

	@Test
	void confirmingCancellingAndVoidingCreateNoPrintJobs() throws Exception {
		long order = confirmOrder(laura, line(facial, 1), line(tint, 1));
		postJson(boss, "/admin/orders/" + order + "/lines/" + lineId(order, "Eyebrow tint") + "/cancel",
				json("reason", "not wanted"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value("120.00"));
		postJson(boss, "/admin/orders/" + order + "/void", json("reason", "wrong customer")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("voided"));
		assertThat(jdbc.sql("SELECT count(*) FROM print_job").query(Long.class).single()).isZero();
	}

	// --- history (5.3)

	@Test
	void historyShowsCustomerAndInvoiceStatus() throws Exception {
		clock.set("2026-11-13T09:00:00Z");
		long notInvoiced = confirmOrder(laura, line(facial, 1));
		clock.set("2026-11-13T10:00:00Z");
		long paid = confirmOrder(laura, line(tint, 1));
		long invoice = invoice(paid, 1, "issued");
		jdbc.sql("""
				INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
				VALUES (?, 'cash', DATE '2026-11-13', (SELECT id FROM staff WHERE username = 'anna'), now())""")
			.param(invoice)
			.update();
		clock.set("2026-11-13T11:00:00Z");
		long cancelled = confirmOrder(laura, line(tint, 1));
		invoice(cancelled, 2, "cancelled");
		int number = jdbc.sql("SELECT number FROM customer WHERE id = ?").param(laura).query(Integer.class).single();

		as(staff, get("/orders?date=2026-11-13")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains((int) cancelled, (int) paid, (int) notInvoiced)))
			.andExpect(jsonPath("$[*].customerName", contains("Laura Meier", "Laura Meier", "Laura Meier")))
			.andExpect(jsonPath("$[0].customerNumber").value(number))
			.andExpect(jsonPath("$[0].invoice.status").value("cancelled"))
			.andExpect(jsonPath("$[1].invoice.number").value("2026-0001"))
			.andExpect(jsonPath("$[1].invoice.status").value("paid"))
			.andExpect(jsonPath("$[2].invoice").doesNotExist())
			.andExpect(jsonPath("$[2].printStatus").doesNotExist());
		as(staff, get("/orders/" + paid)).andExpect(jsonPath("$.invoice.number").value("2026-0001"))
			.andExpect(jsonPath("$.invoice.status").value("paid"))
			.andExpect(jsonPath("$.customerNumber").value(number));
	}

	@Test
	void historyShowsDeletedCustomerAfterAnonymization() throws Exception {
		clock.set("2026-11-13T09:00:00Z");
		confirmOrder(laura, line(facial, 1));
		as(boss, delete("/admin/customers/" + laura))
			.andExpect(status().isOk());
		as(staff, get("/orders?date=2026-11-13")).andExpect(jsonPath("$[0].customerName").value("Deleted customer"));
	}

	// --- helpers

	private long customer(String first, String last) throws Exception {
		String body = postJson(staff, "/customers", json("firstName", first, "lastName", last)).andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static String body(long customerId, String... lines) {
		return "{\"customerId\":" + customerId + ",\"lines\":[" + String.join(",", lines) + "]}";
	}

	private ResultActions confirm(String token, long customerId, String... lines) throws Exception {
		return postJson(token, "/orders", body(customerId, lines));
	}

	private long confirmOrder(long customerId, String... lines) throws Exception {
		String body = confirm(staff, customerId, lines).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long orderCount() {
		return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
	}

	private long invoice(long order, int seq, String status) {
		return jdbc.sql("""
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen, status, cancel_reason)
				SELECT ?, 2026, ?, o.id, o.customer_id, DATE '2026-11-13', now(), o.sent_by, '{}', '{}', false, 2500,
				       ?, ?
				FROM orders o WHERE o.id = ? RETURNING id""")
			.params("2026-%04d".formatted(seq), seq, status, "cancelled".equals(status) ? "wrong" : null, order)
			.query(Long.class)
			.single();
	}

}
