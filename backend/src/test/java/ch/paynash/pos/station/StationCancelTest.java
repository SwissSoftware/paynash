package ch.paynash.pos.station;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.printing.PrintJob;
import ch.paynash.pos.printing.PrintingTestSupport;
import ch.paynash.pos.staff.Role;

/** Spec order-taking, "Cancel sent lines", "Void a sent order", "PIN confirmation for station cancellations". */
class StationCancelTest extends PrintingTestSupport {

	private String waiter;

	private String barStation;

	private long stationId;

	private long annaId;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		annaId = staffRepository.findByUsername("anna").orElseThrow().id();
		long bar = printer("Bar", OFFLINE, ItemType.DRINK);
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		stationId = createStation("station-bar", "5555", bar);
		barStation = login("station-bar", "5555");
		cola = item("Drinks", "Beer", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
	}

	@Test
	void waiterCancelsADrinkAtTheStationWithHerPin() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1), line(pizza, 1));

		cancel(id, lineId(id, "Beer"), "anna", "4711", "sold out").andExpect(status().isOk())
			.andExpect(jsonPath("$.lines[0].status").value("cancelled"))
			.andExpect(jsonPath("$.lines[0].cancelReason").value("sold out"));

		assertThat(jdbc.sql("SELECT cancelled_by FROM order_line WHERE id = ?").param(lineId(id, "Beer"))
			.query(Long.class)
			.single()).isEqualTo(annaId);
		assertThat(jdbc.sql("SELECT cancelled_at_station FROM order_line WHERE id = ?").param(lineId(id, "Beer"))
			.query(Long.class)
			.single()).isEqualTo(stationId);
		PrintJob storno = jobs(id).getLast();
		assertThat(storno.kind()).isEqualTo(PrintJob.Kind.STORNO);
		assertThat(storno.printerName()).isEqualTo("Bar");
	}

	@Test
	void wrongPinChangesNothing() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1));

		cancel(id, lineId(id, "Beer"), "anna", "0000", "sold out").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.pin").value("Wrong username or PIN"));

		assertThat(jdbc.sql("SELECT status FROM order_line WHERE order_id = ?").param(id).query(String.class).single())
			.isEqualTo("sent");
		assertThat(jobs(id)).noneMatch(j -> j.kind() == PrintJob.Kind.STORNO);
		as(barStation, get("/station/printers")).andExpect(status().isOk());
	}

	@Test
	void deactivatedAndStationAccountsCannotConfirm() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1));
		long bob = createStaff("bob", Role.WAITER, "2222");
		jdbc.sql("UPDATE staff SET active = false WHERE id = ?").param(bob).update();

		cancel(id, lineId(id, "Beer"), "bob", "2222", "x").andExpect(status().isBadRequest());
		cancel(id, lineId(id, "Beer"), "station-bar", "5555", "x").andExpect(status().isBadRequest());
		cancel(id, lineId(id, "Beer"), "nobody", "1234", "x").andExpect(status().isBadRequest());
	}

	@Test
	void repeatedWrongPinsLockTheAccount() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1));
		for (int i = 0; i < 5; i++) {
			cancel(id, lineId(id, "Beer"), "anna", "0000", "x").andExpect(status().isBadRequest());
		}

		cancel(id, lineId(id, "Beer"), "anna", "4711", "x").andExpect(status().isBadRequest());
		assertThat(staffRepository.findByUsername("anna").orElseThrow().isLockedAt(clock.instant())).isTrue();
	}

	@Test
	void anyActiveStaffMemberVoidsAnOrderAtTheStation() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1), line(pizza, 1));
		long boss = createStaff("boss", Role.ADMIN, "1111");

		as(barStation, post("/station/orders/" + id + "/void").contentType(MediaType.APPLICATION_JSON)
			.content(json("username", "boss", "pin", "1111", "reason", "double order"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("voided"))
			.andExpect(jsonPath("$.total").value("0.00"));

		assertThat(jdbc.sql("SELECT voided_by FROM orders WHERE id = ?").param(id).query(Long.class).single())
			.isEqualTo(boss);
		assertThat(jdbc.sql("SELECT voided_at_station FROM orders WHERE id = ?").param(id).query(Long.class).single())
			.isEqualTo(stationId);
		assertThat(jobs(id)).filteredOn(j -> j.kind() == PrintJob.Kind.STORNO).hasSize(2);
	}

	@Test
	void stationReadsTheOrderOfATicket() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 2));

		as(barStation, get("/station/orders/" + id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tableLabel").value("12"))
			.andExpect(jsonPath("$.lines[0].name").value("Beer"));
	}

	@Test
	void waiterStillCannotCancelFromHerOwnDevice() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1));

		postJson(waiter, "/admin/orders/" + id + "/lines/" + lineId(id, "Beer") + "/cancel", json("reason", "x"))
			.andExpect(status().isForbidden());
		postJson(waiter, "/station/orders/" + id + "/lines/" + lineId(id, "Beer") + "/cancel",
				json("username", "anna", "pin", "4711", "reason", "x"))
			.andExpect(status().isForbidden());
	}

	private ResultActions cancel(long orderId, long lineId, String username, String pin, String reason)
			throws Exception {
		return as(barStation, post("/station/orders/" + orderId + "/lines/" + lineId + "/cancel")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("username", username, "pin", pin, "reason", reason)));
	}

}
