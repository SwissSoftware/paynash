package ch.paynash.pos.settings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {

	private static final String COLUMNS = """
			mode, business_name, street, postal_code, city, phone, email, website, iban, account_holder, vat_enabled,
			vat_uid""";

	private final JdbcClient jdbc;

	public SettingsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Settings get() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM installation_settings WHERE id = 1").query(this::map).single();
	}

	/** Locks the row until the transaction ends, so a mode change and an order send cannot overlap (design D1). */
	public Settings lockForUpdate() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM installation_settings WHERE id = 1 FOR UPDATE")
			.query(this::map)
			.single();
	}

	/** Shared lock taken by every order send: many sends at once, but never together with a mode change. */
	public Mode lockModeForShare() {
		return jdbc.sql("SELECT mode FROM installation_settings WHERE id = 1 FOR SHARE")
			.query((rs, n) -> Mode.fromDb(rs.getString(1)))
			.single();
	}

	public boolean anyOrderExists() {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM orders)").query(Boolean.class).single();
	}

	public void updateDetails(Settings s, Instant now) {
		jdbc.sql("""
				UPDATE installation_settings SET business_name = ?, street = ?, postal_code = ?, city = ?, phone = ?,
				    email = ?, website = ?, iban = ?, account_holder = ?, vat_enabled = ?, vat_uid = ?, updated_at = ?
				WHERE id = 1""")
			.params(s.businessName(), s.street(), s.postalCode(), s.city(), s.phone(), s.email(), s.website(),
					s.iban(), s.accountHolder(), s.vatEnabled(), s.vatUid(), java.sql.Timestamp.from(now))
			.update();
	}

	public void updateMode(Mode mode, Instant now) {
		jdbc.sql("UPDATE installation_settings SET mode = ?, updated_at = ? WHERE id = 1")
			.params(mode.dbValue(), java.sql.Timestamp.from(now))
			.update();
	}

	private Settings map(ResultSet rs, int n) throws SQLException {
		return new Settings(Mode.fromDb(rs.getString("mode")), rs.getString("business_name"), rs.getString("street"),
				rs.getString("postal_code"), rs.getString("city"), rs.getString("phone"), rs.getString("email"),
				rs.getString("website"), rs.getString("iban"), rs.getString("account_holder"),
				rs.getBoolean("vat_enabled"), rs.getString("vat_uid"));
	}

}
