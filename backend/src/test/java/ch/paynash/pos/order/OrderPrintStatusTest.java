package ch.paynash.pos.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.PrintingTestSupport;
import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.FakeEposPrinter;

/** Spec order-taking, "Order history": print status and who cancelled at which station. */
class OrderPrintStatusTest extends PrintingTestSupport {

	private String waiter;

	private FakeEposPrinter bar;

	private long cola;

	private long beer;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		bar = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		cola = item("Drinks", "Cola", ItemType.DRINK);
		beer = item("Drinks", "Beer", ItemType.DRINK);
	}

	@Test
	void historyShowsATicketWaitingForPaper() throws Exception {
		bar.paperEnd(true);
		sendOrder(waiter, "12", line(cola, 1));
		worker.deliverDue();

		as(waiter, get("/orders")).andExpect(jsonPath("$[0].printStatus").value("pending"))
			.andExpect(jsonPath("$[0].printError").value("paper end"));
	}

	@Test
	void historyTellsUnclearFromFailed() throws Exception {
		bar.answer(FakeEposPrinter.response(false, "EPTR_CUTTER"));
		long unclear = sendOrder(waiter, "1", line(cola, 1));
		worker.deliverDue();
		bar.answer(FakeEposPrinter.response(false, "DeviceNotFound"));
		long failed = sendOrder(waiter, "2", line(cola, 1));
		worker.deliverDue();

		as(waiter, get("/orders")).andExpect(jsonPath("$[0].id").value(failed))
			.andExpect(jsonPath("$[0].printStatus").value("failed"))
			.andExpect(jsonPath("$[0].printError").value("device ID not found on the printer"))
			.andExpect(jsonPath("$[1].id").value(unclear))
			.andExpect(jsonPath("$[1].printStatus").value("unclear"))
			.andExpect(jsonPath("$[1].printError").value("cutter error; check the ticket and reprint if needed"));
	}

	@Test
	void orderShowsWhoCancelledAtWhichStation() throws Exception {
		long barId = printerRepository.findAll().getFirst().id();
		createStation("station-bar", "5555", barId);
		String station = login("station-bar", "5555");
		createStaff("boss", Role.ADMIN, "1111");
		String admin = login("boss", "1111");
		long id = sendOrder(waiter, "12", line(cola, 1), line(beer, 1));

		as(station, post("/station/orders/" + id + "/lines/" + lineId(id, "Cola") + "/cancel")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "anna", "pin", "4711", "reason", "sold out"))).andExpect(status().isOk());
		postJson(admin, "/admin/orders/" + id + "/void", json("reason", "guests left")).andExpect(status().isOk());

		as(waiter, get("/orders/" + id)).andExpect(jsonPath("$.lines[0].cancelledBy").value("anna Display"))
			.andExpect(jsonPath("$.lines[0].cancelledAtStation").value("station-bar Display"))
			.andExpect(jsonPath("$.lines[1].cancelledBy").value("boss Display"))
			.andExpect(jsonPath("$.lines[1].cancelledAtStation").doesNotExist())
			.andExpect(jsonPath("$.voidedBy").value("boss Display"))
			.andExpect(jsonPath("$.voidedAtStation").doesNotExist());
	}

}
