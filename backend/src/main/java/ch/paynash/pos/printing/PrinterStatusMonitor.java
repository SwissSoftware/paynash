package ch.paynash.pos.printing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Keeps each printer's last known state in memory (design D4). The print worker reports what every delivery attempt
 * showed; while a printer has nothing to print, it is checked every {@code pos.printing.status-interval}, so an
 * offline printer is noticed before the next ticket arrives. The idle check can only tell ready from offline.
 */
@Component
public class PrinterStatusMonitor {

	private static final Logger log = LoggerFactory.getLogger(PrinterStatusMonitor.class);

	public record Status(PrinterState state, Instant checkedAt) {
	}

	private static final Status UNKNOWN = new Status(PrinterState.UNKNOWN, null);

	private final PrinterRepository printers;

	private final PrintJobRepository jobs;

	private final EposClient epos;

	private final Clock clock;

	private final Duration interval;

	private final Map<Long, Status> statuses = new ConcurrentHashMap<>();

	private final Set<Long> busy = ConcurrentHashMap.newKeySet();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public PrinterStatusMonitor(PrinterRepository printers, PrintJobRepository jobs, EposClient epos, Clock clock,
			@Value("${pos.printing.status-interval:10s}") Duration interval) {
		this.printers = printers;
		this.jobs = jobs;
		this.epos = epos;
		this.clock = clock;
		this.interval = interval;
	}

	public Status status(long printerId) {
		return statuses.getOrDefault(printerId, UNKNOWN);
	}

	/** Called by the worker after each delivery attempt. */
	void reported(long printerId, EposClient.Result result) {
		PrinterState state = PrinterState.from(result);
		if (state != null) {
			update(printerId, state);
		}
	}

	/** Starts a check for every active, idle printer whose last check is older than the interval. */
	public void checkDue() {
		for (Printer printer : dueForCheck()) {
			if (busy.add(printer.id())) {
				executor.submit(() -> {
					try {
						check(printer);
					}
					finally {
						busy.remove(printer.id());
					}
				});
			}
		}
	}

	/** Checks all due printers now, in the calling thread. */
	public void checkDueNow() {
		dueForCheck().forEach(this::check);
	}

	private java.util.List<Printer> dueForCheck() {
		Instant now = clock.instant();
		return printers.findAll()
			.stream()
			.filter(Printer::active)
			.filter(p -> {
				Instant last = status(p.id()).checkedAt();
				return last == null || !last.plus(interval).isAfter(now);
			})
			.filter(p -> !jobs.hasJobsInProgress(p.id()))
			.toList();
	}

	private void check(Printer printer) {
		PrinterState state = epos.reachable(printer) ? PrinterState.READY : PrinterState.OFFLINE;
		update(printer.id(), state);
	}

	private void update(long printerId, PrinterState state) {
		Status previous = statuses.put(printerId, new Status(state, clock.instant()));
		if (previous != null && previous.state() != state) {
			log.info("Printer {} is now {}", printerId, state.apiValue());
		}
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}

}
