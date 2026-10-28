package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.menu.ItemType;

class SendRoundTest extends OrderTestSupport {

	private String anna;

	private long cola;

	private long beer;

	private long margherita;

	private long order;

	@BeforeEach
	void setUp() throws Exception {
		anna = waiterToken();
		long drinks = category("Drinks");
		cola = item(drinks, "Cola", ItemType.DRINK, 450);
		beer = item(drinks, "Beer", ItemType.DRINK, 600);
		margherita = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
		order = openOrder(anna, "12");
	}

	@Test
	void firstRoundSendsAllUnsentLines() throws Exception {
		addLine(anna, order, cola, 2);
		addLine(anna, order, beer, 1);
		addLine(anna, order, margherita, 1);

		send(anna, order).andExpect(status().isOk())
			.andExpect(jsonPath("$.round.number").value(1))
			.andExpect(jsonPath("$.round.sentBy").value("anna Display"))
			.andExpect(jsonPath("$.round.sentAt").exists())
			.andExpect(jsonPath("$.order.lines[*].status", contains("sent", "sent", "sent")))
			.andExpect(jsonPath("$.order.lines[*].roundNumber", contains(1, 1, 1)))
			.andExpect(jsonPath("$.order.hasUnsentLines").value(false));

		assertThat(printing.rounds).hasSize(1);
		var round = printing.rounds.get(0);
		assertThat(round.round().number()).isEqualTo(1);
		assertThat(round.order().tableLabel()).isEqualTo("12");
		assertThat(round.lines()).extracting(OrderLine::name).containsExactly("Cola", "Beer", "Margherita");
	}

	@Test
	void followUpRoundPrintsOnlyTheNewLines() throws Exception {
		addLine(anna, order, cola, 2);
		addLine(anna, order, margherita, 1);
		send(anna, order);
		addLine(anna, order, beer, 1);

		send(anna, order).andExpect(jsonPath("$.round.number").value(2))
			.andExpect(jsonPath("$.order.lines[*].roundNumber", contains(1, 1, 2)))
			.andExpect(jsonPath("$.order.rounds[*].number", contains(1, 2)));

		assertThat(printing.rounds).hasSize(2);
		assertThat(printing.rounds.get(1).lines()).extracting(OrderLine::name).containsExactly("Beer");
	}

	@Test
	void sendingWithoutUnsentLinesDoesNothing() throws Exception {
		send(anna, order).andExpect(status().isOk()).andExpect(jsonPath("$.round").doesNotExist());
		addLine(anna, order, cola, 1);
		send(anna, order);

		send(anna, order).andExpect(jsonPath("$.round").doesNotExist())
			.andExpect(jsonPath("$.order.rounds[*].number", contains(1)));
		assertThat(printing.rounds).hasSize(1);
	}

	@Test
	void doubleRequestWithSameKeyCreatesOneRound() throws Exception {
		addLine(anna, order, cola, 1);
		String first = as(anna, post("/orders/" + order + "/send").header(IdempotencyFilter.HEADER, "send-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.round.number").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String second = as(anna, post("/orders/" + order + "/send").header(IdempotencyFilter.HEADER, "send-1"))
			.andExpect(status().isOk())
			.andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(printing.rounds).hasSize(1);
		assertThat(jdbc.sql("SELECT count(*) FROM order_round").query(Long.class).single()).isEqualTo(1);
	}

}
