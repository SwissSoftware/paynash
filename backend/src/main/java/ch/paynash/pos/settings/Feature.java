package ch.paynash.pos.settings;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A function of the installation that admins switch on or off (spec business-features, "Feature set"). Each feature is
 * one column on {@code installation_settings} (design D1).
 */
public enum Feature {

	TABLES("tables", "Tables are switched off", null),

	STATIONS("stations", "Stations are switched off", null),

	CUSTOMERS("customers", "Customers are switched off", null),

	INVOICES("invoices", "Invoices are switched off", null),

	SALES_REPORT("sales-report", "The sales report is switched off", INVOICES);

	private final String key;

	private final String offMessage;

	private final Feature requires;

	Feature(String key, String offMessage, Feature requires) {
		this.key = key;
		this.offMessage = offMessage;
		this.requires = requires;
	}

	/** The name used in the API and in messages, e.g. {@code sales-report}. */
	@JsonValue
	public String key() {
		return key;
	}

	/** What a request for this feature is told while it is off (spec business-features, "Functions follow ..."). */
	public String offMessage() {
		return offMessage;
	}

	/** The feature this one cannot work without (spec business-features, "Feature dependencies"). */
	public Optional<Feature> requires() {
		return Optional.ofNullable(requires);
	}

	@JsonCreator
	public static Feature fromKey(String key) {
		for (Feature f : values()) {
			if (f.key.equals(key)) {
				return f;
			}
		}
		throw new IllegalArgumentException("Unknown feature: " + key);
	}

}
