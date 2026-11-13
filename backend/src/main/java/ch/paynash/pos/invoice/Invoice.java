package ch.paynash.pos.invoice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An issued invoice as stored (add-salon-mode design D5). Everything printed or exported comes from here, never from
 * the current settings, customer, or menu. {@code status} is open, paid, or cancelled.
 */
public record Invoice(long id, String number, long orderId, long customerId, LocalDate invoiceDate, Instant issuedAt,
		String issuedByName, Business business, CustomerCopy customer, boolean vatEnabled, String vatUid,
		long totalRappen, Status status, Instant cancelledAt, String cancelledByName, String cancelReason,
		List<Line> lines, List<Vat.Row> vat, List<Payment> payments) {

	public enum Status {

		OPEN, PAID, CANCELLED;

		@com.fasterxml.jackson.annotation.JsonValue
		public String json() {
			return name().toLowerCase();
		}

		/** Parses "open", "paid", or "cancelled"; null or blank means no filter. */
		public static Status parse(String value) {
			if (value == null || value.isBlank()) {
				return null;
			}
			for (Status s : values()) {
				if (s.json().equals(value.trim().toLowerCase())) {
					return s;
				}
			}
			throw ch.paynash.pos.common.ApiException.validation("status", "Status must be open, paid, or cancelled");
		}

	}

	/** The business details at issue time. */
	public record Business(String name, String street, String postalCode, String city, String phone, String email,
			String website, String iban, String accountHolder) {
	}

	/** The customer details at issue time. */
	public record CustomerCopy(int number, String name, String street, String postalCode, String city) {
	}

	public record Line(int position, String name, String category, int qty, int unitPriceRappen, int vatRatePermille,
			long amountRappen, String note) {
	}

	/** {@code removedAt} is set when an admin undid the payment (add-salon-mode design D7). */
	public record Payment(long id, String method, LocalDate paidOn, String recordedByName, Instant recordedAt,
			Instant removedAt, String removedByName, String removeReason) {
	}

	/** The payment that makes the invoice paid, or null. */
	public Payment activePayment() {
		return payments.stream().filter(p -> p.removedAt() == null).findFirst().orElse(null);
	}

}
