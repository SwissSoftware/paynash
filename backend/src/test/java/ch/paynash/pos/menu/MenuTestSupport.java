package ch.paynash.pos.menu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.support.IntegrationTest;

abstract class MenuTestSupport extends IntegrationTest {

	protected String admin;

	protected long createCategory(String name) throws Exception {
		String body = mvc.perform(post("/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("name", name))).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	protected long createItem(long categoryId, String name, String type, String price) throws Exception {
		String body = mvc.perform(post("/admin/items").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("categoryId", categoryId, "name", name, "type", type, "price", price)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

}
