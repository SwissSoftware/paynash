package ch.paynash.pos.invoice;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.paynash.pos.common.Money;
import ch.paynash.pos.common.VatRates;
import ch.paynash.pos.printing.Ticket;

/**
 * The invoice as a receipt on 80 mm paper, in German (spec ticket-printing, "Receipt content"; add-salon-mode design
 * D8). Rendered only from the stored invoice, so a copy shows exactly the same content plus its marker.
 */
@Component
public class ReceiptRenderer {

	/** Characters per line at normal size on 80 mm paper. */
	static final int WIDTH = 48;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final ZoneId zone;

	public ReceiptRenderer(@Value("${pos.time-zone}") ZoneId zone) {
		this.zone = zone;
	}

	/** {@code copy} adds "KOPIE"; a cancelled invoice is always marked "STORNIERT". */
	public Ticket render(Invoice invoice, boolean copy) {
		var t = Ticket.builder();
		if (copy) {
			t.large("*** KOPIE ***");
		}
		if (invoice.status() == Invoice.Status.CANCELLED) {
			t.large("*** STORNIERT ***");
		}
		Invoice.Business b = invoice.business();
		t.large(b.name());
		t.normal(b.street());
		t.normal(b.postalCode() + " " + b.city());
		if (b.phone() != null) {
			t.normal("Tel. " + b.phone());
		}
		if (b.email() != null) {
			t.normal(b.email());
		}
		if (b.website() != null) {
			t.normal(b.website());
		}
		t.separator();
		t.large("Rechnung " + invoice.number());
		t.normal("Datum " + DATE.format(invoice.invoiceDate()));
		Invoice.CustomerCopy c = invoice.customer();
		t.normal("Kunde Nr. " + c.number());
		t.normal(c.name());
		if (c.street() != null) {
			t.normal(c.street());
		}
		if (c.postalCode() != null || c.city() != null) {
			t.normal(((c.postalCode() == null ? "" : c.postalCode()) + " " + (c.city() == null ? "" : c.city())).trim());
		}
		t.separator();
		for (Invoice.Line l : invoice.lines()) {
			t.normal(columns(l.qty() + " x " + l.name(), Money.format(l.amountRappen())));
			if (l.qty() > 1) {
				t.normal("    à " + Money.format(l.unitPriceRappen()));
			}
			if (l.note() != null) {
				t.normal("    > " + l.note());
			}
		}
		t.separator();
		t.large(columns("Total CHF", Money.format(invoice.totalRappen()), WIDTH / 2));
		if (invoice.vatEnabled()) {
			t.normal("");
			t.normal("MWST-Nr. " + invoice.vatUid());
			t.normal(table("Satz", "Netto", "MWST", "Brutto"));
			for (Vat.Row v : invoice.vat()) {
				t.normal(table(VatRates.format(v.ratePermille()) + "%", Money.format(v.netRappen()),
						Money.format(v.vatRappen()), Money.format(v.grossRappen())));
			}
		}
		if (invoice.status() == Invoice.Status.CANCELLED) {
			t.separator();
			t.normal("Storniert am " + DATE.format(invoice.cancelledAt().atZone(zone)));
			t.normal("Grund: " + invoice.cancelReason());
		}
		if (b.iban() != null) {
			t.separator();
			t.normal("IBAN " + groups(b.iban()));
			if (b.accountHolder() != null) {
				t.normal(b.accountHolder());
			}
		}
		t.normal("");
		t.normal("Vielen Dank!");
		return t.build();
	}

	/** {@code left} and {@code right} on one line, {@code left} shortened if needed. */
	static String columns(String left, String right) {
		return columns(left, right, WIDTH);
	}

	static String columns(String left, String right, int width) {
		int room = width - right.length() - 1;
		String l = left.length() > room ? left.substring(0, Math.max(0, room - 1)) + "." : left;
		return l + " ".repeat(width - l.length() - right.length()) + right;
	}

	private static String table(String rate, String net, String vat, String gross) {
		return pad(rate, 9, false) + pad(net, 13, true) + pad(vat, 13, true) + pad(gross, 13, true);
	}

	private static String pad(String s, int width, boolean right) {
		String p = " ".repeat(Math.max(0, width - s.length()));
		return right ? p + s : s + p;
	}

	private static String groups(String iban) {
		return iban.replaceAll("(.{4})(?!$)", "$1 ");
	}

}
