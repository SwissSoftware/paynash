package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.staff.Role;

/** Invoice numbers have no gaps and no duplicates under load (spec invoicing, "Invoice numbers"; design D5). */
class InvoiceNumberConcurrencyTest extends InvoiceTestSupport {

	@Autowired
	InvoiceService invoices;

	@RepeatedTest(10)
	void parallelIssuesGetConsecutiveNumbers() throws Exception {
		clock.set("2026-11-13T10:00:00Z");
		staff = login("anna", "4711");
		List<Long> orders = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			orders.add(confirm(laura, line(facial, 1)));
		}
		var anna = annaPrincipal();
		var start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (long order : orders) {
				Callable<String> issue = () -> {
					start.await();
					return invoices.issue(order, anna).number();
				};
				results.add(pool.submit(issue));
			}
			start.countDown();
			List<String> numbers = new ArrayList<>();
			for (var f : results) {
				numbers.add(f.get());
			}
			assertThat(numbers).containsExactlyInAnyOrderElementsOf(
					IntStream.rangeClosed(1, 20).mapToObj(n -> "2026-%04d".formatted(n)).toList());
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void aFailedIssueInTheMiddleLeavesNoGap() throws Exception {
		clock.set("2026-11-13T10:00:00Z");
		staff = login("anna", "4711");
		var anna = annaPrincipal();
		long first = confirm(laura, line(facial, 1));
		long voided = confirm(laura, line(facial, 1));
		long third = confirm(laura, line(facial, 1));
		boss = login("boss", "1111");
		postJson(boss, "/admin/orders/" + voided + "/void", json("reason", "test"));

		assertThat(invoices.issue(first, anna).number()).isEqualTo("2026-0001");
		try {
			invoices.issue(voided, anna);
		}
		catch (ApiException expected) {
			// the voided order cannot be invoiced
		}
		// Force a failure after the number was taken: a temporary constraint rejects the insert of a 0.07 invoice.
		jdbc.sql("ALTER TABLE invoice ADD CONSTRAINT test_no_total_7 CHECK (total_rappen <> 7)").update();
		try {
			long seven = menu.insertItem(menu.insertCategory("Test"), "Seven", ItemType.SERVICE, 7, null, true, null,
					81);
			long failing = confirm(laura, line(seven, 1));
			try {
				invoices.issue(failing, anna);
			}
			catch (RuntimeException expected) {
				// rolled back after the counter was incremented
			}
		}
		finally {
			jdbc.sql("ALTER TABLE invoice DROP CONSTRAINT test_no_total_7").update();
		}
		assertThat(invoices.issue(third, anna).number()).isEqualTo("2026-0002");
	}

	private AuthenticatedStaff annaPrincipal() {
		long id = staffRepository.findByUsername("anna").orElseThrow().id();
		return new AuthenticatedStaff(id, "anna", "anna Display", Role.WAITER, "test");
	}

}
