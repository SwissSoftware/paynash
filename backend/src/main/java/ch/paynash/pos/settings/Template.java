package ch.paynash.pos.settings;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Starting combinations of features and terms (spec business-features, "Business templates"; design D4). Only
 * {@link SettingsService} reads them; business logic always asks for features, never for the template.
 */
public enum Template {

	EVENT(EnumSet.of(Feature.TABLES, Feature.STATIONS), Terms.DEFAULT),

	RESTAURANT(EnumSet.of(Feature.TABLES, Feature.STATIONS, Feature.CUSTOMERS, Feature.INVOICES, Feature.SALES_REPORT),
			Terms.DEFAULT),

	SALON(EnumSet.of(Feature.CUSTOMERS, Feature.INVOICES, Feature.SALES_REPORT),
			new Terms("Staff", "Services", "Confirm")),

	COUNTER(EnumSet.noneOf(Feature.class), new Terms("Staff", "Menu", "Sell")),

	/** Set when an admin changes features or terms after applying a template. Cannot be applied itself. */
	CUSTOM(null, null);

	private final EnumSet<Feature> features;

	private final Terms terms;

	Template(EnumSet<Feature> features, Terms terms) {
		this.features = features;
		this.terms = terms;
	}

	/** A copy of the template's features, or null for {@link #CUSTOM}. */
	public Set<Feature> features() {
		return features == null ? null : EnumSet.copyOf(features);
	}

	public Terms terms() {
		return terms;
	}

	@JsonValue
	public String dbValue() {
		return name().toLowerCase();
	}

	@JsonCreator
	public static Template fromDb(String value) {
		return valueOf(value.toUpperCase());
	}

}
