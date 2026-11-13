package ch.paynash.pos.report;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.common.Money;
import ch.paynash.pos.common.VatRates;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.RequiresMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The sales report for admins in salon mode (spec sales-reporting). */
@RestController
@RequiresMode(Mode.SALON)
@Tag(name = "Admin: sales report")
public class ReportController {

	static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final ReportService service;

	private final SalesReportExcel excel;

	public ReportController(ReportService service, SalesReportExcel excel) {
		this.service = service;
		this.excel = excel;
	}

	public record SummaryView(String revenue, int invoiceCount, String paid, String open, List<MethodView> byMethod,
			List<VatView> vat) {
	}

	public record MethodView(String method, String amount) {
	}

	public record VatView(String rate, String gross, String net, String vat) {
	}

	public record DayView(LocalDate date, String revenue, int invoiceCount) {
	}

	public record ServiceView(String name, int quantity, String revenue) {
	}

	public record CustomerView(int number, String name, int invoiceCount, String revenue) {
	}

	public record ReportView(LocalDate from, LocalDate to, SummaryView summary, List<DayView> days,
			List<ServiceView> services, List<CustomerView> customers) {

		static ReportView of(SalesReport r) {
			var s = r.summary();
			return new ReportView(r.from(), r.to(),
					new SummaryView(Money.format(s.revenueRappen()), s.invoiceCount(), Money.format(s.paidRappen()),
							Money.format(s.openRappen()),
							s.byMethod().stream().map(m -> new MethodView(m.method(), Money.format(m.rappen()))).toList(),
							s.vat()
								.stream()
								.map(v -> new VatView(VatRates.format(v.ratePermille()), Money.format(v.grossRappen()),
										Money.format(v.netRappen()), Money.format(v.vatRappen())))
								.toList()),
					r.days()
						.stream()
						.map(d -> new DayView(d.date(), Money.format(d.revenueRappen()), d.invoiceCount()))
						.toList(),
					r.services()
						.stream()
						.map(x -> new ServiceView(x.name(), x.quantity(), Money.format(x.revenueRappen())))
						.toList(),
					r.customers()
						.stream()
						.map(c -> new CustomerView(c.number(), c.name(), c.invoiceCount(),
								Money.format(c.revenueRappen())))
						.toList());
		}

	}

	@GetMapping("/admin/reports/sales")
	@Operation(summary = "Sales report of a period",
			description = "from and to are local dates and default to the current month; at most 366 days. Revenue is "
					+ "the total of the invoices that are not cancelled, on their invoice date.")
	public ReportView report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ReportView.of(service.report(from, to));
	}

	@GetMapping(path = "/admin/reports/sales.xlsx", produces = XLSX)
	@Operation(summary = "Sales report of a period as Excel",
			description = "Rapport_<from>_<to>.xlsx with the sheets Übersicht, Tage, Dienstleistungen, Kunden, "
					+ "Rechnungen (also cancelled invoices), and Positionen.")
	public ResponseEntity<byte[]> excel(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		SalesReport report = service.report(from, to);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(XLSX))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(SalesReportExcel.fileName(report)).build().toString())
			.body(excel.write(report));
	}

}
