package ch.paynash.pos.invoice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class InvoicePaymentTest extends InvoiceTestSupport {

	private long invoice;

	@BeforeEach
	void issueOne() throws Exception {
		clock.set("2026-11-13T10:00:00Z");
		staff = login("anna", "4711");
		boss = login("boss", "1111");
		invoice = issued(confirm(laura, line(facial, 1)));
	}

	@Test
	void paidByTwintToday() throws Exception {
		pay(staff, json("method", "twint")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("paid"))
			.andExpect(jsonPath("$.payment.method").value("twint"))
			.andExpect(jsonPath("$.payment.paidOn").value("2026-11-13"))
			.andExpect(jsonPath("$.payment.recordedBy").value("anna Display"));
		as(staff, get("/orders/" + jdbc.sql("SELECT order_id FROM invoice").query(Long.class).single()))
			.andExpect(jsonPath("$.invoice.status").value("paid"));
	}

	@Test
	void earlierDateWithinRangeIsAccepted() throws Exception {
		clock.set("2026-11-20T10:00:00Z");
		staff = login("anna", "4711");
		pay(staff, json("method", "bank_transfer", "paidOn", "2026-11-18")).andExpect(status().isOk())
			.andExpect(jsonPath("$.payment.paidOn").value("2026-11-18"));
	}

	@Test
	void futureDateIsRejected() throws Exception {
		pay(staff, json("method", "cash", "paidOn", "2026-11-14")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.paidOn").value("The payment date must not be in the future"));
	}

	@Test
	void dateBeforeTheInvoiceIsRejected() throws Exception {
		pay(staff, json("method", "cash", "paidOn", "2026-11-12")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.paidOn").value("The payment date must not be before the invoice date"));
	}

	@Test
	void unknownMethodIsRejected() throws Exception {
		pay(staff, json("method", "bitcoin")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.method").value("Method must be cash, card, twint, or bank_transfer"));
	}

	@Test
	void cancelledInvoiceCannotBePaid() throws Exception {
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "wrong")).andExpect(status().isOk());
		pay(staff, json("method", "cash")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("A cancelled invoice cannot be paid"));
	}

	@Test
	void payingTwiceIsRejected() throws Exception {
		pay(staff, json("method", "cash")).andExpect(status().isOk());
		pay(staff, json("method", "card")).andExpect(status().isConflict());
	}

	@Test
	void adminUndoKeepsTheHistory() throws Exception {
		pay(staff, json("method", "twint")).andExpect(status().isOk());
		postJson(boss, "/admin/invoices/" + invoice + "/payment/undo", json("reason", "wrong invoice marked"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("open"))
			.andExpect(jsonPath("$.payment").doesNotExist())
			.andExpect(jsonPath("$.paymentHistory", hasSize(1)))
			.andExpect(jsonPath("$.paymentHistory[0].method").value("twint"))
			.andExpect(jsonPath("$.paymentHistory[0].removedBy").value("boss Display"))
			.andExpect(jsonPath("$.paymentHistory[0].removeReason").value("wrong invoice marked"));
		pay(staff, json("method", "cash")).andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentHistory[*].method", contains("twint", "cash")));
	}

	@Test
	void staffCannotUndo() throws Exception {
		pay(staff, json("method", "twint")).andExpect(status().isOk());
		postJson(staff, "/admin/invoices/" + invoice + "/payment/undo", json("reason", "x"))
			.andExpect(status().isForbidden());
	}

	@Test
	void undoOnAnOpenInvoiceIsRejected() throws Exception {
		postJson(boss, "/admin/invoices/" + invoice + "/payment/undo", json("reason", "x"))
			.andExpect(status().isConflict());
	}

	private ResultActions pay(String token, String body) throws Exception {
		return postJson(token, "/invoices/" + invoice + "/payment", body);
	}

}
