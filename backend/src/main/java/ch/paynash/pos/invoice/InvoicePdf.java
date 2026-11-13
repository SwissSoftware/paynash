package ch.paynash.pos.invoice;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.Utilities;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.paynash.pos.common.Money;
import ch.paynash.pos.common.VatRates;

/**
 * The invoice as an A4 PDF in German (spec invoicing, "A4 invoice as PDF"; add-salon-mode design D9). Created on
 * demand from the stored invoice only, so it can be produced again identically at any time. The customer address sits
 * in the right window of a C5 envelope (Swiss standard: 118 mm from the left, 50 mm from the top).
 */
@Component
public class InvoicePdf {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private static final float MARGIN = Utilities.millimetersToPoints(20);

	/** Where the content of the first page starts, below the envelope window. */
	private static final float BODY_TOP = Utilities.millimetersToPoints(95);

	private static final Color GREY = new Color(0x60, 0x60, 0x60);

	private static final Color RED = new Color(0xB0, 0x00, 0x20);

	private final ZoneId zone;

	public InvoicePdf(@Value("${pos.time-zone}") ZoneId zone) {
		this.zone = zone;
	}

	public static String fileName(Invoice invoice) {
		return "Rechnung-" + invoice.number() + ".pdf";
	}

	public byte[] render(Invoice invoice) {
		var out = new ByteArrayOutputStream();
		// The first page starts its content below the address window; later pages use the normal margin.
		var doc = new Document(PageSize.A4, MARGIN, MARGIN, BODY_TOP, MARGIN);
		PdfWriter writer = PdfWriter.getInstance(doc, out);
		doc.addTitle("Rechnung " + invoice.number());
		doc.addAuthor(invoice.business().name());
		doc.open();
		doc.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

		Font normal = font(false, 10, null);
		Font small = font(false, 8, GREY);
		Font bold = font(true, 10, null);

		// Sender block, top left.
		Invoice.Business b = invoice.business();
		var sender = new Phrase();
		sender.add(new Phrase(b.name() + "\n", font(true, 12, null)));
		sender.add(new Phrase(b.street() + "\n" + b.postalCode() + " " + b.city() + "\n", normal));
		for (String contact : contacts(b)) {
			sender.add(new Phrase(contact + "\n", small));
		}
		place(writer, sender, MARGIN, 20, 95, 40);

		// Recipient in the envelope window.
		Invoice.CustomerCopy c = invoice.customer();
		var recipient = new Phrase();
		recipient.add(new Phrase(c.name() + "\n", normal));
		if (c.street() != null) {
			recipient.add(new Phrase(c.street() + "\n", normal));
		}
		String place = ((c.postalCode() == null ? "" : c.postalCode()) + " " + (c.city() == null ? "" : c.city()))
			.trim();
		if (!place.isEmpty()) {
			recipient.add(new Phrase(place + "\n", normal));
		}
		place(writer, recipient, Utilities.millimetersToPoints(118), 50, 72, 30);

		doc.add(new Paragraph("Rechnung " + invoice.number(), font(true, 16, null)));
		doc.add(new Paragraph("Datum: " + DATE.format(invoice.invoiceDate()) + "     Kunde Nr. " + c.number(),
				normal));
		if (invoice.status() == Invoice.Status.CANCELLED) {
			var cancelled = new Paragraph("STORNIERT am " + DATE.format(invoice.cancelledAt().atZone(zone))
					+ " – Grund: " + invoice.cancelReason(), font(true, 12, RED));
			cancelled.setSpacingBefore(6);
			doc.add(cancelled);
		}

		doc.add(lines(invoice, normal, small, bold));
		if (invoice.vatEnabled()) {
			doc.add(vat(invoice, normal, bold));
		}

		var footer = new Paragraph();
		footer.setSpacingBefore(18);
		if (b.iban() != null) {
			footer.add(new Phrase("Bankverbindung: IBAN " + b.iban().replaceAll("(.{4})(?!$)", "$1 ")
					+ (b.accountHolder() != null ? ", " + b.accountHolder() : "") + "\n", normal));
		}
		footer.add(new Phrase("Vielen Dank für Ihren Besuch.", normal));
		doc.add(footer);

		doc.close();
		return out.toByteArray();
	}

