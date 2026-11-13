package ch.paynash.pos.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ch.paynash.pos.common.VatRates;

/**
 * Writes a {@link SalesReport} as an Excel file (spec sales-reporting, "Excel export"; add-salon-mode design D10).
 * Amounts are numeric cells with two decimals, dates are date cells, and every sheet has a header row. Sheet names and
 * headers are German, like the invoices.
 */
@Component
public class SalesReportExcel {

	private static final Map<String, String> METHOD = Map.of("cash", "Bar", "card", "Karte", "twint", "TWINT",
			"bank_transfer", "Überweisung");

	private static final Map<String, String> STATUS = Map.of("open", "offen", "paid", "bezahlt", "cancelled",
			"storniert");

	public static String fileName(SalesReport report) {
		return "Rapport_" + report.from() + "_" + report.to() + ".xlsx";
	}

	public byte[] write(SalesReport r) {
		try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
			var s = new Styles(wb);
			overview(wb.createSheet("Übersicht"), s, r);
			days(wb.createSheet("Tage"), s, r);
			services(wb.createSheet("Dienstleistungen"), s, r);
			customers(wb.createSheet("Kunden"), s, r);
			invoices(wb.createSheet("Rechnungen"), s, r);
			lines(wb.createSheet("Positionen"), s, r);
			wb.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void overview(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 30, 16, "Kennzahl", "Wert");
		int i = 1;
		i = dateRow(sh, s, i, "Von", r.from());
		i = dateRow(sh, s, i, "Bis", r.to());
		i = amountRow(sh, s, i, "Umsatz CHF", r.summary().revenueRappen());
		Row count = sh.createRow(i++);
		count.createCell(0).setCellValue("Anzahl Rechnungen");
		count.createCell(1).setCellValue(r.summary().invoiceCount());
		i = amountRow(sh, s, i, "Bezahlt CHF", r.summary().paidRappen());
		i = amountRow(sh, s, i, "Offen CHF", r.summary().openRappen());
		for (var m : r.summary().byMethod()) {
			i = amountRow(sh, s, i, "Bezahlt mit " + METHOD.get(m.method()) + " CHF", m.rappen());
		}
		for (var v : r.summary().vat()) {
			String rate = VatRates.format(v.ratePermille()) + "%";
			i = amountRow(sh, s, i, "MWST " + rate + " Brutto CHF", v.grossRappen());
			i = amountRow(sh, s, i, "MWST " + rate + " Netto CHF", v.netRappen());
			i = amountRow(sh, s, i, "MWST " + rate + " Steuer CHF", v.vatRappen());
		}
	}

