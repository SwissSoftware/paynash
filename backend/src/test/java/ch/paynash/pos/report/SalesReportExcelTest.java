package ch.paynash.pos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.jayway.jsonpath.JsonPath;

class SalesReportExcelTest extends ReportTestSupport {

	@Test
	void octoberExportHasSixSheetsWithUsableValues() throws Exception {
		byte[] file = mvc.perform(get("/admin/reports/sales.xlsx?from=2026-10-01&to=2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(boss)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"Rapport_2026-10-01_2026-10-31.xlsx\""))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		String json = mvc.perform(get("/admin/reports/sales?from=2026-10-01&to=2026-10-31")
			.header(HttpHeaders.AUTHORIZATION, bearer(boss))).andReturn().getResponse().getContentAsString();

		try (var wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			List<String> names = new ArrayList<>();
			wb.forEach(s -> names.add(s.getSheetName()));
			assertThat(names).containsExactly("Übersicht", "Tage", "Dienstleistungen", "Kunden", "Rechnungen",
					"Positionen");
			wb.forEach(s -> assertThat(s.getRow(0).getCell(0).getStringCellValue()).as(s.getSheetName()).isNotBlank());

			Sheet days = wb.getSheet("Tage");
			assertThat(days.getLastRowNum()).isEqualTo(31);
			assertThat(days.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(DateUtil.isCellDateFormatted(days.getRow(1).getCell(0))).isTrue();
			assertThat(days.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(days.getRow(1).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
			BigDecimal sumOfDays = BigDecimal.ZERO;
			for (int i = 1; i <= days.getLastRowNum(); i++) {
				sumOfDays = sumOfDays.add(BigDecimal.valueOf(days.getRow(i).getCell(1).getNumericCellValue()));
			}

			Sheet overview = wb.getSheet("Übersicht");
			Row revenue = find(overview, "Umsatz CHF");
			BigDecimal total = BigDecimal.valueOf(revenue.getCell(1).getNumericCellValue());
			assertThat(sumOfDays.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(total)
				.isEqualByComparingTo(new BigDecimal((String) JsonPath.read(json, "$.summary.revenue")))
				.isEqualByComparingTo("530.00");
			assertThat(find(overview, "Bezahlt mit TWINT CHF").getCell(1).getNumericCellValue()).isEqualTo(50.0);
			assertThat(find(overview, "MWST 8.1% Steuer CHF").getCell(1).getNumericCellValue()).isEqualTo(8.99);

			Sheet services = wb.getSheet("Dienstleistungen");
			assertThat(services.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Facial");
			assertThat(services.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(360.0);

			Sheet customers = wb.getSheet("Kunden");
			assertThat(customers.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Deleted customer");

			Sheet invoices = wb.getSheet("Rechnungen");
			assertThat(invoices.getLastRowNum()).isEqualTo(6);
			Row cancelled = find(invoices, "2026-0005");
			assertThat(cancelled.getCell(6).getStringCellValue()).isEqualTo("storniert");
			assertThat(cancelled.getCell(7)).isNull();
			Row paid = find(invoices, "2026-0004");
			assertThat(paid.getCell(6).getStringCellValue()).isEqualTo("bezahlt");
			assertThat(paid.getCell(7).getStringCellValue()).isEqualTo("TWINT");
			assertThat(DateUtil.isCellDateFormatted(paid.getCell(8))).isTrue();

			Sheet lines = wb.getSheet("Positionen");
			assertThat(lines.getLastRowNum()).isEqualTo(6);
			BigDecimal sumOfLines = BigDecimal.ZERO;
			for (int i = 1; i <= lines.getLastRowNum(); i++) {
				sumOfLines = sumOfLines.add(BigDecimal.valueOf(lines.getRow(i).getCell(9).getNumericCellValue()));
			}
			assertThat(sumOfLines.setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(total);
		}
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
