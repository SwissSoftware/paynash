package ch.paynash.pos.invoice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class CancelAndListInvoiceTest extends InvoiceTestSupport {

	@Test
	void cancelledInvoiceKeepsItsNumberAndTheOrderIsInvoicedAgain() throws Exception {
		clock.set("2026-11-13T10:00:00Z");
		staff = login("anna", "4711");
		boss = login("boss", "1111");
		long order = confirm(laura, line(facial, 1), line(tint, 1));
		long first = issued(order);

		postJson(boss, "/admin/invoices/" + first + "/cancel", json("reason", "wrong service"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("cancelled"))
			.andExpect(jsonPath("$.number").value("2026-0001"))
			.andExpect(jsonPath("$.cancelReason").value("wrong service"))
			.andExpect(jsonPath("$.cancelledBy").value("boss Display"))
			.andExpect(jsonPath("$.lines", hasSize(2)));

		long tintLine = jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = 'Eyebrow tint'")
			.param(order)
			.query(Long.class)
			.single();
		postJson(boss, "/admin/orders/" + order + "/lines/" + tintLine + "/cancel", json("reason", "wrong service"))
			.andExpect(status().isOk());
		issue(staff, order).andExpect(status().isCreated())
			.andExpect(jsonPath("$.number").value("2026-0002"))
			.andExpect(jsonPath("$.lines[*].name", contains("Facial")));
		as(staff, get("/orders/" + order)).andExpect(jsonPath("$.invoice.number").value("2026-0002"))
			.andExpect(jsonPath("$.invoice.status").value("open"));
	}

	@Test
	void paidInvoiceCannotBeCancelled() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		jdbc.sql("""
				INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
				SELECT ?, 'cash', invoice_date, issued_by, now() FROM invoice WHERE id = ?""")
			.params(invoice, invoice)
			.update();
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The invoice is paid, set the invoice back to open first"));
	}

	@Test
	void cancellingTwiceIsRejected() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong")).andExpect(status().isOk());
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong"))
			.andExpect(status().isConflict());
	}

	@Test
	void reasonIsRequired() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", " "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.reason").value("Reason is required"));
	}

	@Test
	void staffCannotCancel() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		postJson(staff, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong"))
			.andExpect(status().isForbidden());
	}

	@Test
	void listFiltersByPeriodStatusAndSearch() throws Exception {
		clock.set("2026-10-30T10:00:00Z");
		staff = login("anna", "4711");
		boss = login("boss", "1111");
		issued(confirm(laura, line(facial, 1))); // October
		clock.set("2026-11-13T10:00:00Z");
		staff = login("anna", "4711");
		boss = login("boss", "1111");
		long sara = customer("Sara", "Keller", null, null, null);
		long open = issued(confirm(laura, line(facial, 1)));
		long paid = issued(confirm(sara, line(tint, 2)));
		long cancelled = issued(confirm(sara, line(tint, 1)));
		jdbc.sql("""
				INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
				SELECT id, 'card', invoice_date, issued_by, now() FROM invoice WHERE id = ?""").param(paid).update();
		postJson(boss, "/admin/invoices/" + cancelled + "/cancel", json("reason", "duplicate"))
			.andExpect(status().isOk());

		as(staff, get("/invoices")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].id", contains((int) cancelled, (int) paid, (int) open)))
			.andExpect(jsonPath("$[*].status", contains("cancelled", "paid", "open")))
			.andExpect(jsonPath("$[1].customerName").value("Sara Keller"))
			.andExpect(jsonPath("$[1].total").value("50.00"));
		as(staff, get("/invoices?status=open")).andExpect(jsonPath("$[*].id", contains((int) open)));
		as(staff, get("/invoices?q=keller")).andExpect(jsonPath("$", hasSize(2)));
		as(staff, get("/invoices?q=2026-0003")).andExpect(jsonPath("$[*].id", contains((int) paid)));
		as(staff, get("/invoices?from=2026-10-01&to=2026-10-31")).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].number").value("2026-0001"));
		as(staff, get("/invoices?from=2026-11-30&to=2026-11-01")).andExpect(status().isBadRequest());
	}

}
