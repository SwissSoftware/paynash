package ch.paynash.pos.invoice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.settings.Mode;
import ch.paynash.pos.settings.SettingsService;
import ch.paynash.pos.support.IntegrationTest;

/** A salon with business details, two services, a customer "Laura Meier", an admin "boss", and staff "anna". */
abstract class InvoiceTestSupport extends IntegrationTest {

	@Autowired
	protected MenuRepository menu;

	protected String boss;

	protected String staff;

	protected long facial;

	protected long tint;

	protected long laura;

	@BeforeEach
	void setUpSalon() throws Exception {
		boss = adminToken();
		staff = waiterToken();
		settingsService.changeMode(Mode.SALON);
		settingsService.updateDetails(details(false, null));
		long face = menu.insertCategory("Face");
		facial = menu.insertItem(face, "Facial", ItemType.SERVICE, 12000, null, true, 60, 81);
		tint = menu.insertItem(face, "Eyebrow tint", ItemType.SERVICE, 2500, null, true, null, 81);
		laura = customer("Laura", "Meier", "Bahnhofstrasse 5", "3000", "Bern");
	}

	protected static SettingsService.DetailsInput details(boolean vat, String uid) {
		return new SettingsService.DetailsInput("Kosmetik Aurora", "Bahnhofstrasse 1", "3000", "Bern",
				"031 123 45 67", "info@aurora.ch", null, "CH93 0076 2011 6238 5295 7", "Aurora GmbH", vat, uid);
	}

	protected ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	protected ResultActions postJson(String token, String path, String body) throws Exception {
		return as(token, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected long customer(String first, String last, String street, String postalCode, String city)
			throws Exception {
		return id(postJson(staff, "/customers", json("firstName", first, "lastName", last, "street", street,
				"postalCode", postalCode, "city", city)).andExpect(status().isCreated()));
	}

	protected static String line(long itemId, int qty) {
		return json("menuItemId", itemId, "qty", qty);
	}

	/** Confirms an order for the customer and returns its id. */
	protected long confirm(long customerId, String... lines) throws Exception {
		return id(postJson(staff, "/orders",
				"{\"customerId\":" + customerId + ",\"lines\":[" + String.join(",", lines) + "]}")
			.andExpect(status().isCreated()));
	}

	protected ResultActions issue(String token, long orderId) throws Exception {
		return as(token, post("/orders/" + orderId + "/invoice"));
	}

	/** Issues the invoice of the order and returns its id. */
	protected long issued(long orderId) throws Exception {
		return id(issue(staff, orderId).andExpect(status().isCreated()));
	}

	protected static long id(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	protected static String string(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
