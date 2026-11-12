package ch.paynash.pos.printing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.paynash.pos.menu.ItemType;

/** Running ticket numbers per printer (spec ticket-printing, "Running ticket number per printer"). */
class TicketNumberTest extends PrintingTestSupport {

	private String waiter;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() throws Exception {
		waiter = waiterToken();
		printer("Bar", OFFLINE, ItemType.DRINK);
		printer("Kitchen", OFFLINE, ItemType.FOOD);
		cola = item("Drinks", "Cola", ItemType.DRINK);
		pizza = item("Pizzas", "Margherita", ItemType.FOOD);
	}

	@Test
	void consecutiveDrinkTicketsGetConsecutiveBarNumbersAndLeaveTheKitchenCounterAlone() throws Exception {
		long first = sendOrder(waiter, "1", line(cola, 1));
		long second = sendOrder(waiter, "2", line(cola, 1));
		long third = sendOrder(waiter, "3", line(cola, 1));

		assertThat(List.of(first, second, third)).extracting(o -> job(o, ItemType.DRINK).ticketNo())
			.containsExactly(1, 2, 3);
		assertThat(job(third, ItemType.DRINK).content()).startsWith("# BAR 0003\n");
		long food = sendOrder(waiter, "4", line(pizza, 1));
		assertThat(job(food, ItemType.FOOD).ticketNo()).isEqualTo(1);
	}

	@Test
	void ticketWithoutPrinterHasNoNumberAndItsReprintGetsTheNextOne() throws Exception {
		jdbc.sql("UPDATE printer SET active = false WHERE item_type = 'food'").update();
		long id = sendOrder(waiter, "1", line(pizza, 1));
		PrintJob failed = job(id, ItemType.FOOD);
		assertThat(failed.ticketNo()).isNull();
		jdbc.sql("UPDATE printer SET active = true WHERE item_type = 'food'").update();

		postJson(waiter, "/orders/" + id + "/tickets/" + failed.id() + "/reprint", "");

		PrintJob reprint = jobs(id).getLast();
		assertThat(reprint.ticketNo()).isEqualTo(1);
		assertThat(reprint.content()).startsWith("# KITCHEN 0001\n# *** REPRINT ***\n");
	}

	@Test
	void concurrentOrdersGetDistinctConsecutiveNumbersInQueueOrder() throws Exception {
		int orders = 12;
		List<Callable<Long>> sends = IntStream.rangeClosed(1, orders)
			.<Callable<Long>>mapToObj(i -> () -> sendOrder(waiter, "T" + i, line(cola, 1)))
			.toList();
		List<Long> ids = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
			for (Future<Long> f : pool.invokeAll(sends)) {
				ids.add(f.get());
			}
		}

		List<PrintJob> barJobs = ids.stream()
			.map(o -> job(o, ItemType.DRINK))
			.sorted(Comparator.comparingLong(PrintJob::id))
			.toList();
		assertThat(barJobs).extracting(PrintJob::ticketNo)
			.containsExactlyElementsOf(IntStream.rangeClosed(1, orders).boxed().toList());
	}

}