	private static void days(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 14, 16, 18, "Datum", "Umsatz CHF", "Anzahl Rechnungen");
		int i = 1;
		for (var d : r.days()) {
			Row row = sh.createRow(i++);
			date(row, 0, d.date(), s);
			amount(row, 1, d.revenueRappen(), s);
			row.createCell(2).setCellValue(d.invoiceCount());
		}
	}

	private static void services(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 36, 10, 16, "Dienstleistung", "Anzahl", "Umsatz CHF");
		int i = 1;
		for (var x : r.services()) {
			Row row = sh.createRow(i++);
			row.createCell(0).setCellValue(x.name());
			row.createCell(1).setCellValue(x.quantity());
			amount(row, 2, x.revenueRappen(), s);
		}
	}

	private static void customers(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 12, 30, 18, 16, "Kunden-Nr.", "Name", "Anzahl Rechnungen", "Umsatz CHF");
		int i = 1;
		for (var c : r.customers()) {
			Row row = sh.createRow(i++);
			row.createCell(0).setCellValue(c.number());
			row.createCell(1).setCellValue(c.name());
			row.createCell(2).setCellValue(c.invoiceCount());
			amount(row, 3, c.revenueRappen(), s);
		}
	}

	private static void invoices(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 14, 12, 12, 30, 14, 12, 12, 14, 14, "Rechnung", "Datum", "Kunden-Nr.", "Kunde", "Total CHF",
				"MWST CHF", "Status", "Zahlungsart", "Bezahlt am");
		int i = 1;
		for (var x : r.invoices()) {
			Row row = sh.createRow(i++);
			row.createCell(0).setCellValue(x.number());
			date(row, 1, x.date(), s);
			row.createCell(2).setCellValue(x.customerNumber());
			row.createCell(3).setCellValue(x.customerName());
			amount(row, 4, x.totalRappen(), s);
			amount(row, 5, x.vatRappen(), s);
			row.createCell(6).setCellValue(STATUS.get(x.status()));
			if (x.paymentMethod() != null) {
				row.createCell(7).setCellValue(METHOD.get(x.paymentMethod()));
			}
			if (x.paidOn() != null) {
				date(row, 8, x.paidOn(), s);
			}
		}
	}

	private static void lines(Sheet sh, Styles s, SalesReport r) {
		header(sh, s, 14, 12, 12, 30, 30, 20, 10, 14, 10, 14, "Rechnung", "Datum", "Kunden-Nr.", "Kunde",
				"Dienstleistung", "Kategorie", "Anzahl", "Preis CHF", "MWST %", "Betrag CHF");
		int i = 1;
		for (var l : r.lines()) {
			Row row = sh.createRow(i++);
			row.createCell(0).setCellValue(l.invoiceNumber());
			date(row, 1, l.date(), s);
			row.createCell(2).setCellValue(l.customerNumber());
			row.createCell(3).setCellValue(l.customerName());
			row.createCell(4).setCellValue(l.service());
			row.createCell(5).setCellValue(l.category());
			row.createCell(6).setCellValue(l.quantity());
			amount(row, 7, l.unitPriceRappen(), s);
			var rate = row.createCell(8);
			rate.setCellValue(l.vatRatePermille() / 10.0);
			rate.setCellStyle(s.percent);
			amount(row, 9, l.amountRappen(), s);
		}
	}

	/** Header titles follow the column widths (in characters): {@code header(sh, s, 14, 16, "A", "B")}. */
	private static void header(Sheet sh, Styles s, Object... widthsThenTitles) {
		List<Object> all = List.of(widthsThenTitles);
		int columns = all.size() / 2;
		Row row = sh.createRow(0);
		for (int c = 0; c < columns; c++) {
			sh.setColumnWidth(c, (Integer) all.get(c) * 256);
			var cell = row.createCell(c);
			cell.setCellValue((String) all.get(columns + c));
			cell.setCellStyle(s.header);
		}
		sh.createFreezePane(0, 1);
	}

	private static int amountRow(Sheet sh, Styles s, int i, String label, long rappen) {
		Row row = sh.createRow(i);
		row.createCell(0).setCellValue(label);
		amount(row, 1, rappen, s);
		return i + 1;
	}

	private static int dateRow(Sheet sh, Styles s, int i, String label, LocalDate value) {
		Row row = sh.createRow(i);
		row.createCell(0).setCellValue(label);
		date(row, 1, value, s);
		return i + 1;
	}

	/** Rappen are integers, so Rappen / 100 is the exact two-decimal amount as far as a double can hold it. */
	private static void amount(Row row, int column, long rappen, Styles s) {
		var cell = row.createCell(column);
		cell.setCellValue(rappen / 100.0);
		cell.setCellStyle(s.amount);
	}

	private static void date(Row row, int column, LocalDate value, Styles s) {
		var cell = row.createCell(column);
		cell.setCellValue(value);
		cell.setCellStyle(s.date);
	}

	private static final class Styles {

		final CellStyle header;

		final CellStyle amount;

		final CellStyle date;

		final CellStyle percent;

		Styles(XSSFWorkbook wb) {
			var format = wb.createDataFormat();
			Font bold = wb.createFont();
			bold.setBold(true);
			header = wb.createCellStyle();
			header.setFont(bold);
			amount = wb.createCellStyle();
			amount.setDataFormat(format.getFormat("#,##0.00"));
			date = wb.createCellStyle();
			date.setDataFormat(format.getFormat("dd.mm.yyyy"));
			percent = wb.createCellStyle();
			percent.setDataFormat(format.getFormat("0.0"));
		}

	}

}
