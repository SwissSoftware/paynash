package ch.paynash.pos.settings;

/** The single settings row (design D1). Business details are null until an admin enters them. */
public record Settings(Mode mode, String businessName, String street, String postalCode, String city, String phone,
		String email, String website, String iban, String accountHolder, boolean vatEnabled, String vatUid) {

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
