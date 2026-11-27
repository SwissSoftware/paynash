package ch.paynash.pos.settings;

import java.util.Set;

/**
 * The single settings row: features, template, and terms (change configurable-business-features, design D1) and the
 * business details (spec venue-mode). Business details are null until an admin enters them.
 */
public record Settings(Set<Feature> features, Template template, Terms terms, String businessName, String street,
		String postalCode, String city, String phone, String email, String website, String iban, String accountHolder,
		boolean vatEnabled, String vatUid) {

	public boolean has(Feature feature) {
		return features.contains(feature);
	}

	/** Names of the business details that invoices need but are still missing (spec venue-mode). */
	public java.util.List<String> missingInvoiceDetails() {
		var missing = new java.util.ArrayList<String>();
		if (businessName == null) {
			missing.add("business name");
		}
		if (street == null || postalCode == null || city == null) {
			missing.add("business address");
		}
		return missing;
	}

}