	private static PdfPTable lines(Invoice invoice, Font normal, Font small, Font bold) {
		boolean vat = invoice.vatEnabled();
		var table = new PdfPTable(vat ? new float[] { 8, 52, 14, 10, 16 } : new float[] { 8, 62, 14, 16 });
		table.setWidthPercentage(100);
		table.setSpacingBefore(14);
		table.setHeaderRows(1);
		header(table, bold, "Menge", "Dienstleistung", "Preis", vat ? "MWST" : null, "Betrag");
		for (Invoice.Line l : invoice.lines()) {
			table.addCell(cell(String.valueOf(l.qty()), normal, Element.ALIGN_RIGHT, false));
			var name = new Phrase(l.name(), normal);
			if (l.note() != null) {
				name.add(new Phrase("\n" + l.note(), small));
			}
			var nameCell = new PdfPCell(name);
			style(nameCell, Element.ALIGN_LEFT, false);
			table.addCell(nameCell);
			table.addCell(cell(Money.format(l.unitPriceRappen()), normal, Element.ALIGN_RIGHT, false));
			if (vat) {
				table.addCell(cell(VatRates.format(l.vatRatePermille()) + "%", normal, Element.ALIGN_RIGHT, false));
			}
			table.addCell(cell(Money.format(l.amountRappen()), normal, Element.ALIGN_RIGHT, false));
		}
		var label = cell("Total CHF", bold, Element.ALIGN_LEFT, true);
		label.setColspan(vat ? 4 : 3);
		table.addCell(label);
		table.addCell(cell(Money.format(invoice.totalRappen()), bold, Element.ALIGN_RIGHT, true));
		return table;
	}

	private static PdfPTable vat(Invoice invoice, Font normal, Font bold) {
		var table = new PdfPTable(new float[] { 25, 25, 25, 25 });
		table.setWidthPercentage(60);
		table.setHorizontalAlignment(Element.ALIGN_RIGHT);
		table.setSpacingBefore(12);
		var uid = cell("MWST-Nr. " + invoice.vatUid(), normal, Element.ALIGN_LEFT, false);
		uid.setColspan(4);
		table.addCell(uid);
		for (String title : new String[] { "Satz", "Netto", "MWST", "Brutto" }) {
			table.addCell(cell(title, bold, Element.ALIGN_RIGHT, true));
		}
		for (Vat.Row r : invoice.vat()) {
			table.addCell(cell(VatRates.format(r.ratePermille()) + "%", normal, Element.ALIGN_RIGHT, false));
			table.addCell(cell(Money.format(r.netRappen()), normal, Element.ALIGN_RIGHT, false));
			table.addCell(cell(Money.format(r.vatRappen()), normal, Element.ALIGN_RIGHT, false));
			table.addCell(cell(Money.format(r.grossRappen()), normal, Element.ALIGN_RIGHT, false));
		}
		return table;
	}

	private static void header(PdfPTable table, Font bold, String... titles) {
		for (int i = 0; i < titles.length; i++) {
			if (titles[i] != null) {
				table.addCell(cell(titles[i], bold, i == 1 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, true));
			}
		}
	}

	private static PdfPCell cell(String text, Font font, int align, boolean line) {
		var cell = new PdfPCell(new Phrase(text, font));
		style(cell, align, line);
		return cell;
	}

	private static void style(PdfPCell cell, int align, boolean line) {
		cell.setHorizontalAlignment(align);
		cell.setBorder(line ? Rectangle.TOP | Rectangle.BOTTOM : Rectangle.NO_BORDER);
		cell.setPaddingTop(3);
		cell.setPaddingBottom(4);
	}

	private static List<String> contacts(Invoice.Business b) {
		var list = new ArrayList<String>();
		if (b.phone() != null) {
			list.add("Tel. " + b.phone());
		}
		if (b.email() != null) {
			list.add(b.email());
		}
		if (b.website() != null) {
			list.add(b.website());
		}
		return list;
	}

	/** Places text in a box {@code topMm} from the top of the page, {@code widthMm} wide and {@code heightMm} high. */
	private static void place(PdfWriter writer, Phrase text, float x, float topMm, float widthMm, float heightMm) {
		float top = PageSize.A4.getHeight() - Utilities.millimetersToPoints(topMm);
		var column = new ColumnText(writer.getDirectContent());
		column.setSimpleColumn(text, x, top - Utilities.millimetersToPoints(heightMm),
				x + Utilities.millimetersToPoints(widthMm), top, 13, Element.ALIGN_LEFT);
		column.go();
	}

	private static Font font(boolean bold, float size, Color color) {
		var font = new Font(bold ? PdfFonts.bold() : PdfFonts.regular(), size);
		if (color != null) {
			font.setColor(color);
		}
		return font;
	}

}
