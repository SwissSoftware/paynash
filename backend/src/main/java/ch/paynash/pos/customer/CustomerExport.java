package ch.paynash.pos.customer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ch.paynash.pos.common.Money;
import tools.jackson.databind.json.JsonMapper;

/**
 * Collects all data stored about one customer for an access request (spec customer-management, "Customer data
 * export"; design D4): the customer record, the invoices with their lines and copied customer details, and all
 * payments including removed ones.
 */
@Component
class CustomerExport {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	CustomerExport(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	Map<String, Object> collect(Customer c) {
		var record = new LinkedHashMap<String, Object>();
		record.put("number", c.number());
		record.put("firstName", c.firstName());
		record.put("lastName", c.lastName());
		record.put("phone", c.phone());
		record.put("email", c.email());
		record.put("street", c.street());
		record.put("postalCode", c.postalCode());
		record.put("city", c.city());
		record.put("note", c.note());
		record.put("active", c.active());
		record.put("anonymized", c.anonymized());

		List<Map<String, Object>> invoices = new ArrayList<>();
		jdbc.sql("""
				SELECT id, number, invoice_date, total_rappen, status, cancel_reason, customer::text AS customer_copy
				  FROM invoice WHERE customer_id = ? ORDER BY year, seq""")
			.param(c.id())
			.query((rs, n) -> {
				var inv = new LinkedHashMap<String, Object>();
				inv.put("id", rs.getLong("id"));
				inv.put("number", rs.getString("number"));
				inv.put("date", rs.getString("invoice_date"));
				inv.put("total", Money.format(rs.getLong("total_rappen")));
				inv.put("status", rs.getString("status"));
				inv.put("cancelReason", rs.getString("cancel_reason"));
				inv.put("customerAsIssued", json.readTree(rs.getString("customer_copy")));
				return inv;
			})
			.list()
			.forEach(invoices::add);
		for (var inv : invoices) {
			long id = (Long) inv.remove("id");
			inv.put("lines", jdbc.sql("""
					SELECT qty, name, unit_price_rappen, amount_rappen, note FROM invoice_line
					 WHERE invoice_id = ? ORDER BY position""").param(id).query((rs, n) -> {
				var line = new LinkedHashMap<String, Object>();
				line.put("quantity", rs.getInt("qty"));
				line.put("name", rs.getString("name"));
				line.put("unitPrice", Money.format(rs.getLong("unit_price_rappen")));
				line.put("amount", Money.format(rs.getLong("amount_rappen")));
				line.put("note", rs.getString("note"));
				return line;
			}).list());
			inv.put("payments", jdbc.sql("""
					SELECT method, paid_on, removed_at IS NOT NULL AS removed, remove_reason FROM invoice_payment
					 WHERE invoice_id = ? ORDER BY recorded_at""").param(id).query((rs, n) -> {
				var p = new LinkedHashMap<String, Object>();
				p.put("method", rs.getString("method"));
				p.put("paidOn", rs.getString("paid_on"));
				p.put("removed", rs.getBoolean("removed"));
				p.put("removeReason", rs.getString("remove_reason"));
				return p;
			}).list());
		}

		var result = new LinkedHashMap<String, Object>();
		result.put("customer", record);
		result.put("invoices", invoices);
		return result;
	}

}
