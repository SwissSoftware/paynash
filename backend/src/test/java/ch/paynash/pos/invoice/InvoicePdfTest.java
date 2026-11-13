package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpHeaders;

import ch.paynash.pos.settings.SettingsService;

class InvoicePdfTest extends InvoiceTestSupport {

	@Test
	void pdfIsA4AndContainsTheInvoice() throws Exception {
		settingsService.updateDetails(new SettingsService.DetailsInput("Kosmetik Aurora", "Bahnhofstrasse 1", "8001",
				"Zürich", "044 123 45 67", null, null, "CH93 0076 2011 6238 5295 7", "Aurora GmbH", true,
				"CHE-116.281.710 MWST"));
		long zoe = customer("Zoë", "Müller", "Seestrasse 7", "8002", "Zürich");
		long invoice = issued(confirm(zoe, json("menuItemId", facial, "qty", 1, "note", "empfindliche Haut"),
				line(tint, 2)));

		byte[] pdf = download(invoice, "Rechnung-2026-0001.pdf");
		var reader = new PdfReader(pdf);
		assertThat(reader.getPageSize(1).getWidth()).isEqualTo(PageSize.A4.getWidth());
		assertThat(reader.getPageSize(1).getHeight()).isEqualTo(PageSize.A4.getHeight());
		String text = new PdfTextExtractor(reader).getTextFromPage(1);
		assertThat(text).contains("Rechnung 2026-0001", "Kosmetik Aurora", "Zürich", "Zoë Müller", "Seestrasse 7",
				"Facial", "empfindliche Haut", "Eyebrow tint", "120.00", "50.00", "170.00", "Total CHF",
				"MWST-Nr. CHE-116.281.710 MWST", "8.1%", "12.74", "IBAN CH93 0076 2011 6238 5295 7")
			.doesNotContain("STORNIERT");
	}

	@Test
	void cancelledInvoiceIsMarkedStorniert() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		postJson(boss, "/admin/invoices/" + invoice + "/cancel", json("reason", "falsche Leistung"))
			.andExpect(status().isOk());
		String text = new PdfTextExtractor(new PdfReader(download(invoice, "Rechnung-2026-0001.pdf")))
			.getTextFromPage(1);
		assertThat(text).contains("STORNIERT", "falsche Leistung");
	}

	@Test
	void laterDownloadHasTheSameTextAfterTheBusinessMoved() throws Exception {
		long invoice = issued(confirm(laura, line(facial, 1)));
		String first = new PdfTextExtractor(new PdfReader(download(invoice, "Rechnung-2026-0001.pdf")))
			.getTextFromPage(1);

		settingsService.updateDetails(new SettingsService.DetailsInput("Kosmetik Aurora", "Marktgasse 9", "3011",
				"Bern", null, null, null, null, null, false, null));
		String second = new PdfTextExtractor(new PdfReader(download(invoice, "Rechnung-2026-0001.pdf")))
			.getTextFromPage(1);

		assertThat(second).isEqualTo(first).contains("Bahnhofstrasse 1").doesNotContain("Marktgasse");
	}

	private byte[] download(long invoice, String fileName) throws Exception {
		return as(staff, get("/invoices/" + invoice + "/pdf")).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\""))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
	}

}
