package ch.paynash.pos.printing;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * One ticket in the outbox (design D3, and D1 of harden-printing-for-events). {@code printerId} and {@code ticketNo}
 * are null when no printer was configured. {@code waitingReason} says why a pending ticket waits; {@code lastError} is
 * the error of an unclear or failed ticket. {@code resolution} is set once station staff resolved an unclear ticket,
 * or when it was reprinted. A job prints either a ticket of {@code orderId} or the receipt of {@code invoiceId}
 * (add-salon-mode design D8); the other one is null.
 */
public record PrintJob(long id, Long orderId, Long invoiceId, PrintPurpose purpose, Long printerId, String printerName,
		Kind kind, String content, Status status, int attempts, String waitingReason, String lastError,
		Instant firstAttemptAt, Instant nextAttemptAt, Long reprintOf, boolean reprinted, Integer ticketNo,
		int lineCount, Resolution resolution, Instant resolvedAt, Instant createdAt) {

	public enum Kind {

		NORMAL, STORNO, REPRINT,
		/** A receipt printed again, marked "KOPIE". */
		COPY;

		public String dbValue() {
			return name().toLowerCase();
		}

		static Kind fromDb(String value) {
			return valueOf(value.toUpperCase());
		}

	}

	public enum Status {

		PENDING, PRINTING, PRINTED, UNCLEAR, FAILED;

		public String dbValue() {
			return name().toLowerCase();
		}

		static Status fromDb(String value) {
			return valueOf(value.toUpperCase());
		}

	}

	public enum Resolution {

		/** Staff found the ticket in the pile. */
		PRINTED,
		/** Staff had it printed again. */
		REPRINT;

		@JsonValue
		public String dbValue() {
			return name().toLowerCase();
		}

		@JsonCreator
		public static Resolution fromDb(String value) {
			return value == null ? null : valueOf(value.toUpperCase());
		}

	}

	/** Waiting or being printed, so it must not be reprinted yet. */
	public boolean inProgress() {
		return status == Status.PENDING || status == Status.PRINTING;
	}

	/** An unclear ticket that still needs a decision from station staff. */
	public boolean needsResolution() {
		return status == Status.UNCLEAR && resolution == null;
	}

}
