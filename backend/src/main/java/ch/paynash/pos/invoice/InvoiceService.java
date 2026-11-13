package ch.paynash.pos.invoice;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.customer.Customer;
import ch.paynash.pos.customer.CustomerRepository;
import ch.paynash.pos.order.LineStatus;
import ch.paynash.pos.order.Order;
import ch.paynash.pos.order.OrderLine;
import ch.paynash.pos.order.OrderRepository;
import ch.paynash.pos.order.OrderStatus;
import ch.paynash.pos.settings.Settings;
import ch.paynash.pos.settings.SettingsService;

/** Issuing and cancelling invoices (spec invoicing, add-salon-mode design D5, D6). */
@Service
public class InvoiceService {

	private final InvoiceRepository invoices;

	private final OrderRepository orders;

	private final CustomerRepository customers;

	private final SettingsService settings;

	private final InvoiceReceipts receipts;

	private final Clock clock;

	private final ZoneId zone;

	public InvoiceService(InvoiceRepository invoices, OrderRepository orders, CustomerRepository customers,
			SettingsService settings, InvoiceReceipts receipts, Clock clock, @Value("${pos.time-zone}") ZoneId zone) {
		this.invoices = invoices;
		this.receipts = receipts;
		this.orders = orders;
		this.customers = customers;
		this.settings = settings;
		this.clock = clock;
		this.zone = zone;
	}

	public Invoice get(long id) {
		return invoices.find(id).orElseThrow(() -> ApiException.notFound("Invoice"));
	}

	public List<InvoiceRepository.Summary> list(LocalDate from, LocalDate to, Invoice.Status status, String query) {
		LocalDate today = today();
		LocalDate start = from != null ? from : today.withDayOfMonth(1);
		LocalDate end = to != null ? to : start.withDayOfMonth(start.lengthOfMonth());
		if (end.isBefore(start)) {
			throw ApiException.validation("to", "The end date must not be before the start date");
		}
		return invoices.list(start, end, status, query);
	}

	/**
	 * Issues the invoice of a confirmed salon order. The order row is locked, the same lock cancelling and voiding
	 * take, so an invoice and a correction of the same order cannot overlap. The number comes from the year's
	 * counter in the same transaction, so a failure leaves no gap. The receipt is queued in the same transaction.
	 */
	@Transactional
	public Invoice issue(long orderId, AuthenticatedStaff staff) {
		Settings s = settings.get();
		List<String> missing = s.missingInvoiceDetails();
		if (!missing.isEmpty()) {
			throw ApiException.conflict(String.join(" and ", missing) + " missing, ask an admin to complete the settings")
				.with("missingDetails", missing);
		}
		Order order = orders.findForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
		if (order.customerId() == null) {
			throw ApiException.conflict("Only orders for a customer can be invoiced");
		}
		if (order.status() != OrderStatus.SENT) {
			throw ApiException.conflict("The order is " + order.status().dbValue() + " and cannot be invoiced");
		}
		var existing = invoices.findActiveIdByOrder(orderId);
		if (existing.isPresent()) {
			Invoice inv = get(existing.get());
			throw ApiException.conflict("The order already has invoice " + inv.number())
				.with("invoiceId", inv.id())
				.with("invoiceNumber", inv.number());
		}
		List<OrderLine> lines = orders.findLines(orderId)
			.stream()
			.filter(l -> l.status() != LineStatus.CANCELLED)
			.toList();
		if (lines.isEmpty()) {
			throw ApiException.conflict("The order has no lines to invoice");
		}
		Customer c = customers.findById(order.customerId()).orElseThrow();

		List<InvoiceRepository.NewLine> newLines = new ArrayList<>();
		List<Vat.Amount> amounts = new ArrayList<>();
		long total = 0;
		int position = 1;
		for (OrderLine l : lines) {
			long amount = l.totalRappen();
			total += amount;
			amounts.add(new Vat.Amount(amount, l.vatRatePermille()));
			newLines.add(new InvoiceRepository.NewLine(position++, l.id(), l.name(), l.category(), l.qty(),
					l.unitPriceRappen(), l.vatRatePermille(), amount, l.note()));
		}
		List<Vat.Row> vat = s.vatEnabled() ? Vat.breakdown(amounts) : List.of();

		LocalDate date = today();
		int seq = invoices.nextSeq(date.getYear());
		String number = "%d-%04d".formatted(date.getYear(), seq);
		var business = new Invoice.Business(s.businessName(), s.street(), s.postalCode(), s.city(), s.phone(),
				s.email(), s.website(), s.iban(), s.accountHolder());
		var customer = new Invoice.CustomerCopy(c.number(), c.displayName(), c.street(), c.postalCode(), c.city());
		long id = invoices.insert(new InvoiceRepository.NewInvoice(number, date.getYear(), seq, orderId, c.id(), date,
				clock.instant(), staff.id(), business, customer, s.vatEnabled(), s.vatEnabled() ? s.vatUid() : null,
				total), newLines, vat);
		Invoice invoice = get(id);
		receipts.issued(invoice);
		return invoice;
	}

	/**
	 * Cancels an invoice; it keeps its number and content (spec invoicing, "Cancel an invoice"). A paid invoice must
	 * be set back to open first.
	 */
	@Transactional
	public Invoice cancel(long id, String reason, long staffId) {
		Invoice invoice = invoices.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Invoice"));
		if (invoice.status() == Invoice.Status.CANCELLED) {
			throw ApiException.conflict("The invoice is already cancelled");
		}
		if (invoice.status() == Invoice.Status.PAID) {
			throw ApiException.conflict("The invoice is paid, set the invoice back to open first");
		}
		invoices.cancel(id, staffId, reason.trim(), clock.instant());
		return get(id);
	}

	/** Payment methods (spec invoicing, "Payment status"). */
	public static final List<String> METHODS = List.of("cash", "card", "twint", "bank_transfer");

	/**
	 * Marks an open invoice as paid. {@code paidOn} defaults to today and must lie between the invoice date and today
	 * (add-salon-mode design D7).
	 */
	@Transactional
	public Invoice pay(long id, String method, LocalDate paidOn, long staffId) {
		if (method == null || !METHODS.contains(method)) {
			throw ApiException.validation("method", "Method must be cash, card, twint, or bank_transfer");
		}
		Invoice invoice = invoices.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Invoice"));
		if (invoice.status() == Invoice.Status.CANCELLED) {
			throw ApiException.conflict("A cancelled invoice cannot be paid");
		}
		if (invoice.status() == Invoice.Status.PAID) {
			throw ApiException.conflict("The invoice is already paid");
		}
		LocalDate today = today();
		LocalDate date = paidOn != null ? paidOn : today;
		if (date.isAfter(today)) {
			throw ApiException.validation("paidOn", "The payment date must not be in the future");
		}
		if (date.isBefore(invoice.invoiceDate())) {
			throw ApiException.validation("paidOn", "The payment date must not be before the invoice date");
		}
		invoices.insertPayment(id, method, date, staffId, clock.instant());
		return get(id);
	}

	/** Sets a paid invoice back to open; the removed payment stays in the history with the reason. */
	@Transactional
	public Invoice undoPayment(long id, String reason, long staffId) {
		Invoice invoice = invoices.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Invoice"));
		if (invoice.status() != Invoice.Status.PAID) {
			throw ApiException.conflict("The invoice is not paid");
		}
		invoices.removePayment(id, staffId, reason.trim(), clock.instant());
		return get(id);
	}

	LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), zone);
	}

}
