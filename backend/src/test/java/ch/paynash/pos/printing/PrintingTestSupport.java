package ch.paynash.pos.printing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
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

abstract class PrintingTestSupport extends IntegrationTest {

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

	protected long printer(String name, String address, ItemType type) {
		return printerRepository.insert(name, address, "local_printer", type, true);
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
		return menu.insertItem(categoryId, name, type, 450, null, true);
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

	protected PrintJob job(long orderId, ItemType type) {
		return jobs(orderId).stream().filter(j -> j.itemType() == type).findFirst().orElseThrow();
	}

	protected long lineId(long orderId, String name) {
		return jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = ?")
			.params(orderId, name)
			.query(Long.class)
			.single();
	}

}
