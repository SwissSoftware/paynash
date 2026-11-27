package ch.paynash.pos.invoice;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintJobs;

/**
 * Invoice receipts through the print outbox (spec invoicing, "Receipt on the receipt printer"; ticket-printing,
 * "Receipt print jobs"; add-salon-mode design D8).
 */
@Service
public class InvoiceReceipts {

	private final PrintJobs printJobs;

	private final ReceiptRenderer renderer;

	private final InvoiceRepository invoices;

	public InvoiceReceipts(PrintJobs printJobs, ReceiptRenderer renderer, InvoiceRepository invoices) {
		this.printJobs = printJobs;
		this.renderer = renderer;
		this.invoices = invoices;
	}

	/** Queues the receipt of a newly issued invoice; called inside the issuing transaction. */
	void issued(Invoice invoice) {
		printJobs.queueReceipt(invoice.id(), PrintJob.Kind.NORMAL, renderer.render(invoice, false),
				invoice.lines().size());
	}

	/**
	 * Prints the receipt again, marked "KOPIE" (and "STORNIERT" if cancelled). Printing a copy also resolves an
	 * unclear receipt. Rejected while a receipt of the invoice is still waiting or printing, so a copy never doubles
	 * a receipt that is about to come out.
	 */
	@Transactional
	public List<PrintJob> copy(long invoiceId) {
		Invoice invoice = invoices.findForUpdate(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
		if (printJobs.receipts(invoiceId).stream().anyMatch(PrintJob::inProgress)) {
			throw ApiException.conflict("The receipt is still being printed");
		}
		printJobs.resolveUnclearReceipts(invoiceId);
		return printJobs.queueReceipt(invoiceId, PrintJob.Kind.COPY, renderer.render(invoice, true),
				invoice.lines().size());
	}

	/** The latest receipt job of the invoice, if any. */
	public Optional<PrintJob> latest(long invoiceId) {
		List<PrintJob> jobs = printJobs.receipts(invoiceId);
		return jobs.stream().max(Comparator.comparingLong(PrintJob::id));
	}

	/**
	 * The latest receipt job per printer, ordered by printer name (spec ticket-printing, "Receipt status on the
	 * invoice"; change printer-category-routing design D5).
	 */
	public List<PrintJob> latestPerPrinter(long invoiceId) {
		Map<Long, PrintJob> latest = new HashMap<>();
		for (PrintJob job : printJobs.receipts(invoiceId)) {
			latest.merge(job.printerId() == null ? -job.id() : job.printerId(), job,
					(a, b) -> a.id() > b.id() ? a : b);
		}
		return latest.values()
			.stream()
			.sorted(Comparator.comparing((PrintJob j) -> j.printerName() == null ? "" : j.printerName().toLowerCase())
				.thenComparingLong(PrintJob::id))
			.toList();
	}

}
