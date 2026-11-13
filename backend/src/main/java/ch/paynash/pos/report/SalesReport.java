package ch.paynash.pos.report;

import java.time.LocalDate;
import java.util.List;

/**
 * The sales report of a period (spec sales-reporting, add-salon-mode design D10). Revenue is the total of the invoices
 * that are not cancelled, on their invoice date. All amounts are in Rappen. The app view and the Excel export are both
 * built from this one object, so their totals always match.
 */
public record SalesReport(LocalDate from, LocalDate to, Summary summary, List<Day> days, List<Service> services,
		List<Customer> customers, List<InvoiceRow> invoices, List<LineRow> lines) {

	/** {@code byMethod} is in the order cash, card, twint, bank_transfer; {@code vat} is empty if no invoice had VAT. */
	public record Summary(long revenueRappen, int invoiceCount, long paidRappen, long openRappen,
			List<MethodTotal> byMethod, List<VatTotal> vat) {
	}

	public record MethodTotal(String method, long rappen) {
	}

	public record VatTotal(int ratePermille, long grossRappen, long netRappen, long vatRappen) {
	}

	public record Day(LocalDate date, long revenueRappen, int invoiceCount) {
	}

	/** Grouped by the name the service had when sold. */
	public record Service(String name, int quantity, long revenueRappen) {
	}

	/** {@code name} is "Deleted customer" for an anonymized customer. */
	public record Customer(int number, String name, int invoiceCount, long revenueRappen) {
	}

	/** Every invoice of the period, also cancelled ones. {@code status} is open, paid, or cancelled. */
	public record InvoiceRow(String number, LocalDate date, int customerNumber, String customerName, long totalRappen,
			long vatRappen, String status, String paymentMethod, LocalDate paidOn) {
	}

	/** Every line of the invoices of the period that are not cancelled. */
	public record LineRow(String invoiceNumber, LocalDate date, int customerNumber, String customerName, String service,
			String category, int quantity, long unitPriceRappen, int vatRatePermille, long amountRappen) {
	}

}
