package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.support.FakeEposPrinter;

/** Spec ticket-printing, "Printer configuration". */
class PrinterAdminTest extends PrintingTestSupport {

	private String admin;

	@BeforeEach
	void login() throws Exception {
		admin = adminToken();
	}

	@Test
	void configureBarPrinterRoutesDrinksToIt() throws Exception {
		create("Bar", "192.168.1.50", "drink", true).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Bar"))
			.andExpect(jsonPath("$.address").value("192.168.1.50"))
			.andExpect(jsonPath("$.deviceId").value("local_printer"))
			.andExpect(jsonPath("$.type").value("drink"))
			.andExpect(jsonPath("$.active").value(true));
		long cola = item("Drinks", "Cola", ItemType.DRINK);

		long id = sendOrder(waiterToken(), "12", line(cola, 1));

		assertThat(job(id, ItemType.DRINK).printerName()).isEqualTo("Bar");
	}

	@Test
	void onlyOneActivePrinterPerType() throws Exception {
		create("Bar", "192.168.1.50", "drink", true).andExpect(status().isCreated());

		create("Bar 2", "192.168.1.51", "drink", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.type").value("\"Bar\" already prints drink. Deactivate it first."));

		long spare = id(create("Bar 2", "192.168.1.51", "drink", false).andExpect(status().isCreated()));
		update(spare, "Bar 2", "192.168.1.51", "drink", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.type").exists());
		update(spare, "Bar 2", "192.168.1.51", "food", true).andExpect(status().isOk());
		assertThatThrownBy(() -> printerRepository.insert("Bar 3", "x", "local_printer", ItemType.DRINK, true))
			.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void switchingPrintersByDeactivatingTheOldOneFirst() throws Exception {
		long bar = id(create("Bar", "192.168.1.50", "drink", true));
		long spare = id(create("Spare", "192.168.1.51", "drink", false));

		update(bar, "Bar", "192.168.1.50", "drink", false).andExpect(status().isOk());
		update(spare, "Spare", "192.168.1.51", "drink", true).andExpect(status().isOk());

		assertThat(printerRepository.findActiveFor(ItemType.DRINK)).hasValueSatisfying(
				p -> assertThat(p.name()).isEqualTo("Spare"));
	}

	@Test
	void invalidInputIsRejectedPerField() throws Exception {
		postJson(admin, "/admin/printers", json("name", "", "address", "http://bar/../x?y", "type", "beer"))
			.andExpect(status().isBadRequest());
		postJson(admin, "/admin/printers", json("name", "", "address", "a b")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.address").value("Enter an IP address or host name, optionally with port"))
			.andExpect(jsonPath("$.errors.type").value("Type is required (food or drink)"));
		create("Bar", "bar.local:8008", "drink", true).andExpect(status().isCreated());
		create("BAR", "192.168.1.9", "food", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").value("A printer with this name already exists"));
	}

	@Test
	void listShowsAllPrinters() throws Exception {
		create("Kitchen", "192.168.1.51", "food", true);
		create("Bar", "192.168.1.50", "drink", false);

		as(admin, get("/admin/printers")).andExpect(jsonPath("$[*].name").value(
				org.hamcrest.Matchers.contains("Bar", "Kitchen")));
	}

	@Test
	void testPrintReportsSuccessAndFailure() throws Exception {
		var fake = fakePrinter();
		long ok = id(create("Kitchen", fake.address(), "food", true));
		long offline = id(create("Bar", OFFLINE, "drink", true));

		as(admin, post("/admin/printers/" + ok + "/test")).andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.error").doesNotExist());
		as(admin, post("/admin/printers/" + offline + "/test")).andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error").value("printer offline"));

		assertThat(fake.requests).singleElement().satisfies(r -> assertThat(r).contains("Test print")
			.contains("Umlauts: ä ö ü"));
		fake.answer(FakeEposPrinter.response(false, "EPTR_COVER_OPEN"));
		as(admin, post("/admin/printers/" + ok + "/test")).andExpect(jsonPath("$.error").value("cover open"));
	}

	@Test
	void waitersCannotConfigurePrinters() throws Exception {
		String waiter = waiterToken();
		as(waiter, get("/admin/printers")).andExpect(status().isForbidden());
		postJson(waiter, "/admin/printers", json("name", "Bar", "address", "1.2.3.4", "type", "drink"))
			.andExpect(status().isForbidden());
		assertThat(printerRepository.findAll()).isEmpty();
	}

	private ResultActions create(String name, String address, String type, boolean active) throws Exception {
		return postJson(admin, "/admin/printers",
				json("name", name, "address", address, "type", type, "active", active));
	}

	private ResultActions update(long id, String name, String address, String type, boolean active)
			throws Exception {
		return as(admin, put("/admin/printers/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(json("name", name, "address", address, "type", type, "active", active)));
	}

	private static long id(ResultActions created) throws Exception {
		return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
