package ch.paynash.pos.printing;

import java.time.Instant;

import ch.paynash.pos.menu.ItemType;

/** One ticket in the outbox (design D3). {@code printerId} is null when no printer was configured. */
public record PrintJob(long id, long orderId, ItemType itemType, Long printerId, String printerName, Kind kind,
		String content, Status status, int attempts, String lastError, Instant firstAttemptAt, Instant nextAttemptAt,
		Long reprintOf, boolean reprinted, Instant createdAt) {

	public enum Kind {

		NORMAL, STORNO, REPRINT;

		public String dbValue() {
			return name().toLowerCase();
		}

		static Kind fromDb(String value) {
			return valueOf(value.toUpperCase());
		}

	}

	public enum Status {

		PENDING, PRINTED, FAILED;

		public String dbValue() {
			return name().toLowerCase();
		}

		static Status fromDb(String value) {
			return valueOf(value.toUpperCase());
		}

	}

}
