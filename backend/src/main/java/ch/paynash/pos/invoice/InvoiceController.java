package ch.paynash.pos.invoice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.Money;
import ch.paynash.pos.common.VatRates;
import ch.paynash.pos.printing.TicketView;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.RequiresMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Invoices of salon orders (spec invoicing). Cancelling is admin-only. */
@RestController
@RequiresMode(Mode.SALON)
@Tag(name = "Invoices")
public class InvoiceController {

	private final InvoiceService service;

	private final InvoiceReceipts receipts;

	private final InvoicePdf pdf;

	public InvoiceController(InvoiceService service, InvoiceReceipts receipts, InvoicePdf pdf) {
		this.service = service;
		this.receipts = receipts;
		this.pdf = pdf;
	}

	private InvoiceView view(Invoice invoice) {
		return InvoiceView.of(invoice, receipts.latest(invoice.id()).map(TicketView::of).orElse(null));
	}

	public record LineView(int position, int qty, String name, String category, String unitPrice, String vatRate,
			String amount, String note) {
	}

	public record VatView(String rate, String gross, String net, String vat) {
	}

	public record PaymentView(long id, String method, LocalDate paidOn, String recordedBy, Instant recordedAt,
			Instant removedAt, String removedBy, String removeReason) {
	}

	/**
	 * {@code status} is open, paid, or cancelled; {@code payment} is the active payment, {@code paymentHistory} every
	 * payment including removed ones. {@code vatUid} and {@code vat} are only set if the invoice has VAT.
	 * {@code receipt} is the latest receipt print job: its status, and the printer's error if it waits, is unclear,
	 * or failed (e.g. "no printer configured").
	 */
	public record InvoiceView(long id, String number, long orderId, LocalDate date, Instant issuedAt, String issuedBy,
			Invoice.Status status, Invoice.Business business, Invoice.CustomerCopy customer, List<LineView> lines,
			String total, boolean vatEnabled, String vatUid, List<VatView> vat, Instant cancelledAt,
			String cancelledBy, String cancelReason, PaymentView payment, List<PaymentView> paymentHistory,
			TicketView receipt) {

		static InvoiceView of(Invoice i, TicketView receipt) {
			var payments = i.payments().stream().map(InvoiceView::payment).toList();
			Invoice.Payment active = i.activePayment();
			return new InvoiceView(i.id(), i.number(), i.orderId(), i.invoiceDate(), i.issuedAt(), i.issuedByName(),
					i.status(), i.business(), i.customer(),
					i.lines()
						.stream()
						.map(l -> new LineView(l.position(), l.qty(), l.name(), l.category(),
								Money.format(l.unitPriceRappen()), VatRates.format(l.vatRatePermille()),
								Money.format(l.amountRappen()), l.note()))
						.toList(),
					Money.format(i.totalRappen()), i.vatEnabled(), i.vatUid(),
					i.vat()
						.stream()
						.map(v -> new VatView(VatRates.format(v.ratePermille()), Money.format(v.grossRappen()),
								Money.format(v.netRappen()), Money.format(v.vatRappen())))
						.toList(),
					i.cancelledAt(), i.cancelledByName(), i.cancelReason(), active == null ? null : payment(active),
					payments, receipt);
		}

		private static PaymentView payment(Invoice.Payment p) {
			return new PaymentView(p.id(), p.method(), p.paidOn(), p.recordedByName(), p.recordedAt(), p.removedAt(),
					p.removedByName(), p.removeReason());
		}

	}

	public record InvoiceSummaryView(long id, String number, LocalDate date, int customerNumber, String customerName,
			String total, Invoice.Status status) {
	}

	/** {@code method} is cash, card, twint, or bank_transfer; {@code paidOn} defaults to today. */
	public record PaymentRequest(String method, LocalDate paidOn) {
	}

	public record ReasonRequest(@NotBlank(message = "Reason is required") @Size(max = 140,
			message = "Reason must be at most 140 characters") String reason) {
	}

	@PostMapping("/orders/{orderId}/invoice")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Issue the invoice of a confirmed salon order",
			description = "Accepts an Idempotency-Key header. 409 if business details are missing (missingDetails), "
					+ "the order is voided, has no lines left, or already has an invoice (invoiceId, invoiceNumber).")
	public InvoiceView issue(@PathVariable long orderId, @AuthenticationPrincipal AuthenticatedStaff current) {
		return view(service.issue(orderId, current));
	}

	@GetMapping("/invoices")
	@Operation(summary = "Invoices of a period, newest first",
			description = "from and to are local dates and default to the current month. status is open, paid, or "
					+ "cancelled. q matches the invoice number or the customer name.")
	public ResponseEntity<List<InvoiceSummaryView>> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
		var views = service.list(from, to, Invoice.Status.parse(status), q)
			.stream()
			.map(s -> new InvoiceSummaryView(s.id(), s.number(), s.invoiceDate(), s.customerNumber(), s.customerName(),
					Money.format(s.totalRappen()), s.status()))
			.toList();
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(views);
	}

	@GetMapping("/invoices/{id}")
	@Operation(summary = "An invoice with its lines, VAT, and payments")
	public InvoiceView get(@PathVariable long id) {
		return view(service.get(id));
	}

	@GetMapping(path = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@Operation(summary = "The invoice as an A4 PDF",
			description = "Named Rechnung-<number>.pdf. Created from the stored invoice, so it shows the details valid "
					+ "when the invoice was issued. A cancelled invoice is marked STORNIERT.")
	public ResponseEntity<byte[]> pdf(@PathVariable long id) {
		Invoice invoice = service.get(id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(InvoicePdf.fileName(invoice)).build().toString())
			.body(pdf.render(invoice));
	}

	@PostMapping("/invoices/{id}/receipt")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Print the receipt again as a copy",
			description = "Marked \"KOPIE\" (and \"STORNIERT\" for a cancelled invoice). Also resolves an unclear "
					+ "receipt. 409 while a receipt of the invoice is still waiting or printing.")
	public TicketView printCopy(@PathVariable long id) {
		return TicketView.of(receipts.copy(id));
	}

	@PostMapping("/invoices/{id}/payment")
	@Operation(summary = "Mark an open invoice as paid",
			description = "paidOn must not be before the invoice date or in the future. 409 if the invoice is "
					+ "cancelled or already paid. Accepts an Idempotency-Key header.")
	public InvoiceView pay(@PathVariable long id, @RequestBody PaymentRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		return view(service.pay(id, r.method(), r.paidOn(), current.id()));
	}

	@PostMapping("/admin/invoices/{id}/payment/undo")
	@Operation(summary = "Set a paid invoice back to open",
			description = "The removed payment stays in paymentHistory with who removed it and why.")
	public InvoiceView undoPayment(@PathVariable long id, @Valid @RequestBody ReasonRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		return view(service.undoPayment(id, r.reason(), current.id()));
	}

	@PostMapping("/admin/invoices/{id}/cancel")
	@Operation(summary = "Cancel an invoice",
			description = "The invoice keeps its number and is marked cancelled. 409 if it is paid or already "
					+ "cancelled. The order can then be corrected and invoiced again.")
	public InvoiceView cancel(@PathVariable long id, @Valid @RequestBody ReasonRequest r,
			@AuthenticationPrincipal AuthenticatedStaff current) {
		return view(service.cancel(id, r.reason(), current.id()));
	}

}
