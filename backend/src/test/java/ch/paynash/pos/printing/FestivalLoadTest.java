package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.staff.Role;
import ch.paynash.pos.support.FakeEposPrinter;
import ch.paynash.pos.support.FakeEposPrinter.Fault;

/**
 * Festival load with failures (design D11): 15 waiters send orders at the same time to two printers that drop
 * connections, answer too slowly, and run out of paper. Afterwards every ticket is printed or unclear (shown at the
 * station), none was delivered twice, and both queues recovered.
 */
@TestPropertySource(properties = "pos.printing.epos-timeout=300ms")
class FestivalLoadTest extends PrintingTestSupport {

	private static final int WAITERS = 15;

	private static final int ORDERS_PER_WAITER = 8;

	private static final Pattern LABEL = Pattern.compile("(BAR|KITCHEN) \\d{4}");

	@Test
	void fifteenWaitersAtPeakWithFaultyPrintersLoseAndDuplicateNothing() throws Exception {
		var bar = fakePrinter();
		var kitchen = fakePrinter();
		printer("Bar", bar.address(), ItemType.DRINK);
		printer("Kitchen", kitchen.address(), ItemType.FOOD);
		long beer = item("Drinks", "Beer", ItemType.DRINK);
		long pizza = item("Pizzas", "Margherita", ItemType.FOOD);
		bar.faults(n -> n % 13 == 0 ? Fault.DROP_AFTER_READ : Fault.NONE);
		kitchen.faults(n -> n % 11 == 0 ? Fault.DROP_BEFORE_READ : n % 17 == 0 ? Fault.DROP_AFTER_READ : Fault.NONE);
		kitchen.answer(new FakeEposPrinter.Answer(200, FakeEposPrinter.SUCCESS, Duration.ofSeconds(1)));
		List<String> tokens = new ArrayList<>();
		for (int w = 1; w <= WAITERS; w++) {
			createStaff("waiter" + w, Role.WAITER, "4711");
			tokens.add(login("waiter" + w, "4711"));
		}

		AtomicBoolean running = new AtomicBoolean(true);
		List<Throwable> workerErrors = new CopyOnWriteArrayList<>();
		Thread workerThread = Thread.ofPlatform().start(() -> {
			while (running.get()) {
				try {
					worker.deliverDue();
					clock.advance(Duration.ofSeconds(1));
					Thread.sleep(2);
				}
				catch (InterruptedException ex) {
					return;
				}
				catch (Throwable ex) {
					workerErrors.add(ex);
				}
			}
		});

		List<Long> orders = new ArrayList<>();
		try (ExecutorService waiters = Executors.newFixedThreadPool(WAITERS)) {
			List<Callable<List<Long>>> shifts = IntStream.range(0, WAITERS).<Callable<List<Long>>>mapToObj(w -> () -> {
				List<Long> sent = new ArrayList<>();
				for (int i = 0; i < ORDERS_PER_WAITER; i++) {
					String table = "T" + (w * ORDERS_PER_WAITER + i);
					sent.add(switch ((w + i) % 3) {
						case 0 -> sendOrder(tokens.get(w), table, line(beer, 2));
						case 1 -> sendOrder(tokens.get(w), table, line(pizza, 1));
						default -> sendOrder(tokens.get(w), table, line(beer, 1), line(pizza, 2, "no onions"));
					});
					Thread.sleep(5);
				}
				return sent;
			}).toList();
			List<Future<List<Long>>> results = waiters.invokeAll(shifts.subList(0, WAITERS / 2));
			// The bar runs out of paper halfway through the rush.
			bar.paperEnd(true);
			results = new ArrayList<>(results);
			results.addAll(waiters.invokeAll(shifts.subList(WAITERS / 2, WAITERS)));
			for (Future<List<Long>> f : results) {
				orders.addAll(f.get());
			}
		}
		// Keep the paper out until the bar has turned tickets away a few times, however busy the worker was.
		waitUntil(() -> bar.requests.size() - bar.possiblyPrintedTickets().size() >= 3, "paper end answers");
		bar.paperEnd(false);
		bar.fault(Fault.NONE);
		kitchen.fault(Fault.NONE);
		waitUntilQueuesAreEmpty();
		running.set(false);
		workerThread.join(10_000);

		assertThat(workerErrors).isEmpty();
		assertThat(orders).hasSize(WAITERS * ORDERS_PER_WAITER).doesNotHaveDuplicates();

		List<PrintJob> tickets = new ArrayList<>();
		for (long orderId : orders) {
			List<PrintJob> ofOrder = jobs(orderId);
			assertThat(ofOrder).as("one ticket per printer for order %d", orderId)
				.extracting(PrintJob::purpose)
				.doesNotHaveDuplicates();
			assertThat(ofOrder).allSatisfy(j -> {
				assertThat(j.kind()).isEqualTo(PrintJob.Kind.NORMAL);
				assertThat(j.status()).as("ticket %s", j.ticketNo()).isIn(PrintJob.Status.PRINTED, PrintJob.Status.UNCLEAR);
			});
			tickets.addAll(ofOrder);
		}
		assertThat(tickets).hasSize(orders.size() + orders.size() / 3);

		Map<String, Long> maybePrinted = countByLabel(bar.possiblyPrintedTickets(), kitchen.possiblyPrintedTickets());
		Map<String, Long> printed = countByLabel(bar.printedTickets(), kitchen.printedTickets());
		assertThat(maybePrinted.values()).as("no ticket reached a printer twice").allMatch(n -> n == 1);
		for (PrintJob t : tickets) {
			String label = Ticket.label(t.printerName(), t.ticketNo());
			if (t.status() == PrintJob.Status.PRINTED) {
				assertThat(printed.get(label)).as(label).isEqualTo(1L);
			}
			else {
				assertThat(t.needsResolution()).as(label).isTrue();
			}
		}
		assertThat(tickets).filteredOn(t -> t.status() == PrintJob.Status.UNCLEAR).as("faults were injected").isNotEmpty();
		assertThat(bar.requests.size()).as("the bar was retried during the paper end")
			.isGreaterThan(bar.possiblyPrintedTickets().size());

		for (PrintPurpose type : List.of(PrintPurpose.DRINK, PrintPurpose.FOOD)) {
			assertThat(tickets).filteredOn(t -> t.purpose() == type)
				.extracting(PrintJob::ticketNo)
				.containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1,
						(int) tickets.stream().filter(t -> t.purpose() == type).count()).boxed().toList());
		}
	}

	private void waitUntilQueuesAreEmpty() throws InterruptedException {
		waitUntil(() -> jdbc.sql("SELECT count(*) FROM print_job WHERE status IN ('pending', 'printing')")
			.query(Long.class)
			.single() == 0, "queues recovered");
	}

	private static void waitUntil(java.util.function.BooleanSupplier condition, String what)
			throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("timed out waiting for: " + what);
	}

	@SafeVarargs
	private static Map<String, Long> countByLabel(Collection<String>... requests) {
		return java.util.Arrays.stream(requests)
			.flatMap(Collection::stream)
			.map(FestivalLoadTest::label)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	private static String label(String request) {
		Matcher m = LABEL.matcher(request);
		if (!m.find()) {
			throw new AssertionError("no ticket number in " + request);
		}
		return m.group();
	}

}
