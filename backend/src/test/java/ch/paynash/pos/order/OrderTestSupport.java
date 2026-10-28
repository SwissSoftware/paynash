package ch.paynash.pos.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

	protected long openOrder(String token, String table) throws Exception {
		String body = postJson(token, "/orders", json("tableLabel", table)).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	/** Adds a line and returns its id. */
	protected long addLine(String token, long orderId, long itemId, int qty) throws Exception {
		String body = postJson(token, "/orders/" + orderId + "/lines", json("menuItemId", itemId, "qty", qty))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.lines[-1].id")).longValue();
	}

	protected ResultActions send(String token, long orderId) throws Exception {
		return as(token, post("/orders/" + orderId + "/send"));
	}

}
