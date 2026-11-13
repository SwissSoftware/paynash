package ch.paynash.pos.report;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class SalesReportTest extends ReportTestSupport {

	@Test
	void summaryOfOctober() throws Exception {
		report(boss, "?from=2026-10-01&to=2026-10-31").andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2026-10-01"))
			.andExpect(jsonPath("$.to").value("2026-10-31"))
			.andExpect(jsonPath("$.summary.revenue").value("530.00"))
			.andExpect(jsonPath("$.summary.invoiceCount").value(5))
			.andExpect(jsonPath("$.summary.paid").value("290.00"))
			.andExpect(jsonPath("$.summary.open").value("240.00"))
			.andExpect(jsonPath("$.summary.byMethod[*].method", contains("cash", "card", "twint", "bank_transfer")))
			.andExpect(jsonPath("$.summary.byMethod[*].amount", contains("120.00", "120.00", "50.00", "0.00")))
			.andExpect(jsonPath("$.summary.vat", hasSize(1)))
			.andExpect(jsonPath("$.summary.vat[0].rate").value("8.1"))
			.andExpect(jsonPath("$.summary.vat[0].gross").value("120.00"))
			.andExpect(jsonPath("$.summary.vat[0].net").value("111.01"))
			.andExpect(jsonPath("$.summary.vat[0].vat").value("8.99"));
	}

	@Test
	void everyDayIsListedAndCountsOnTheInvoiceDate() throws Exception {
		report(boss, "?from=2026-10-01&to=2026-10-31").andExpect(jsonPath("$.days", hasSize(31)))
			// The order of 2026-0001 was confirmed on 30 September; it counts on its invoice date, 1 October.
			.andExpect(jsonPath("$.days[0].date").value("2026-10-01"))
			.andExpect(jsonPath("$.days[0].revenue").value("120.00"))
			.andExpect(jsonPath("$.days[1].revenue").value("300.00"))
			.andExpect(jsonPath("$.days[1].invoiceCount").value(2))
			.andExpect(jsonPath("$.days[3].date").value("2026-10-04"))
			.andExpect(jsonPath("$.days[3].revenue").value("0.00"))
			.andExpect(jsonPath("$.days[3].invoiceCount").value(0))
			// 6 October only has the cancelled invoice.
			.andExpect(jsonPath("$.days[5].revenue").value("0.00"))
			.andExpect(jsonPath("$.days[30].revenue").value("60.00"));
	}

	@Test
	void servicesByRevenueHighestFirst() throws Exception {
		report(boss, "?from=2026-10-01&to=2026-10-31")
			.andExpect(jsonPath("$.services[*].name", contains("Facial", "Manicure", "Eyebrow tint")))
			.andExpect(jsonPath("$.services[*].quantity", contains(3, 2, 2)))
			.andExpect(jsonPath("$.services[*].revenue", contains("360.00", "120.00", "50.00")));
	}

	@Test
	void customersByRevenueWithDeletedCustomer() throws Exception {
		report(boss, "?from=2026-10-01&to=2026-10-31")
			.andExpect(jsonPath("$.customers[*].name", contains("Peter Meili", "Laura Meier", "Deleted customer")))
			.andExpect(jsonPath("$.customers[*].number", contains(peter, laura, sara)))
			.andExpect(jsonPath("$.customers[*].invoiceCount", contains(2, 2, 1)))
			.andExpect(jsonPath("$.customers[*].revenue", contains("300.00", "170.00", "60.00")));
	}

	@Test
	void defaultsToTheCurrentMonth() throws Exception {
		clock.set("2026-10-15T10:00:00Z");
		boss = login("boss", "1111");
		report(boss, "").andExpect(jsonPath("$.from").value("2026-10-01"))
			.andExpect(jsonPath("$.to").value("2026-10-31"))
			.andExpect(jsonPath("$.summary.revenue").value("530.00"));
	}

	@Test
	void rangeOfMoreThan366DaysIsRejected() throws Exception {
		report(boss, "?from=2025-01-01&to=2026-02-04").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.to").value("The period must be at most 366 days"));
		report(boss, "?from=2026-10-31&to=2026-10-01").andExpect(status().isBadRequest());
	}

	@Test
	void staffCannotSeeTheReportOrExport() throws Exception {
		report(staff, "").andExpect(status().isForbidden());
		mvc.perform(get("/admin/reports/sales.xlsx").header(HttpHeaders.AUTHORIZATION, bearer(staff)))
			.andExpect(status().isForbidden());
	}

	private ResultActions report(String token, String query) throws Exception {
		return mvc.perform(get("/admin/reports/sales" + query).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

}
