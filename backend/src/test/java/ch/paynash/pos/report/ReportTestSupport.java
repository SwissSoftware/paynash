package ch.paynash.pos.report;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;

import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.support.IntegrationTest;

/**
 * A fixed October 2026 data set, written directly into the invoice tables as issuing would:
 * <ul>
 * <li>2026-0001, 1 Oct, Peter, 1 × Facial 120.00, paid cash (order confirmed on 30 Sep)</li>
 * <li>2026-0002, 2 Oct, Laura, 1 × Facial 120.00, paid card, with VAT 8.1 %</li>
 * <li>2026-0003, 2 Oct, Peter, 1 × Facial 120.00 + 1 × Manicure 60.00, open</li>
 * <li>2026-0004, 5 Oct, Laura, 2 × Eyebrow tint 25.00, paid TWINT</li>
 * <li>2026-0005, 6 Oct, Sara, 1 × Facial 120.00, cancelled</li>
 * <li>2026-0006, 31 Oct, Sara, 1 × Manicure 60.00, open; Sara is anonymized</li>
 * <li>2026-0007, 1 Nov, Laura, 1 × Facial 120.00 (outside October)</li>
 * </ul>
 * October: revenue 530.00 from 5 invoices, paid 290.00, open 240.00.
 */
abstract class ReportTestSupport extends IntegrationTest {

	protected String boss;

	protected String staff;

	protected int laura;

	protected int peter;

	protected int sara;

	private long staffId;

	private long item;

	@BeforeEach
	void setUpData() throws Exception {
		boss = adminToken();
		staff = waiterToken();
		settingsService.changeMode(Mode.SALON);
		staffId = staffRepository.findByUsername("anna").orElseThrow().id();
		long category = jdbc.sql("INSERT INTO category (name) VALUES ('Face') RETURNING id").query(Long.class).single();
		item = jdbc.sql("INSERT INTO menu_item (category_id, name, type, price_rappen) VALUES (?, 'X', 'service', 0) "
				+ "RETURNING id").param(category).query(Long.class).single();
		long lauraId = customer("Laura", "Meier");
		long peterId = customer("Peter", "Meili");
		long saraId = customer("Sara", "Keller");
		laura = number(lauraId);
		peter = number(peterId);
		sara = number(saraId);

		invoice(1, "2026-09-30", "2026-10-01", peterId, "issued", "cash", false, line("Facial", 1, 12000));
		invoice(2, "2026-10-02", "2026-10-02", lauraId, "issued", "card", true, line("Facial", 1, 12000));
		invoice(3, "2026-10-02", "2026-10-02", peterId, "issued", null, false, line("Facial", 1, 12000),
				line("Manicure", 1, 6000));
		invoice(4, "2026-10-05", "2026-10-05", lauraId, "issued", "twint", false, line("Eyebrow tint", 2, 2500));
		invoice(5, "2026-10-06", "2026-10-06", saraId, "cancelled", null, false, line("Facial", 1, 12000));
		invoice(6, "2026-10-31", "2026-10-31", saraId, "issued", null, false, line("Manicure", 1, 6000));
		invoice(7, "2026-11-01", "2026-11-01", lauraId, "issued", null, false, line("Facial", 1, 12000));
		jdbc.sql("""
				UPDATE customer SET first_name = NULL, last_name = NULL, active = false, anonymized_at = now()
				WHERE id = ?""").param(saraId).update();
	}

	protected record Line(String name, int qty, int unitRappen) {
	}

	private static Line line(String name, int qty, int unitRappen) {
		return new Line(name, qty, unitRappen);
	}

	private long customer(String first, String last) {
		return jdbc.sql("INSERT INTO customer (first_name, last_name) VALUES (?, ?) RETURNING id")
			.params(first, last)
			.query(Long.class)
			.single();
	}

	private int number(long customerId) {
		return jdbc.sql("SELECT number FROM customer WHERE id = ?").param(customerId).query(Integer.class).single();
	}

	private void invoice(int seq, String orderDay, String day, long customer, String status, String method,
			boolean vat, Line... lines) {
		long order = jdbc.sql("""
				INSERT INTO orders (customer_id, sent_by, sent_at) VALUES (?, ?, (?::date + time '10:00') AT TIME ZONE
				'Europe/Zurich') RETURNING id""").params(customer, staffId, orderDay).query(Long.class).single();
		long total = 0;
		for (Line l : lines) {
			total += (long) l.qty() * l.unitRappen();
		}
		long invoice = jdbc.sql("""
				INSERT INTO invoice (number, year, seq, order_id, customer_id, invoice_date, issued_at, issued_by,
				                     business, customer, vat_enabled, vat_uid, total_rappen, status, cancel_reason)
				VALUES (?, 2026, ?, ?, ?, ?::date, now(), ?, '{}', '{}', ?, ?, ?, ?, ?) RETURNING id""")
			.params("2026-%04d".formatted(seq), seq, order, customer, day, staffId, vat,
					vat ? "CHE-116.281.710 MWST" : null, total, status, "cancelled".equals(status) ? "duplicate" : null)
			.query(Long.class)
			.single();
		int position = 1;
		for (Line l : lines) {
			long orderLine = jdbc.sql("""
					INSERT INTO order_line (order_id, menu_item_id, name_snapshot, type_snapshot, category_snapshot,
					                        unit_price_snapshot, qty)
					VALUES (?, ?, ?, 'service', 'Face', ?, ?) RETURNING id""")
				.params(order, item, l.name(), l.unitRappen(), l.qty())
				.query(Long.class)
				.single();
			jdbc.sql("""
					INSERT INTO invoice_line (invoice_id, position, order_line_id, name, category, qty,
					                          unit_price_rappen, vat_rate_permille, amount_rappen)
					VALUES (?, ?, ?, ?, 'Face', ?, ?, 81, ?)""")
				.params(invoice, position++, orderLine, l.name(), l.qty(), l.unitRappen(),
						(long) l.qty() * l.unitRappen())
				.update();
		}
		if (vat) {
			jdbc.sql("""
					INSERT INTO invoice_vat (invoice_id, vat_rate_permille, gross_rappen, net_rappen, vat_rappen)
					VALUES (?, 81, 12000, 11101, 899)""").param(invoice).update();
		}
		if (method != null) {
			jdbc.sql("""
					INSERT INTO invoice_payment (invoice_id, method, paid_on, recorded_by, recorded_at)
					VALUES (?, ?, ?::date, ?, now())""").params(invoice, method, day, staffId).update();
		}
	}

	protected static LocalDate date(String iso) {
		return LocalDate.parse(iso);
	}

}
