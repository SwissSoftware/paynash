package ch.paynash.pos.station;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.PrintingTestSupport;

/** Spec print-station, "Station ticket list" and "Printer status at the station"; staff-access, station permissions. */
class StationTicketsTest extends PrintingTestSupport {

	private String waiter;

	private String barStation;

	private long bar;

	private long kitchen;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		bar = printer("Bar", OFFLINE, ItemType.DRINK);
		kitchen = printer("Kitchen", OFFLINE, ItemType.FOOD);
		createStation("station-bar", "5555", bar);
		barStation = login("station-bar", "5555");
		cola = item("Drinks", "Cola", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		clock.set("2026-10-29T18:30:00Z");
	}

	@Test
	void barStationListsOnlyDrinkTicketsNewestFirstWithTheirDetails() throws Exception {
		long first = sendOrder(waiter, "12", line(cola, 2), line(pizza, 1));
		clock.advance(Duration.ofMinutes(1));
		long second = sendOrder(waiter, "7", line(cola, 1));

		tickets(bar).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].orderId").value(second))
			.andExpect(jsonPath("$[0].label").value("BAR 0002"))
			.andExpect(jsonPath("$[0].tableLabel").value("7"))
			.andExpect(jsonPath("$[1].orderId").value(first))
			.andExpect(jsonPath("$[1].ticketNo").value(1))
			.andExpect(jsonPath("$[1].tableLabel").value("12"))
			.andExpect(jsonPath("$[1].waiter").value("anna Display"))
			.andExpect(jsonPath("$[1].lines").value(1))
			.andExpect(jsonPath("$[1].kind").value("normal"))
			.andExpect(jsonPath("$[1].status").value("pending"))
			.andExpect(jsonPath("$[1].needsResolution").value(false));
	}

	@Test
	void stationCannotSeeAnotherPrinter() throws Exception {
		tickets(kitchen).andExpect(status().isForbidden());
	}

	@Test
	void unchangedListAnswers304() throws Exception {
		sendOrder(waiter, "12", line(cola, 1));
		String etag = tickets(bar).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

		as(barStation, get("/station/tickets").param("printerId", String.valueOf(bar))
			.header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());
		sendOrder(waiter, "13", line(cola, 1));
		as(barStation, get("/station/tickets").param("printerId", String.valueOf(bar))
			.header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isOk()).andExpect(header().exists("ETag"));
	}

	@Test
	void unresolvedUnclearTicketIsPinnedOnTop() throws Exception {
		long unclear = sendOrder(waiter, "1", line(cola, 1));
		sendOrder(waiter, "2", line(cola, 1));
		jobRepository.markUnclear(job(unclear, ItemType.DRINK).id(), "no answer");

		tickets(bar).andExpect(jsonPath("$[0].orderId").value(unclear))
			.andExpect(jsonPath("$[0].status").value("unclear"))
			.andExpect(jsonPath("$[0].error").value("no answer"))
			.andExpect(jsonPath("$[0].needsResolution").value(true));
	}

	@Test
	void yesterdaysPrintedTicketsDisappearButOpenProblemsStay() throws Exception {
		long printed = sendOrder(waiter, "1", line(cola, 1));
		long unclear = sendOrder(waiter, "2", line(cola, 1));
		long waiting = sendOrder(waiter, "3", line(cola, 1));
		jobRepository.markPrinted(job(printed, ItemType.DRINK).id(), clock.instant());
		jobRepository.markUnclear(job(unclear, ItemType.DRINK).id(), "no answer");
		clock.advance(Duration.ofHours(8)); // after midnight in Zurich

		tickets(bar).andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].orderId").value(unclear))
			.andExpect(jsonPath("$[1].orderId").value(waiting));
	}

	@Test
	void printersShowStatusAndQueue() throws Exception {
		long first = sendOrder(waiter, "1", line(cola, 1));
		jobRepository.markPrinted(job(first, ItemType.DRINK).id(), clock.instant());
		clock.advance(Duration.ofMinutes(2));
		sendOrder(waiter, "2", line(cola, 1));
		clock.advance(Duration.ofMinutes(1));
		sendOrder(waiter, "3", line(cola, 1));

		as(barStation, get("/station/printers")).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("Bar"))
			.andExpect(jsonPath("$[0].status").value("unknown"))
			.andExpect(jsonPath("$[0].lastPrintedTicketNo").value(1))
			.andExpect(jsonPath("$[0].lastPrintedLabel").value("BAR 0001"))
			.andExpect(jsonPath("$[0].waiting").value(2))
			.andExpect(jsonPath("$[0].oldestWaitingSince").value("2026-10-29T18:32:00Z"))
			.andExpect(jsonPath("$[0].oldestWaitingSeconds").value(60))
			.andExpect(jsonPath("$[0].unclear").value(0));
	}

	@Test
	void eachPollRecordsWhenTheStationWasLastSeenForAdmins() throws Exception {
		String admin = adminToken();
		as(admin, get("/admin/printers")).andExpect(jsonPath("$[0].name").value("Bar"))
			.andExpect(jsonPath("$[0].stations[0].name").value("station-bar Display"))
			.andExpect(jsonPath("$[0].stations[0].lastSeenAt").doesNotExist());

		as(barStation, get("/station/printers")).andExpect(status().isOk());
		as(admin, get("/admin/printers")).andExpect(jsonPath("$[0].stations[0].lastSeenAt").value("2026-10-29T18:30:00Z"));

		clock.advance(Duration.ofSeconds(2));
		tickets(bar).andExpect(status().isOk());
		as(admin, get("/admin/printers")).andExpect(jsonPath("$[0].stations[0].lastSeenAt").value("2026-10-29T18:30:02Z"))
			.andExpect(jsonPath("$[1].stations.length()").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions tickets(long printerId) throws Exception {
		return as(barStation, get("/station/tickets").param("printerId", String.valueOf(printerId)));
	}

}
