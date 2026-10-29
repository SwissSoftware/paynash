package ch.paynash.pos.printing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Delivers queued tickets (design D3). Each printer is served by one task at a time and only its oldest pending job
 * is sent, so tickets arrive in order and a stuck printer does not hold up the other one. Failures where nothing can
 * have printed are retried with growing pauses for at least the retry window; anything else is marked failed at
 * once, so a ticket is never printed twice automatically.
 */
@Component
public class PrintWorker {

	private static final Logger log = LoggerFactory.getLogger(PrintWorker.class);

	private static final Duration MAX_PAUSE = Duration.ofSeconds(16);

	private final PrintJobRepository jobs;

	private final PrinterRepository printers;

	private final EposClient epos;

	private final Clock clock;

	private final Duration retryWindow;

	private final Set<Long> busy = ConcurrentHashMap.newKeySet();

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public PrintWorker(PrintJobRepository jobs, PrinterRepository printers, EposClient epos, Clock clock,
			@Value("${pos.printing.retry-window:60s}") Duration retryWindow) {
		this.jobs = jobs;
		this.printers = printers;
		this.epos = epos;
		this.clock = clock;
		this.retryWindow = retryWindow;
	}

	/** Starts a delivery task for every printer that has a due job and none running. */
	public void dispatch() {
		for (long printerId : jobs.findPrintersWithDueJobs(clock.instant())) {
			if (busy.add(printerId)) {
				executor.submit(() -> {
					try {
						drain(printerId);
					}
					catch (RuntimeException ex) {
						log.error("Printing to printer {} failed unexpectedly", printerId, ex);
					}
					finally {
						busy.remove(printerId);
					}
				});
			}
		}
	}

	/** Delivers all due jobs now, in the calling thread. */
	public void deliverDue() {
		jobs.findPrintersWithDueJobs(clock.instant()).forEach(this::drain);
	}

	/** Sends this printer's jobs in order until none is left or the oldest has to wait for its next attempt. */
	void drain(long printerId) {
		while (true) {
			PrintJob job = jobs.findNextPending(printerId).orElse(null);
			Instant now = clock.instant();
			if (job == null || (job.nextAttemptAt() != null && job.nextAttemptAt().isAfter(now))) {
				return;
			}
			Printer printer = printers.findById(printerId).orElse(null);
			if (printer == null) {
				jobs.markFailed(job.id(), PrintJobs.NO_PRINTER);
				continue;
			}
			jobs.startAttempt(job.id(), now);
			Instant firstAttempt = job.firstAttemptAt() != null ? job.firstAttemptAt() : now;
			EposClient.Result result = epos.print(printer, Ticket.decode(job.content()));
			Instant done = clock.instant();
			switch (result) {
				case EposClient.Result.Printed p -> jobs.markPrinted(job.id(), done);
				case EposClient.Result.Failed f when f.retryable()
						&& Duration.between(firstAttempt, done).compareTo(retryWindow) < 0 -> {
					jobs.retryAt(job.id(), f.error(), done.plus(pause(job.attempts() + 1)));
					return;
				}
				case EposClient.Result.Failed f -> {
					log.warn("Ticket {} for order {} failed on {}: {}", job.id(), job.orderId(), printer.name(),
							f.error());
					jobs.markFailed(job.id(), f.error());
				}
			}
		}
	}

	/** 2, 4, 8, 16, 16, ... seconds after the given attempt. */
	static Duration pause(int attempt) {
		Duration pause = Duration.ofSeconds(1L << Math.min(attempt, 4));
		return pause.compareTo(MAX_PAUSE) > 0 ? MAX_PAUSE : pause;
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}

}
