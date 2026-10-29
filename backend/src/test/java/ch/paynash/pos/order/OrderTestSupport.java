package ch.paynash.pos.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.support.IntegrationTest;

abstract class OrderTestSupport extends IntegrationTest {

	@Autowired
	protected MenuRepository menu;

	protected long category(String name) {
		return menu.insertCategory(name);
	}

	protected long item(long categoryId, String name, ItemType type, int priceRappen) {
		return menu.insertItem(categoryId, name, type, priceRappen, null, true);
	}

	protected ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	protected ResultActions postJson(String token, String path, String body) throws Exception {
		return as(token, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** A line of a send request, e.g. {@code line(cola, 2)}. */
	protected static String line(long itemId, int qty) {
		return json("menuItemId", itemId, "qty", qty);
	}

	protected static String sendBody(String table, String... lines) {
		return "{\"tableLabel\":\"" + table + "\",\"lines\":["
				+ Arrays.stream(lines).collect(Collectors.joining(",")) + "]}";
	}

	protected ResultActions send(String token, String table, String... lines) throws Exception {
		return postJson(token, "/orders", sendBody(table, lines));
	}

	/** Sends an order and returns its id. */
	protected long sendOrder(String token, String table, String... lines) throws Exception {
		String body = send(token, table, lines).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	protected long lineId(long orderId, String name) {
		return jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = ?")
			.params(orderId, name)
			.query(Long.class)
			.single();
	}

}
