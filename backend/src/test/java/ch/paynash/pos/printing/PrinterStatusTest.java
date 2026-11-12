package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.paynash.pos.menu.ItemType;

/** Printer status monitoring (spec ticket-printing, "Printer status monitoring"; design D4). */
class PrinterStatusTest extends PrintingTestSupport {

	@Autowired
	PrinterStatusMonitor monitor;

	private String admin;

	@BeforeEach
	void setUp() throws Exception {
		admin = adminToken();
	}

	@Test
	void idlePrinterThatGoesAwayIsOfflineWithinFifteenSecondsAndReadyWhenBack() throws Exception {
		var kitchen = fakePrinter();
		long id = printer("Kitchen", kitchen.address(), ItemType.FOOD);
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.UNKNOWN);

		monitor.checkDueNow();
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.READY);

		kitchen.close();
		checkEverySecondFor(Duration.ofSeconds(15));
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.OFFLINE);
		as(admin, get("/admin/printers")).andExpect(jsonPath("$[0].status").value("offline"))
			.andExpect(jsonPath("$[0].statusCheckedAt").exists());

		var back = fakePrinter();
		printerRepository.update(id, "Kitchen", back.address(), "local_printer", ItemType.FOOD, true);
		checkEverySecondFor(Duration.ofSeconds(15));
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.READY);
		assertThat(back.requests).as("checking must not print").isEmpty();
	}

	@Test
	void checksAreSpacedByTheInterval() throws Exception {
		long id = printer("Kitchen", fakePrinter().address(), ItemType.FOOD);
		monitor.checkDueNow();
		var firstCheck = monitor.status(id).checkedAt();

		clock.advance(Duration.ofSeconds(5));
		monitor.checkDueNow();
		assertThat(monitor.status(id).checkedAt()).isEqualTo(firstCheck);

		clock.advance(Duration.ofSeconds(5));
		monitor.checkDueNow();
		assertThat(monitor.status(id).checkedAt()).isAfter(firstCheck);
	}

	@Test
	void deliveryResultsUpdateTheStatusAndABusyPrinterIsNotCheckedOver() throws Exception {
		var bar = fakePrinter();
		long id = printer("Bar", bar.address(), ItemType.DRINK);
		long cola = item("Drinks", "Cola", ItemType.DRINK);
		bar.paperEnd(true);
		sendOrder(waiterToken(), "12", line(cola, 1));

		worker.deliverDue();
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.PAPER_END);

		// The printer accepts connections, but the ticket still waits, so the idle check must not report "ready".
		clock.advance(Duration.ofSeconds(11));
		monitor.checkDueNow();
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.PAPER_END);

		bar.paperEnd(false);
		clock.advance(Duration.ofSeconds(16));
		worker.deliverDue();
		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.READY);
	}

	@Test
	void inactivePrinterIsNotChecked() throws Exception {
		long id = printerRepository.insert("Spare", OFFLINE, "local_printer", ItemType.FOOD, false);

		monitor.checkDueNow();

		assertThat(monitor.status(id).state()).isEqualTo(PrinterState.UNKNOWN);
	}

	private void checkEverySecondFor(Duration duration) {
		for (long s = 0; s < duration.toSeconds(); s++) {
			clock.advance(Duration.ofSeconds(1));
			monitor.checkDueNow();
		}
	}

}
