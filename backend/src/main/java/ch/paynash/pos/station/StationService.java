package ch.paynash.pos.station;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ch.paynash.pos.auth.AuthenticatedStaff;
import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.printing.PrinterStatusMonitor;
import ch.paynash.pos.station.StationRepository.AssignedPrinter;

/**
 * The print station (spec print-station, design D6, D8). A station only sees the printers assigned to it; any other
 * printer answers 403.
 */
@Service
public class StationService {

	private final StationRepository stations;

	private final PrinterStatusMonitor monitor;

	private final Clock clock;

	private final ZoneId zone;

	public StationService(StationRepository stations, PrinterStatusMonitor monitor, Clock clock,
			@Value("${pos.time-zone}") ZoneId zone) {
		this.stations = stations;
		this.monitor = monitor;
		this.clock = clock;
		this.zone = zone;
	}

	/** {@code oldestWaitingSeconds} is measured on the server clock, so tablets with a wrong clock still alert. */
	public record StationPrinter(AssignedPrinter printer, PrinterStatusMonitor.Status status,
			StationRepository.QueueSummary queue, Long oldestWaitingSeconds) {
	}

	public List<StationPrinter> printers(AuthenticatedStaff station) {
		Instant now = clock.instant();
		stations.touch(station.id(), now);
		return stations.assignedPrinters(station.id()).stream().map(p -> {
			StationRepository.QueueSummary queue = stations.summary(p.id());
			Long waited = queue.oldestWaitingSince() == null ? null
					: Math.max(0, Duration.between(queue.oldestWaitingSince(), now).toSeconds());
			return new StationPrinter(p, monitor.status(p.id()), queue, waited);
		}).toList();
	}

	/** Today's tickets of an assigned printer and every older one that still needs attention. */
	public List<StationRepository.StationTicket> tickets(AuthenticatedStaff station, long printerId) {
		requireAssigned(station, printerId);
		stations.touch(station.id(), clock.instant());
		LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
		return stations.tickets(printerId, today.atStartOfDay(zone).toInstant());
	}

	/** Whether the station shows this printer. */
	public boolean shows(AuthenticatedStaff station, long printerId) {
		return stations.assignedPrinters(station.id()).stream().anyMatch(p -> p.id() == printerId);
	}

	/** Rejects printers the station is not assigned to (spec staff-access, "Station limited to its printers"). */
	public void requireAssigned(AuthenticatedStaff station, long printerId) {
		if (!shows(station, printerId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "This station does not show that printer");
		}
	}

}
