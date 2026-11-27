package ch.paynash.pos.invoice;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.settings.Feature;
import ch.paynash.pos.settings.Template;

/**
 * Invoices for counter sales without customer and the CHF 400.00 limit (change invoices-without-customer; spec
 * invoicing, "Small-amount limit without customer").
 */
class InvoiceWithoutCustomerTest extends InvoiceTestSupport {

	@Autowired
	InvoiceRepository invoiceRepository;

	private long exactly400;

	private long above400;

	@BeforeEach
	void counterWithInvoices() {
		settingsService.applyTemplate(Template.COUNTER);
		settingsService.updateFeatures(EnumSet.of(Feature.INVOICES));
		long big = menu.insertCategory("Packages");
		exactly400 = menu.insertItem(big, "Day package", ItemType.SERVICE, 40_000, null, true, null, 81);
		above400 = menu.insertItem(big, "Day package plus", ItemType.SERVICE, 40_005, null, true, null, 81);
	}

	private long counterSale(long itemId) throws Exception {
		return id(postJson(staff, "/orders", "{\"lines\":[" + line(itemId, 1) + "]}").andExpect(status().isCreated()));
	}

	@Test
	void counterSaleIsInvoicedWithoutRecipient() throws Exception {
		long order = counterSale(tint);
		long id = id(issue(staff, order).andExpect(status().isCreated())
			.andExpect(jsonPath("$.total").value("25.00"))
			.andExpect(jsonPath("$.customer").doesNotExist()));
		Invoice invoice = invoiceRepository.find(id).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(invoice.customerId()).isNull();
		org.assertj.core.api.Assertions.assertThat(invoice.hasCustomer()).isFalse();
	}

	@Test
	void exactlyAtTheLimitIsIssued() throws Exception {
		issue(staff, counterSale(exactly400)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.total").value("400.00"));
	}

	@Test
	void aboveTheLimitIsRejectedAndUsesNoNumber() throws Exception {
		issue(staff, counterSale(above400)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("customer_required"))
			.andExpect(jsonPath("$.detail").value("Invoices above CHF 400.00 need a customer"));
		String number = string(issue(staff, counterSale(tint)).andExpect(status().isCreated()), "$.number");
		org.assertj.core.api.Assertions.assertThat(number).endsWith("-0001");
	}

	@Test
	void largeInvoiceWithCustomerIsIssued() throws Exception {
		settingsService.applyTemplate(Template.SALON);
		long big = menu.insertItem(menu.insertCategory("Events"), "Company event", ItemType.SERVICE, 125_000, null,
				true, null, 81);
		issue(staff, confirm(laura, line(big, 1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.total").value("1250.00"))
			.andExpect(jsonPath("$.customer.name").value("Laura Meier"));
	}

	/** Design D5: "Invoices are final" and the invoice in the history apply to orders without customer as well. */
	@Test
	void invoicedTableOrderWithoutCustomerIsFinalAndShowsItsInvoice() throws Exception {
		settingsService.applyTemplate(Template.RESTAURANT);
		long order = id(postJson(staff, "/orders", "{\"tableLabel\":\"7\",\"lines\":[" + line(tint, 2) + "]}")
			.andExpect(status().isCreated()));
		String number = string(issue(staff, order).andExpect(status().isCreated()), "$.number");
		long lineId = ((Number) com.jayway.jsonpath.JsonPath
			.read(as(staff, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/" + order))
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.lines[0].id")).longValue();

		postJson(boss, "/admin/orders/" + order + "/lines/" + lineId + "/cancel", json("reason", "spilled"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The order has invoice " + number + ", cancel the invoice first"));
		postJson(boss, "/admin/orders/" + order + "/void", json("reason", "test", "version", 0))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The order has invoice " + number + ", cancel the invoice first"));
		as(staff, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders"))
			.andExpect(jsonPath("$[0].invoice.number").value(number));
		as(staff, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/" + order))
			.andExpect(jsonPath("$.invoice.number").value(number));
	}

}
