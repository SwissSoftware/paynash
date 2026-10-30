package ch.paynash.pos.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.menu.MenuRepository;
import ch.paynash.pos.support.IntegrationTest;

/** Spec order-export. */
class OrderExportTest extends IntegrationTest {

	private static final String HEADER = "order_id,table,waiter,sent_at,order_status,item_name,item_type,category,"
			+ "quantity,unit_price,line_total,line_status,note";

	@Autowired
	private MenuRepository menu;

	private String admin;

	private String waiter;

	private long cola;

	private long roesti;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
		waiter = waiterToken();
		cola = menu.insertItem(menu.insertCategory("Drinks"), "Cola", ItemType.DRINK, 450, null, true);
		roesti = menu.insertItem(menu.insertCategory("Mains"), "Rösti", ItemType.FOOD, 1850, null, true);
	}

	@Test
	void exportsExactlyTheLinesOfOneLocalDay() throws Exception {
		clock.set("2026-09-30T21:59:00Z"); // 23:59 on 30 Sept in Zurich
		send("1", line(cola, 1, null));
		clock.set("2026-09-30T22:00:00Z"); // 00:00 on 1 Oct
		long first = send("12", line(cola, 2, null), line(roesti, 1, "ohne \"Zwiebeln\", bitte"));
		clock.set("2026-10-01T21:59:59Z"); // 23:59:59 on 1 Oct
		long last = send("3", line(roesti, 1, null));
		clock.set("2026-10-01T22:00:00Z"); // 00:00 on 2 Oct
		send("4", line(cola, 1, null));

		var result = export("from=2026-10-01&to=2026-10-01").andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"orders_2026-10-01_2026-10-01.csv\""));

		assertThat(lines(result)).containsExactly(HEADER,
				first + ",12,anna Display,2026-10-01T00:00:00+02:00,sent,Cola,drink,Drinks,2,4.50,9.00,sent,",
				first + ",12,anna Display,2026-10-01T00:00:00+02:00,sent,Rösti,food,Mains,1,18.50,18.50,sent,"
						+ "\"ohne \"\"Zwiebeln\"\", bitte\"",
				last + ",3,anna Display,2026-10-01T23:59:59+02:00,sent,Rösti,food,Mains,1,18.50,18.50,sent,");
	}

	@Test
	void cancelledLineHasLineTotalZero() throws Exception {
		clock.set("2026-10-01T10:00:00Z");
		long id = send("12", line(cola, 2, null), line(roesti, 1, null));
		long colaLine = jdbc.sql("SELECT id FROM order_line WHERE order_id = ? AND name_snapshot = 'Cola'")
			.param(id)
			.query(Long.class)
			.single();
		postJson(admin, "/admin/orders/" + id + "/lines/" + colaLine + "/cancel", json("reason", "spilled"))
			.andExpect(status().isOk());

		assertThat(lines(export("from=2026-10-01&to=2026-10-01"))).element(1)
			.isEqualTo(id + ",12,anna Display,2026-10-01T12:00:00+02:00,sent,Cola,drink,Drinks,2,4.50,0.00,cancelled,");
	}

	@Test
	void filtersByOrderStatusWithSentAsDefault() throws Exception {
		clock.set("2026-10-01T10:00:00Z");
		long sent = send("1", line(cola, 1, null));
		long voided = send("2", line(cola, 1, null));
		postJson(admin, "/admin/orders/" + voided + "/void", json("reason", "test")).andExpect(status().isOk());

		assertThat(orderIds(export("from=2026-10-01&to=2026-10-01"))).containsExactly(sent);
		assertThat(orderIds(export("from=2026-10-01&to=2026-10-01&status=voided"))).containsExactly(voided);
		assertThat(orderIds(export("from=2026-10-01&to=2026-10-01&status=all"))).containsExactly(sent, voided);
		assertThat(lines(export("from=2026-10-01&to=2026-10-01&status=voided")).get(1)).contains(",voided,")
			.endsWith(",0.00,cancelled,");
	}

	@Test
	void emptyPeriodIsJustTheHeader() throws Exception {
		byte[] body = export("from=2026-01-01&to=2026-01-31").andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertThat(body).isEqualTo(("﻿" + HEADER + "\r\n").getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void startAfterEndIsRejected() throws Exception {
		export("from=2026-10-02&to=2026-10-01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.from").value("The start date must not be after the end date"));
		export("from=2026-10-01&to=2026-10-01&status=paid").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.status").exists());
		export("from=2026-10-01").andExpect(status().isBadRequest());
	}

	@Test
	void waitersCannotExport() throws Exception {
		mvc.perform(get("/exports/orders.csv?from=2026-10-01&to=2026-10-01").header(HttpHeaders.AUTHORIZATION,
				bearer(waiter)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/exports/orders.csv?from=2026-10-01&to=2026-10-01")).andExpect(status().isUnauthorized());
	}

	// --- helpers

	private ResultActions export(String query) throws Exception {
		return mvc.perform(get("/exports/orders.csv?" + query).header(HttpHeaders.AUTHORIZATION, bearer(admin)));
	}

	private ResultActions postJson(String token, String path, String body) throws Exception {
		return mvc.perform(post(path)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String line(long itemId, int qty, String note) {
		return note == null ? json("menuItemId", itemId, "qty", qty)
				: "{\"menuItemId\":" + itemId + ",\"qty\":" + qty + ",\"note\":\"" + note.replace("\"", "\\\"") + "\"}";
	}

	private long send(String table, String... lines) throws Exception {
		String body = mvc
			.perform(post("/orders")
				.header(HttpHeaders.AUTHORIZATION, bearer(waiter))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"tableLabel\":\"" + table + "\",\"lines\":[" + String.join(",", lines) + "]}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	/** The rows without the byte-order mark; line breaks inside quoted notes stay within their row. */
	private static List<String> lines(ResultActions result) throws Exception {
		String body = new String(result.andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertThat(body).startsWith("﻿");
		return List.of(body.substring(1).split("\r\n"));
	}

	private static List<Long> orderIds(ResultActions result) throws Exception {
		return lines(result).stream().skip(1).map(l -> Long.parseLong(l.substring(0, l.indexOf(',')))).toList();
	}

}
