package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

/** Snapshots of the tickets an order produces (spec ticket-printing, "Route lines by type" and "Ticket content"). */
class TicketRenderingTest extends PrintingTestSupport {

	private static final String LINE = "  " + "-".repeat(48);

	private String waiter;

	private String admin;

	private long cola;

	private long beer;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		admin = adminToken();
		printer("Bar", OFFLINE, ItemType.DRINK);
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		cola = item("Drinks", "Cola", ItemType.DRINK);
		beer = item("Drinks", "Beer", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		clock.set("2026-10-29T18:30:00Z");
	}

	@Test
	void mixedOrderGivesOneTicketPerPrinterWithOnlyItsLines() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 2), line(pizza, 1, "extra spicy"), line(beer, 1));

		assertThat(jobs(id)).hasSize(2);
		assertThat(job(id, ItemType.DRINK).content()).isEqualTo("""
				# Table 12
				# Order %d
				# anna Display
				# 29.10.2026 19:30
				%s
				# 2 x Cola
				# 1 x Beer""".formatted(id, LINE));
		assertThat(job(id, ItemType.FOOD).content()).isEqualTo("""
				# Table 12
				# Order %d
				# anna Display
				# 29.10.2026 19:30
				%s
				# 1 x Margherita
				#   > extra spicy""".formatted(id, LINE));
		assertThat(job(id, ItemType.DRINK).printerName()).isEqualTo("Bar");
		assertThat(job(id, ItemType.FOOD).printerName()).isEqualTo("Kitchen");
		assertThat(jobs(id)).allSatisfy(j -> {
			assertThat(j.kind()).isEqualTo(PrintJob.Kind.NORMAL);
			assertThat(j.status()).isEqualTo(PrintJob.Status.PENDING);
		});
	}

	@Test
	void drinksOnlyGoToTheBarOnly() throws Exception {
		long id = sendOrder(waiter, "3", line(cola, 1));

		assertThat(jobs(id)).singleElement().satisfies(j -> assertThat(j.printerName()).isEqualTo("Bar"));
	}

	@Test
	void cancelledLinePrintsAStornoOnItsPrinter() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 2), line(pizza, 1));
		clock.set("2026-10-29T18:45:00Z");

		postJson(admin, "/admin/orders/" + id + "/lines/" + lineId(id, "Cola") + "/cancel", json("reason", "spilled"))
			.andExpect(status().isOk());

		PrintJob storno = jobs(id).getLast();
		assertThat(storno.kind()).isEqualTo(PrintJob.Kind.STORNO);
		assertThat(storno.printerName()).isEqualTo("Bar");
		assertThat(storno.content()).isEqualTo("""
				# *** STORNO ***
				# Table 12
				# Order %d
				# 29.10.2026 19:45
				%s
				# 2 x Cola
				%s
				# Reason: spilled""".formatted(id, LINE, LINE));
	}

	@Test
	void voidPrintsAtMostOneStornoPerPrinterWithTheLinesStillSent() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 2), line(beer, 1), line(pizza, 1));
		postJson(admin, "/admin/orders/" + id + "/lines/" + lineId(id, "Beer") + "/cancel", json("reason", "wrong"))
			.andExpect(status().isOk());
		clock.set("2026-10-29T19:00:00Z");

		postJson(admin, "/admin/orders/" + id + "/void", json("reason", "guests left")).andExpect(status().isOk());

		var voidTickets = jobs(id).stream().filter(j -> j.kind() == PrintJob.Kind.STORNO).skip(1).toList();
		assertThat(voidTickets).hasSize(2);
		assertThat(voidTickets.get(0).content()).isEqualTo("""
				# *** STORNO ***
				# Table 12
				# Order %d voided
				# 29.10.2026 20:00
				%s
				# 2 x Cola
				%s
				# Reason: guests left""".formatted(id, LINE, LINE));
		assertThat(voidTickets.get(1).printerName()).isEqualTo("Kitchen");
		assertThat(voidTickets.get(1).content()).contains("# 1 x Margherita").doesNotContain("Cola");
	}

	@Test
	void reprintIsTheSameTicketMarkedReprint() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1));
		PrintJob original = job(id, ItemType.FOOD);
		jobRepository.markFailed(original.id(), "paper end");

		postJson(waiter, "/orders/" + id + "/tickets/" + original.id() + "/reprint", "").andExpect(status().isCreated());

		PrintJob reprint = jobs(id).getLast();
		assertThat(reprint.kind()).isEqualTo(PrintJob.Kind.REPRINT);
		assertThat(reprint.reprintOf()).isEqualTo(original.id());
		assertThat(reprint.content()).isEqualTo("# *** REPRINT ***\n" + original.content());
	}

	@Test
	void ticketsRoundTripThroughStorage() {
		var ticket = new Ticket(java.util.List.of(new Ticket.Row(true, "Table 1"), new Ticket.Row(false, ""),
				new Ticket.Row(false, "a # b")));
		assertThat(Ticket.decode(ticket.encode())).isEqualTo(ticket);
	}

}
