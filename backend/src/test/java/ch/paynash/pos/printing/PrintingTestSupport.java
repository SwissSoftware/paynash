package ch.paynash.pos.printing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.support.FakeEposPrinter;
import ch.paynash.pos.support.IntegrationTest;

public abstract class PrintingTestSupport extends IntegrationTest {

	@Autowired
	protected MenuRepository menu;

	@Autowired
	protected PrinterRepository printerRepository;

	@Autowired
	protected PrintJobRepository jobRepository;

	@Autowired
	protected PrintWorker worker;

	private final List<FakeEposPrinter> fakes = new java.util.ArrayList<>();

	@AfterEach
	void stopFakePrinters() {
		fakes.forEach(FakeEposPrinter::close);
	}

	protected FakeEposPrinter fakePrinter() throws Exception {
		var fake = new FakeEposPrinter();
		fakes.add(fake);
		return fake;
	}

	/** Ticket printers by the item type whose categories they print, as the migration sets up existing printers. */
	private final Map<ItemType, List<Long>> printersByType = new EnumMap<>(ItemType.class);

	/**
	 * A ticket printer for the categories holding items of {@code type}, now and when {@link #item} adds them later,
	 * so tests read like "the drink printer".
	 */
	protected long printer(String name, String address, ItemType type) {
		long id = printerRepository.insert(name, address, "local_printer", true, false, true);
		printersByType.computeIfAbsent(type, t -> new ArrayList<>()).add(id);
		jdbc.sql("""
				INSERT INTO printer_category (printer_id, category_id)
				SELECT DISTINCT ?, category_id FROM menu_item WHERE type = ? ON CONFLICT DO NOTHING""")
			.params(id, type.dbValue())
			.update();
		return id;
	}

	/** Assigns {@code category} to the printer (in addition to its other categories). */
	protected void assign(long printerId, String category) {
		jdbc.sql("""
				INSERT INTO printer_category (printer_id, category_id)
				SELECT ?, id FROM category WHERE name = ? ON CONFLICT DO NOTHING""")
			.params(printerId, category)
			.update();
	}

	/** An address where nothing listens, so connecting is refused at once. */
	protected static final String OFFLINE = "127.0.0.1:1";

	protected long item(String category, String name, ItemType type) {
		long categoryId = menu.findAllCategories()
			.stream()
			.filter(c -> c.name().equals(category))
			.findFirst()
			.map(c -> c.id())
			.orElseGet(() -> menu.insertCategory(category));
		for (long printerId : printersByType.getOrDefault(type, List.of())) {
			assign(printerId, category);
		}
		return menu.insertItem(categoryId, name, type, 450, null, true, null, 81);
	}

	protected ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	protected ResultActions postJson(String token, String path, String body) throws Exception {
		return as(token, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected static String line(long itemId, int qty) {
		return json("menuItemId", itemId, "qty", qty);
	}

	protected static String line(long itemId, int qty, String note) {
		return json("menuItemId", itemId, "qty", qty, "note", note);
	}

	/** Sends an order and returns its id. */
	protected long sendOrder(String token, String table, String... lines) throws Exception {
		String body = postJson(token, "/orders", "{\"tableLabel\":\"" + table + "\",\"lines\":["
				+ Arrays.stream(lines).collect(Collectors.joining(",")) + "]}")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	protected List<PrintJob> jobs(long orderId) {
		return jobRepository.findByOrder(orderId);
	}

	/** The order's first ticket on a printer of {@code type} (see {@link #printer}). */
	protected PrintJob job(long orderId, ItemType type) {
		List<Long> ofType = printersByType.getOrDefault(type, List.of());
		return jobs(orderId).stream().filter(j -> ofType.contains(j.printerId())).findFirst().orElseThrow();
	}

	protected long lineId(long orderId, String name) {
		return jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = ?")
			.params(orderId, name)
			.query(Long.class)
			.single();
	}

}
