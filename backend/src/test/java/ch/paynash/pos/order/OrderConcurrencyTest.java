package ch.paynash.pos.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.menu.ItemType;
import ch.paynash.pos.staff.Role;

/** Several devices working on the same order at the same time (spec order-taking, "Concurrent editing"). */
class OrderConcurrencyTest extends OrderTestSupport {

	private static final int DEVICES = 8;

	@Autowired
	OrderService service;

	private final ExecutorService pool = Executors.newFixedThreadPool(DEVICES);

	private AuthenticatedStaff anna;

	private AuthenticatedStaff ben;

	private long cola;

	private long pizza;

	@BeforeEach
	void setUp() {
		anna = staff("anna");
		ben = staff("ben");
		cola = item(category("Drinks"), "Cola", ItemType.DRINK, 450);
		pizza = item(category("Pizzas"), "Margherita", ItemType.FOOD, 1800);
	}

	@AfterEach
	void tearDown() {
		pool.shutdownNow();
	}

	@Test
	void twoWaitersAddingAtTheSameTimeKeepBothLines() throws Exception {
		long order = service.open("5", anna).detail().order().id();

		runTogether(List.of(() -> service.addLine(order, cola, 1, null, anna),
				() -> service.addLine(order, pizza, 1, null, ben)));

		var detail = service.detail(order);
		assertThat(detail.lines()).extracting(OrderLine::name).containsExactlyInAnyOrder("Cola", "Margherita");
		assertThat(detail.order().version()).isEqualTo(2);
	}

	@Test
	void manyParallelAdditionsAreAllKept() throws Exception {
		long order = service.open("5", anna).detail().order().id();
		List<Callable<Object>> adds = new ArrayList<>();
		for (int i = 0; i < DEVICES; i++) {
			adds.add(() -> service.addLine(order, cola, 1, null, anna));
		}

		runTogether(adds);

		assertThat(service.detail(order).lines()).hasSize(DEVICES);
	}

	@Test
	void parallelSendsCreateOneRound() throws Exception {
		long order = service.open("5", anna).detail().order().id();
		service.addLine(order, cola, 2, null, anna);
		service.addLine(order, pizza, 1, null, anna);
		List<Callable<Object>> sends = new ArrayList<>();
		for (int i = 0; i < DEVICES; i++) {
			var waiter = i % 2 == 0 ? anna : ben;
			sends.add(() -> service.send(order, waiter));
		}

		runTogether(sends);

		assertThat(service.detail(order).rounds()).hasSize(1);
		assertThat(printing.rounds).singleElement().satisfies(r -> assertThat(r.lines()).hasSize(2));
	}

	@Test
	void parallelOpeningOfOneTableCreatesOneOrder() throws Exception {
		List<Callable<Object>> opens = new ArrayList<>();
		for (int i = 0; i < DEVICES; i++) {
			var waiter = i % 2 == 0 ? anna : ben;
			opens.add(() -> service.open("7", waiter).detail().order().id());
		}

		List<Object> ids = runTogether(opens);

		assertThat(ids).containsOnly(ids.get(0));
		assertThat(jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void lineAddedWhileVoidingIsEitherKeptOrRejected() throws Exception {
		long order = service.open("5", anna).detail().order().id();
		var admin = new AuthenticatedStaff(createStaff("boss", Role.ADMIN, "1111"), "boss", "Boss", Role.ADMIN, "t");

		List<Object> results = runTogether(List.of(() -> service.voidOrder(order, "test", null, admin),
				() -> service.addLine(order, cola, 1, null, anna)), true);

		var detail = service.detail(order);
		assertThat(detail.order().status()).isEqualTo(OrderStatus.VOIDED);
		boolean addRejected = results.get(1) instanceof ApiException;
		// Either the line made it in before the void, or it was rejected because the order was already voided.
		assertThat(detail.lines()).hasSize(addRejected ? 0 : 1);
	}

	private AuthenticatedStaff staff(String name) {
		long id = createStaff(name, Role.WAITER, "4711");
		return new AuthenticatedStaff(id, name, name, Role.WAITER, "t");
	}

	private List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
		return runTogether(tasks, false);
	}

	/** Starts all tasks at the same moment. With {@code collectApiErrors}, an ApiException becomes the result. */
	private List<Object> runTogether(List<Callable<Object>> tasks, boolean collectApiErrors) throws Exception {
		var start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		for (Callable<Object> task : tasks) {
			futures.add(pool.submit(() -> {
				start.await();
				try {
					return task.call();
				}
				catch (ApiException ex) {
					if (collectApiErrors) {
						return ex;
					}
					throw ex;
				}
			}));
		}
		start.countDown();
		List<Object> results = new ArrayList<>();
		for (Future<Object> f : futures) {
			results.add(f.get());
		}
		return results;
	}

}
