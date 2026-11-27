package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.support.FakeEposPrinter;

/** Spec ticket-printing, "Printer configuration". */
class PrinterAdminTest extends PrintingTestSupport {

	private String admin;

	@BeforeEach
	void login() throws Exception {
		admin = adminToken();
	}

	@Test
	void configureBarPrinterRoutesItsCategoriesToIt() throws Exception {
		long cola = item("Soft drinks", "Cola", ItemType.DRINK);
		long beer = item("Beer", "Lager", ItemType.DRINK);
		create("Bar", "192.168.1.50", true, false, true, category("Beer"), category("Soft drinks"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Bar"))
			.andExpect(jsonPath("$.address").value("192.168.1.50"))
			.andExpect(jsonPath("$.deviceId").value("local_printer"))
			.andExpect(jsonPath("$.printsTickets").value(true))
			.andExpect(jsonPath("$.printsReceipts").value(false))
			.andExpect(jsonPath("$.categories[*].name").value(
					org.hamcrest.Matchers.containsInAnyOrder("Soft drinks", "Beer")))
			.andExpect(jsonPath("$.active").value(true));

		long id = sendOrder(waiterToken(), "12", line(cola, 1), line(beer, 2));

		assertThat(jobs(id)).singleElement().satisfies(j -> assertThat(j.printerName()).isEqualTo("Bar"));
	}

	@Test
	void categoryOnTwoPrintersAndTwoReceiptPrintersAreAccepted() throws Exception {
		item("Pizza", "Margherita", ItemType.FOOD);
		create("Kitchen", "192.168.1.51", true, false, true, category("Pizza")).andExpect(status().isCreated());
		create("Pass", "192.168.1.52", true, false, true, category("Pizza")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.categories[0].name").value("Pizza"));
		create("Counter", "192.168.1.60", false, true, true).andExpect(status().isCreated());
		create("Office", "192.168.1.61", false, true, true).andExpect(status().isCreated());

		assertThat(printerRepository.receiptPrinters()).extracting(Printer::name).containsExactly("Counter", "Office");
	}

	@Test
	void ticketsOffKeepsTheCategories() throws Exception {
		item("Pizza", "Margherita", ItemType.FOOD);
		long kitchen = id(create("Kitchen", "192.168.1.51", true, false, true, category("Pizza")));

		update(kitchen, "Kitchen", "192.168.1.51", false, false, true, category("Pizza")).andExpect(status().isOk())
			.andExpect(jsonPath("$.printsTickets").value(false))
			.andExpect(jsonPath("$.categories[0].name").value("Pizza"));
		as(admin, get("/admin/printers")).andExpect(jsonPath("$.unprintedCategories[*].name").value(
				org.hamcrest.Matchers.contains("Pizza")));
	}

	@Test
	void invalidInputIsRejectedPerField() throws Exception {
		postJson(admin, "/admin/printers", json("name", "", "address", "http://bar/../x?y"))
			.andExpect(status().isBadRequest());
		postJson(admin, "/admin/printers", json("name", "", "address", "a b")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.address").value("Enter an IP address or host name, optionally with port"));
		create("Bar", "bar.local:8008", true, false, true).andExpect(status().isCreated());
		create("BAR", "192.168.1.9", true, false, true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").value("A printer with this name already exists"));
		create("Kitchen", "192.168.1.9", true, false, true, 999_999L).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.categoryIds").value("Unknown category: 999999"));
	}

	@Test
	void listShowsAllPrintersAndTheCategoriesThatAreNotPrinted() throws Exception {
		item("Pizza", "Margherita", ItemType.FOOD);
		item("Soft drinks", "Cola", ItemType.DRINK);
		item("Entrance", "Entry fee", ItemType.FOOD);
		create("Kitchen", "192.168.1.51", true, false, true, category("Pizza"));
		create("Bar", "192.168.1.50", true, false, false, category("Soft drinks"));

		as(admin, get("/admin/printers"))
			.andExpect(jsonPath("$.printers[*].name").value(org.hamcrest.Matchers.contains("Bar", "Kitchen")))
			.andExpect(jsonPath("$.unprintedCategories[*].name").value(
					org.hamcrest.Matchers.containsInAnyOrder("Soft drinks", "Entrance")));
	}

	@Test
	void deletingACategoryDropsItsAssignment() throws Exception {
		menu.insertCategory("Specials");
		long kitchen = id(create("Kitchen", "192.168.1.51", true, false, true, category("Specials")));

		jdbc.sql("DELETE FROM category WHERE name = 'Specials'").update();

		assertThat(printerRepository.categories(kitchen)).isEmpty();
		assertThat(printerRepository.findById(kitchen)).isPresent();
	}

	@Test
	void testPrintReportsSuccessAndFailure() throws Exception {
		var fake = fakePrinter();
		item("Pizza", "Margherita", ItemType.FOOD);
		long ok = id(create("Kitchen", fake.address(), true, true, true, category("Pizza")));
		long offline = id(create("Bar", OFFLINE, true, false, true));

		as(admin, post("/admin/printers/" + ok + "/test")).andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.error").doesNotExist());
		as(admin, post("/admin/printers/" + offline + "/test")).andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error").value("printer offline"));

		assertThat(fake.requests).singleElement().satisfies(r -> assertThat(r).contains("Test print")
			.contains("Prints tickets: Pizza")
			.contains("Prints receipts")
			.contains("Umlauts: ä ö ü"));
		fake.answer(FakeEposPrinter.response(false, "EPTR_COVER_OPEN"));
		as(admin, post("/admin/printers/" + ok + "/test")).andExpect(jsonPath("$.error").value("cover open"));
	}

	@Test
	void waitersCannotConfigurePrinters() throws Exception {
		String waiter = waiterToken();
		as(waiter, get("/admin/printers")).andExpect(status().isForbidden());
		postJson(waiter, "/admin/printers", json("name", "Bar", "address", "1.2.3.4", "printsTickets", true))
			.andExpect(status().isForbidden());
		assertThat(printerRepository.findAll()).isEmpty();
	}

	@Test
	void bothSwitchesAreAvailableInEveryMode() throws Exception {
		create("Counter", "192.168.1.60", false, true, true).andExpect(status().isCreated())
			.andExpect(jsonPath("$.printsReceipts").value(true));
		settingsService.changeMode(Mode.SALON);
		menu.insertCategory("Hair");
		create("Salon desk", "192.168.1.61", true, false, true, category("Hair")).andExpect(status().isCreated());
		create("Front", "192.168.1.62", false, true, true).andExpect(status().isCreated());
	}

	private long category(String name) {
		return menu.findAllCategories().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
	}

	private ResultActions create(String name, String address, boolean tickets, boolean receipts, boolean active,
			Long... categoryIds) throws Exception {
		return postJson(admin, "/admin/printers", body(name, address, tickets, receipts, active, categoryIds));
	}

	private ResultActions update(long id, String name, String address, boolean tickets, boolean receipts,
			boolean active, Long... categoryIds) throws Exception {
		return as(admin, put("/admin/printers/" + id).contentType(MediaType.APPLICATION_JSON)
			.content(body(name, address, tickets, receipts, active, categoryIds)));
	}

	private static String body(String name, String address, boolean tickets, boolean receipts, boolean active,
			Long... categoryIds) {
		String json = json("name", name, "address", address, "printsTickets", tickets, "printsReceipts", receipts,
				"active", active);
		return json.substring(0, json.length() - 1) + ",\"categoryIds\":" + java.util.Arrays.toString(categoryIds) + "}";
	}

	private static long id(ResultActions created) throws Exception {
		return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
