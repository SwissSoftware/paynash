package ch.paynash.pos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.jayway.jsonpath.JsonPath;

/**
 * Invoices without customer in the report and the Excel export (change invoices-without-customer, design D4; spec
 * sales-reporting, "Revenue per customer", scenario "Counter sales without customer").
 */
class ReportWithoutCustomerTest extends ReportTestSupport {

	@BeforeEach
	void counterSales() {
		invoiceWithoutCustomer(8, "2026-10-10", item("Croissant", 50, 280), item("Espresso", 10, 450));
		invoiceWithoutCustomer(9, "2026-10-11", item("Lunch", 10, 1550));
	}

	@Test
	void counterSalesAreOneEntryWithoutNumberAndCountInTheTotal() throws Exception {
		mvc.perform(get("/admin/reports/sales?from=2026-10-01&to=2026-10-31").header(HttpHeaders.AUTHORIZATION,
				bearer(boss)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.customers[*].name",
					contains("Ohne Kunde", "Peter Meili", "Laura Meier", "Deleted customer")))
			.andExpect(jsonPath("$.customers[0].number").value(nullValue()))
			.andExpect(jsonPath("$.customers[0].invoiceCount").value(2))
			.andExpect(jsonPath("$.customers[0].revenue").value("340.00"))
			.andExpect(jsonPath("$.summary.revenue").value("870.00"))
			.andExpect(jsonPath("$.summary.invoiceCount").value(7));
	}

	@Test
	void excelContainsTheInvoicesWithoutCustomer() throws Exception {
		byte[] file = mvc.perform(get("/admin/reports/sales.xlsx?from=2026-10-01&to=2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(boss)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		String json = mvc.perform(get("/admin/reports/sales?from=2026-10-01&to=2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(boss))).andReturn().getResponse().getContentAsString();
		BigDecimal total = new BigDecimal((String) JsonPath.read(json, "$.summary.revenue"));

		try (var wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet customers = wb.getSheet("Kunden");
			assertThat(customers.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Ohne Kunde");
			assertThat(blank(customers.getRow(1), 0)).isTrue();

			Sheet invoices = wb.getSheet("Rechnungen");
			Row counter = find(invoices, "2026-0008");
			assertThat(blank(counter, 2)).isTrue();
			assertThat(blank(counter, 3)).isTrue();

			Sheet lines = wb.getSheet("Positionen");
			assertThat(lines.getLastRowNum()).isEqualTo(9);
			BigDecimal sum = BigDecimal.ZERO;
			for (int i = 1; i <= lines.getLastRowNum(); i++) {
				sum = sum.add(BigDecimal.valueOf(lines.getRow(i).getCell(9).getNumericCellValue()));
			}
			assertThat(sum.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(total);
		}
	}

	private static boolean blank(Row row, int column) {
		var cell = row.getCell(column);
		return cell == null || cell.getCellType() == CellType.BLANK;
	}

	private static Row find(Sheet sheet, String firstCell) {
		for (Row row : sheet) {
			if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING
					&& row.getCell(0).getStringCellValue().equals(firstCell)) {
				return row;
			}
		}
		throw new AssertionError(firstCell + " not found on " + sheet.getSheetName());
	}

}
