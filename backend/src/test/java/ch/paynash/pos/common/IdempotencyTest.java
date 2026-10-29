package ch.paynash.pos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.IntegrationTest;

class IdempotencyTest extends IntegrationTest {

	@Autowired
	MenuRepository menu;

	private String anna;

	private long cola;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		cola = menu.insertItem(menu.insertCategory("Drinks"), "Cola", ItemType.DRINK, 450, null, true);
	}

	@Test
	void sameKeyTwiceGivesIdenticalResultAndOneEffect() throws Exception {
		var first = request(anna, "/orders", "k1", order(1)).andExpect(status().isCreated())
			.andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
			.andReturn()
			.getResponse();
		var second = request(anna, "/orders", "k1", order(1)).andExpect(status().isCreated())
			.andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
			.andReturn()
			.getResponse();

		assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
		assertThat(second.getContentType()).isEqualTo(first.getContentType());
		assertThat(lineCount()).isEqualTo(1);
	}

	@Test
	void differentKeysAreDifferentRequests() throws Exception {
		request(anna, "/orders", "k1", order(1));
		request(anna, "/orders", "k2", order(1));
		request(anna, "/orders", null, order(1));
		assertThat(lineCount()).isEqualTo(3);
	}

	@Test
	void keyReusedForADifferentRequestIsRejected() throws Exception {
		request(anna, "/orders", "k1", order(1));
		request(anna, "/orders", "k1", order(2)).andExpect(status().isUnprocessableContent());
		assertThat(lineCount()).isEqualTo(1);
	}

	@Test
	void keysAreScopedPerStaffMember() throws Exception {
		createStaff("ben", Role.WAITER, "2222");
		String ben = login("ben", "2222");
		request(anna, "/orders", "k1", order(1));
		request(ben, "/orders", "k1", order(1))
			.andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
		assertThat(lineCount()).isEqualTo(2);
	}

	@Test
	void errorResponsesAreReplayedToo() throws Exception {
		request(anna, "/orders", "k1", order(0)).andExpect(status().isBadRequest());
		request(anna, "/orders", "k1", order(0)).andExpect(status().isBadRequest())
			.andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"));
	}

	@Test
	void requestStillInProgressAnswers409() throws Exception {
		long staffId = staffRepository.findByUsername("anna").orElseThrow().id();
		String body = order(1);
		request(anna, "/orders", "k1", body);
		// Simulate the first request still running: same fingerprint, no stored response yet.
		jdbc.sql("UPDATE idempotency_key SET response_status = NULL WHERE staff_id = ?").param(staffId).update();

		request(anna, "/orders", "k1", body).andExpect(status().isConflict());
		assertThat(lineCount()).isEqualTo(1);
	}

	@Test
	void keysExpireAfter24Hours() throws Exception {
		request(anna, "/orders", "k1", order(1));
		clock.advance(Duration.ofHours(24).plusMinutes(1));
		anna = login("anna", "4711"); // the 12 h session has expired as well
		request(anna, "/orders", "k1", order(1))
			.andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
		assertThat(lineCount()).isEqualTo(2);
		// The expired key was cleaned up and replaced.
		assertThat(jdbc.sql("SELECT count(*) FROM idempotency_key").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void readsAndOtherPathsAreNotAffected() throws Exception {
		mvc.perform(get("/menu")
			.header(HttpHeaders.AUTHORIZATION, bearer(anna))
			.header(IdempotencyFilter.HEADER, "k1")).andExpect(status().isOk());
		assertThat(jdbc.sql("SELECT count(*) FROM idempotency_key").query(Long.class).single()).isZero();
	}

	private ResultActions request(String token, String path, String key, String body) throws Exception {
		var request = post(path).header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (key != null) {
			request.header(IdempotencyFilter.HEADER, key);
		}
		return mvc.perform(request);
	}

	/** A send request for table 12 with one Cola line. */
	private String order(int qty) {
		return "{\"tableLabel\":\"12\",\"lines\":[" + json("menuItemId", cola, "qty", qty) + "]}";
	}

	private long lineCount() {
		return jdbc.sql("SELECT count(*) FROM order_line").query(Long.class).single();
	}

}
