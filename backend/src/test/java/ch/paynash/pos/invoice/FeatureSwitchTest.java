package ch.paynash.pos.invoice;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.settings.Feature;
import ch.paynash.pos.settings.Template;

/**
 * Features that follow invoices, customers, and the report, and data kept when features are switched off (change
 * configurable-business-features, spec business-features "Switching features with existing data", specs invoicing,
 * customer-management, sales-reporting).
 */
class FeatureSwitchTest extends InvoiceTestSupport {

	@Test
	void reportIsRejectedWhileSwitchedOff() throws Exception {
		settingsService.updateFeatures(EnumSet.of(Feature.CUSTOMERS, Feature.INVOICES));
		as(boss, get("/admin/reports/sales")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("feature_disabled"))
			.andExpect(jsonPath("$.feature").value("sales-report"))
			.andExpect(jsonPath("$.detail").value("The sales report is switched off"));
	}

	@Test
	void customersAreRejectedWhileSwitchedOff() throws Exception {
		settingsService.applyTemplate(Template.EVENT);
		as(staff, get("/customers")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Customers are switched off"));
	}

	/** Spec invoicing, "Issue an invoice", scenario "Order without customer" (change invoices-without-customer). */
	@Test
	void tableOrderWithoutCustomerIsInvoicedWithoutRecipient() throws Exception {
		settingsService.applyTemplate(Template.RESTAURANT);
		settingsService.updateDetails(details(false, null));
		long order = id(postJson(staff, "/orders", "{\"tableLabel\":\"12\",\"lines\":[" + line(tint, 1) + "]}")
			.andExpect(status().isCreated()));
		issue(staff, order).andExpect(status().isCreated())
			.andExpect(jsonPath("$.total").value("25.00"))
			.andExpect(jsonPath("$.customer").doesNotExist());
	}

	@Test
	void restaurantInvoicesATableOrderWithACustomer() throws Exception {
		settingsService.applyTemplate(Template.RESTAURANT);
		settingsService.updateDetails(details(false, null));
		long order = id(postJson(staff, "/orders",
				"{\"tableLabel\":\"12\",\"customerId\":" + laura + ",\"lines\":[" + line(facial, 1) + "]}")
			.andExpect(status().isCreated()));
		issue(staff, order).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value("120.00"));
	}

	@Test
	void historyKeepsTablesAndCustomersAfterSwitchingThemOff() throws Exception {
		settingsService.applyTemplate(Template.RESTAURANT);
		postJson(staff, "/orders",
				"{\"tableLabel\":\"12\",\"customerId\":" + laura + ",\"lines\":[" + line(facial, 1) + "]}")
			.andExpect(status().isCreated());

		settingsService.applyTemplate(Template.COUNTER);

		as(staff, get("/orders")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].tableLabel", contains("12")))
			.andExpect(jsonPath("$[*].customerName", contains("Laura Meier")));
	}

	@Test
	void invoicesComeBackWithGaplessNumbersAfterSwitchingOffAndOn() throws Exception {
		long first = issued(confirm(laura, line(facial, 1)));
		String firstNumber = string(as(staff, get("/invoices/" + first)), "$.number");

		settingsService.applyTemplate(Template.EVENT);
		as(staff, get("/invoices")).andExpect(status().isConflict());

		settingsService.applyTemplate(Template.SALON);
		as(staff, get("/invoices")).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].number", contains(firstNumber)));
		long second = issued(confirm(laura, line(tint, 1)));
		String secondNumber = string(as(staff, get("/invoices/" + second)), "$.number");
		org.assertj.core.api.Assertions.assertThat(seq(secondNumber)).isEqualTo(seq(firstNumber) + 1);
	}

	private static int seq(String number) {
		return Integer.parseInt(number.substring(number.indexOf('-') + 1));
	}

}
