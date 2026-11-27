package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

/**
 * Spec ticket-printing, "Route lines by category" and "Cancellation tickets": a category may go to several printers,
 * and STORNO tickets follow where a line was printed (change printer-category-routing, design D2-D4).
 */
class CategoryRoutingTest extends PrintingTestSupport {

	private String waiter;

	private String admin;

	private long kitchen;

	private long pass;

	private long bar;

	private long pizza;

	private long tiramisu;

	private long cola;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		admin = adminToken();
		pizza = item("Pizza", "Margherita", ItemType.FOOD);
		tiramisu = item("Dessert", "Tiramisu", ItemType.FOOD);
		cola = item("Soft drinks", "Cola", ItemType.DRINK);
		kitchen = printerRepository.insert("Kitchen", OFFLINE, "local_printer", true, false, true);
		pass = printerRepository.insert("Pass", OFFLINE, "local_printer", true, false, true);
		bar = printerRepository.insert("Bar", OFFLINE, "local_printer", true, false, true);
		assign(kitchen, "Pizza");
		assign(kitchen, "Dessert");
		assign(pass, "Pizza");
		assign(bar, "Soft drinks");
	}

	@Test
	void eachPrinterGetsOneTicketWithTheLinesOfItsCategories() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1), line(cola, 2), line(tiramisu, 1));

		assertThat(jobs(id)).extracting(PrintJob::printerName).containsExactly("Bar", "Kitchen", "Pass");
		assertThat(ticketOn(id, bar)).contains("2 x Cola").doesNotContain("Margherita");
		assertThat(ticketOn(id, kitchen)).contains("1 x Margherita").contains("1 x Tiramisu").doesNotContain("Cola");
		assertThat(ticketOn(id, pass)).contains("1 x Margherita").doesNotContain("Tiramisu");
	}

	@Test
	void cancelledLineIsCancelledOnEveryPrinterItWasPrintedOn() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1), line(cola, 1));

		cancel(id, lineId(id, "Margherita"));

		assertThat(stornos(id)).extracting(PrintJob::printerName).containsExactly("Kitchen", "Pass");
	}

	@Test
	void cancellationFollowsThePrintersAtSendTimeNotTheCurrentAssignment() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1));
		jdbc.sql("DELETE FROM printer_category WHERE category_id = (SELECT id FROM category WHERE name = 'Pizza')")
			.update();
		assign(bar, "Pizza");

		cancel(id, lineId(id, "Margherita"));

		assertThat(stornos(id)).extracting(PrintJob::printerName).containsExactly("Kitchen", "Pass");
	}

	@Test
	void lineThatWasNotPrintedGetsNoCancellationTicket() throws Exception {
		long entry = item("Entrance", "Entry fee", ItemType.FOOD);
		long id = sendOrder(waiter, "12", line(entry, 1), line(cola, 1));

		cancel(id, lineId(id, "Entry fee"));

		assertThat(stornos(id)).isEmpty();
	}

	@Test
	void voidPrintsAtMostOneCancellationTicketPerPrinter() throws Exception {
		long id = sendOrder(waiter, "12", line(pizza, 1), line(tiramisu, 1), line(cola, 1));

		postJson(admin, "/admin/orders/" + id + "/void", json("reason", "wrong table")).andExpect(status().isOk());

		assertThat(stornos(id)).extracting(PrintJob::printerName).containsExactly("Bar", "Kitchen", "Pass");
		assertThat(stornos(id).get(1).content()).contains("1 x Margherita").contains("1 x Tiramisu");
	}

	@Test
	void cancellationOnAPrinterThatIsInactiveNowFails() throws Exception {
		long id = sendOrder(waiter, "12", line(cola, 1));
		jdbc.sql("UPDATE printer SET active = false WHERE id = ?").param(bar).update();

		cancel(id, lineId(id, "Cola"));

		assertThat(stornos(id)).singleElement().satisfies(j -> {
			assertThat(j.printerId()).isEqualTo(bar);
			assertThat(j.status()).isEqualTo(PrintJob.Status.FAILED);
			assertThat(j.lastError()).isEqualTo("no active printer");
		});
	}

	private void cancel(long orderId, long lineId) throws Exception {
		postJson(admin, "/admin/orders/" + orderId + "/lines/" + lineId + "/cancel", json("reason", "mistake"))
			.andExpect(status().isOk());
	}

	private String ticketOn(long orderId, long printerId) {
		return jobs(orderId).stream()
			.filter(j -> j.printerId() == printerId && j.kind() == PrintJob.Kind.NORMAL)
			.findFirst()
			.orElseThrow()
			.content();
	}

	private java.util.List<PrintJob> stornos(long orderId) {
		return jobs(orderId).stream().filter(j -> j.kind() == PrintJob.Kind.STORNO).toList();
	}

}
