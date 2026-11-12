package ch.paynash.pos.printing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Looks for due tickets and printers due for a status check every second. Tests switch it off and call the worker and
 * the monitor directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pos.printing.worker-enabled", havingValue = "true", matchIfMissing = true)
class PrintScheduler {

	private final PrintWorker worker;

	private final PrinterStatusMonitor monitor;

	PrintScheduler(PrintWorker worker, PrinterStatusMonitor monitor) {
		this.worker = worker;
		this.monitor = monitor;
	}

	@Scheduled(fixedDelayString = "${pos.printing.poll-interval:1s}")
	void tick() {
		worker.dispatch();
		monitor.checkDue();
	}

}
