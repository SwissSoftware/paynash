package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReceiptRenderingTest {

	private final ReceiptRenderer renderer = new ReceiptRenderer(ZoneId.of("Europe/Zurich"));

	private static final Invoice.Business BUSINESS = new Invoice.Business("Kosmetik Aurora", "Bahnhofstrasse 1",
			"3000", "Bern", "031 123 45 67", null, null, "CH9300762011623852957", "Aurora GmbH");

	private static final Invoice.CustomerCopy LAURA = new Invoice.CustomerCopy(42, "Laura Meier", "Bahnhofstrasse 5",
			"3000", "Bern");

	@Test
	void receiptWithoutVat() {
		assertThat(renderer.render(invoice(false, Invoice.Status.OPEN), false).encode()).isEqualTo("""
				# Kosmetik Aurora
				  Bahnhofstrasse 1
				  3000 Bern
				  Tel. 031 123 45 67
				  ------------------------------------------------
				# Rechnung 2026-0042
				  Datum 13.11.2026
				  Kunde Nr. 42
				  Laura Meier
				  Bahnhofstrasse 5
				  3000 Bern
				  ------------------------------------------------
				  1 x Facial                                120.00
				      > sensitive skin
				  2 x Eyebrow tint                           50.00
				      à 25.00
				  ------------------------------------------------
				# Total CHF         170.00
				  ------------------------------------------------
				  IBAN CH93 0076 2011 6238 5295 7
				  Aurora GmbH
				 \s
				  Vielen Dank!""");
	}

	@Test
	void receiptWithVatShowsUidAndRates() {
		String text = renderer.render(invoice(true, Invoice.Status.OPEN), false).encode();
		assertThat(text).contains("""
				# Total CHF         170.00
				 \s
				  MWST-Nr. CHE-116.281.710 MWST
				  Satz             Netto         MWST       Brutto
				  8.1%            157.26        12.74       170.00
				""");
	}

	@Test
	void copyIsMarkedKopie() {
		String text = renderer.render(invoice(false, Invoice.Status.PAID), true).encode();
		assertThat(text).startsWith("# *** KOPIE ***\n# Kosmetik Aurora\n");
	}

	@Test
	void cancelledReceiptIsMarkedStorniertWithTheReason() {
		String text = renderer.render(invoice(false, Invoice.Status.CANCELLED), true).encode();
		assertThat(text).startsWith("# *** KOPIE ***\n# *** STORNIERT ***\n# Kosmetik Aurora\n");
		assertThat(text).contains("  Storniert am 14.11.2026\n  Grund: wrong service\n");
	}

	@Test
	void longNamesAreShortenedToKeepTheAmount() {
		assertThat(ReceiptRenderer.columns("1 x " + "Very long treatment name ".repeat(3), "120.00"))
			.hasSize(48)
			.endsWith(". 120.00");
	}

	private static Invoice invoice(boolean vat, Invoice.Status status) {
		var lines = List.of(new Invoice.Line(1, "Facial", "Face", 1, 12000, 81, 12000, "sensitive skin"),
				new Invoice.Line(2, "Eyebrow tint", "Face", 2, 2500, 81, 5000, null));
		var vatRows = vat ? List.of(new Vat.Row(81, 17000, 15726, 1274)) : List.<Vat.Row>of();
		boolean cancelled = status == Invoice.Status.CANCELLED;
		return new Invoice(1, "2026-0042", 7, 3, LocalDate.of(2026, 11, 13), Instant.parse("2026-11-13T10:00:00Z"),
				"Anna", BUSINESS, LAURA, vat, vat ? "CHE-116.281.710 MWST" : null, 17000, status,
				cancelled ? Instant.parse("2026-11-13T23:30:00Z") : null, cancelled ? "Boss" : null,
				cancelled ? "wrong service" : null, lines, vatRows, List.of());
	}

}
