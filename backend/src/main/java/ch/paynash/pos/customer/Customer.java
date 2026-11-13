package ch.paynash.pos.customer;

/** A salon customer (spec customer-management). All personal fields are null once the customer is anonymized. */
public record Customer(long id, int number, String firstName, String lastName, String phone, String email,
		String street, String postalCode, String city, String note, boolean active, boolean anonymized) {

	public static final String DELETED_NAME = "Deleted customer";

	/** "First Last", or "Deleted customer" after anonymization. */
	public String displayName() {
		if (anonymized) {
			return DELETED_NAME;
		}
		if (firstName == null) {
			return lastName;
		}
		return lastName == null ? firstName : firstName + " " + lastName;
	}

}
