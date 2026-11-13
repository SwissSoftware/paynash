package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class InvoiceSchemaTest extends IntegrationTest {

	private long staff;

	private long customer;

	private long order;

	@BeforeEach
	void setUp() {
		staff = createStaff("anna", Role.WAITER, "4711");
		customer = jdbc.sql("INSERT INTO customer (last_name) VALUES ('Meier') RETURNING id")
			.query(Long.class)
			.single();
		order = jdbc.sql("INSERT INTO orders (customer_id, sent_by, sent_at) VALUES (?, ?, now()) RETURNING id")
			.params(customer, staff)
			.query(Long.class)
			.single();
	}

	@Test
	void anOrderHasAtMostOneActiveInvoice() {
		long first = invoice(1);
		assertThatThrownBy(() -> invoice(2)).isInstanceOf(DataIntegrityViolationException.class);

		jdbc.sql("UPDATE invoice SET status = 'cancelled', cancel_reason = 'wrong service' WHERE id = ?")
			.param(first)
			.update();
		invoice(2);
	}

	@Test
	void anInvoiceHasAtMostOneActivePayment() {
		long invoice = invoice(1);
		payment(invoice);
		assertThatThrownBy(() -> payment(invoice)).isInstanceOf(DataIntegrityViolationException.class);

		jdbc.sql("UPDATE invoice_payment SET removed_at = now(), removed_by = ?, remove_reason = 'mistake'")
			.param(staff)
			.update();
		payment(invoice);
	}

	@Test
	void receiptJobsBelongToAnInvoice() {
		long invoice = invoice(1);
		jdbc.sql("""
				INSERT INTO print_job (invoice_id, item_type, kind, content, created_at)
				VALUES (?, 'receipt', 'normal', 'x', now())""").param(invoice).update();
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO print_job (order_id, item_type, kind, content, created_at)
				VALUES (?, 'receipt', 'normal', 'x', now())""").param(order).update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long invoice(int seq) {
		return jdbc.sql("""
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, total_rappen)
				VALUES (?, 2026, ?, ?, ?, DATE '2026-11-13', now(), ?, '{}', '{}', false, 12000) RETURNING id""")
			.params("2026-%04d".formatted(seq), seq, order, customer, staff)
			.query(Long.class)
			.single();
	}

	private void payment(long invoice) {
		jdbc.sql("""
				INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
				VALUES (?, 'twint', DATE '2026-11-13', ?, now())""").params(invoice, staff).update();
	}

}
