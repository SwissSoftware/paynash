package ch.paynash.pos.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.TestcontainersConfiguration;
import ch.paynash.pos.auth.PinService;
import ch.paynash.pos.staff.Role;
import ch.paynash.pos.staff.StaffRepository;

/**
 * Full application against PostgreSQL (Testcontainers), with empty tables, a controllable clock, and a ticket printing
 * hook that only records.
 */
@SpringBootTest(properties = "pos.image-dir=target/test-images")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, IntegrationTest.TestBeans.class })
public abstract class IntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock();
		}

		@Bean
		@Primary
		RecordingTicketPrinting recordingTicketPrinting() {
			return new RecordingTicketPrinting();
		}

	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected StaffRepository staffRepository;

	@Autowired
	protected PinService pinService;

	@Autowired
	protected RecordingTicketPrinting printing;

	@BeforeEach
	void resetState() {
		jdbc.sql("DELETE FROM idempotency_key").update();
		jdbc.sql("DELETE FROM order_line").update();
		jdbc.sql("DELETE FROM order_round").update();
		jdbc.sql("DELETE FROM orders").update();
		jdbc.sql("DELETE FROM menu_item").update();
		jdbc.sql("DELETE FROM category").update();
		jdbc.sql("DELETE FROM session").update();
		jdbc.sql("DELETE FROM staff").update();
		clock.reset();
		printing.reset();
	}

	protected long createStaff(String username, Role role, String pin) {
		return staffRepository.insert(username, username + " Display", role, pinService.hash(pin));
	}

	protected String login(String username, String pin) throws Exception {
		String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(json("username", username, "pin", pin)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	/** Creates an admin "boss" and returns a bearer token for it. */
	protected String adminToken() throws Exception {
		createStaff("boss", Role.ADMIN, "1111");
		return login("boss", "1111");
	}

	/** Creates a waiter "anna" and returns a bearer token for her. */
	protected String waiterToken() throws Exception {
		createStaff("anna", Role.WAITER, "4711");
		return login("anna", "4711");
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	/** Builds a flat JSON object from key/value pairs; values are strings unless they are Boolean or Number. */
	protected static String json(Object... keyValues) {
		var sb = new StringBuilder("{");
		for (int i = 0; i < keyValues.length; i += 2) {
			if (i > 0) {
				sb.append(',');
			}
			Object v = keyValues[i + 1];
			sb.append('"').append(keyValues[i]).append("\":");
			sb.append(v instanceof Boolean || v instanceof Number ? v.toString() : "\"" + v + "\"");
		}
		return sb.append('}').toString();
	}

}
