package ch.paynash.pos.invoice;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

class InvoiceVatTest extends InvoiceTestSupport {

	@Test
	void standardRateGivesVat810OnGross10810() throws Exception {
		settingsService.updateDetails(details(true, "CHE-116.281.710 MWST"));
		long peel = menu.insertItem(menu.insertCategory("Body"), "Peeling", ItemType.SERVICE, 10810, null, true, null,
				81);
		issue(staff, confirm(laura, line(peel, 1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.vatEnabled").value(true))
			.andExpect(jsonPath("$.vatUid").value("CHE-116.281.710 MWST"))
			.andExpect(jsonPath("$.vat", hasSize(1)))
			.andExpect(jsonPath("$.vat[0].rate").value("8.1"))
			.andExpect(jsonPath("$.vat[0].gross").value("108.10"))
			.andExpect(jsonPath("$.vat[0].vat").value("8.10"))
			.andExpect(jsonPath("$.vat[0].net").value("100.00"));
	}

	@Test
	void mixedRatesGetOneRowEach() throws Exception {
		settingsService.updateDetails(details(true, "CHE-116.281.710 MWST"));
		long voucher = menu.insertItem(menu.insertCategory("Other"), "Voucher", ItemType.SERVICE, 5000, null, true,
				null, 0);
		issue(staff, confirm(laura, line(facial, 1), line(voucher, 1))).andExpect(jsonPath("$.total").value("170.00"))
			.andExpect(jsonPath("$.vat[*].rate", contains("8.1", "0.0")))
			.andExpect(jsonPath("$.vat[0].vat").value("8.99"))
			.andExpect(jsonPath("$.vat[1].vat").value("0.00"))
			.andExpect(jsonPath("$.lines[1].vatRate").value("0.0"));
	}

	@Test
	void vatOffGivesNoVatRows() throws Exception {
		issue(staff, confirm(laura, line(facial, 1))).andExpect(jsonPath("$.vatEnabled").value(false))
			.andExpect(jsonPath("$.vat", hasSize(0)))
			.andExpect(jsonPath("$.vatUid").doesNotExist());
	}

}
