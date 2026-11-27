package ch.paynash.pos.settings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {

	private static final String FEATURE_COLUMNS = """
			feature_tables, feature_stations, feature_customers, feature_invoices, feature_sales_report""";

	private static final String COLUMNS = FEATURE_COLUMNS + """
			, template, term_waiter, term_items, term_send, business_name, street, postal_code, city, phone, email,
			website, iban, account_holder, vat_enabled, vat_uid""";

	private final JdbcClient jdbc;

	public SettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Settings get() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM installation_settings WHERE id = 1").query(this::map).single();
	}

	/** Locks the row until the transaction ends, so a settings change and an order send cannot overlap. */
	public Settings lockForUpdate() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM installation_settings WHERE id = 1 FOR UPDATE")
			.query(this::map)
			.single();
	}

	/**
	 * Shared lock taken by every order send: many sends at once, but never together with a feature change (design
	 * D3).
	 */
	public Set<Feature> lockFeaturesForShare() {
		return jdbc.sql("SELECT " + FEATURE_COLUMNS + " FROM installation_settings WHERE id = 1 FOR SHARE")
			.query((rs, n) -> features(rs))
			.single();
	}

	public void updateDetails(Settings s, Instant now) {
		jdbc.sql("""
				UPDATE installation_settings SET business_name = ?, street = ?, postal_code = ?, city = ?, phone = ?,
				    email = ?, website = ?, iban = ?, account_holder = ?, vat_enabled = ?, vat_uid = ?, updated_at = ?
				WHERE id = 1""")
			.params(s.businessName(), s.street(), s.postalCode(), s.city(), s.phone(), s.email(), s.website(),
					s.iban(), s.accountHolder(), s.vatEnabled(), s.vatUid(), Timestamp.from(now))
			.update();
	}

	public void updateFeaturesAndTerms(Set<Feature> features, Template template, Terms terms, Instant now) {
		jdbc.sql("""
				UPDATE installation_settings SET feature_tables = ?, feature_stations = ?, feature_customers = ?,
				    feature_invoices = ?, feature_sales_report = ?, template = ?, term_waiter = ?, term_items = ?,
				    term_send = ?, updated_at = ?
				WHERE id = 1""")
			.params(features.contains(Feature.TABLES), features.contains(Feature.STATIONS),
					features.contains(Feature.CUSTOMERS), features.contains(Feature.INVOICES),
					features.contains(Feature.SALES_REPORT), template.dbValue(), terms.waiter(), terms.items(),
					terms.send(), Timestamp.from(now))
			.update();
	}

	private Settings map(ResultSet rs, int n) throws SQLException {
		return new Settings(features(rs), Template.fromDb(rs.getString("template")),
				new Terms(rs.getString("term_waiter"), rs.getString("term_items"), rs.getString("term_send")),
				rs.getString("business_name"), rs.getString("street"), rs.getString("postal_code"),
				rs.getString("city"), rs.getString("phone"), rs.getString("email"), rs.getString("website"),
				rs.getString("iban"), rs.getString("account_holder"), rs.getBoolean("vat_enabled"),
				rs.getString("vat_uid"));
	}

	private static Set<Feature> features(ResultSet rs) throws SQLException {
		var features = EnumSet.noneOf(Feature.class);
		if (rs.getBoolean("feature_tables")) {
			features.add(Feature.TABLES);
		}
		if (rs.getBoolean("feature_stations")) {
			features.add(Feature.STATIONS);
		}
		if (rs.getBoolean("feature_customers")) {
			features.add(Feature.CUSTOMERS);
		}
		if (rs.getBoolean("feature_invoices")) {
			features.add(Feature.INVOICES);
		}
		if (rs.getBoolean("feature_sales_report")) {
			features.add(Feature.SALES_REPORT);
		}
		return features;
	}

}
