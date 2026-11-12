package ch.paynash.pos.printing;

import com.fasterxml.jackson.annotation.JsonValue;

/** A printer's last known state (design D4). {@link #UNKNOWN} until it was checked or printed for the first time. */
public enum PrinterState {

	READY, OFFLINE, PAPER_END, COVER_OPEN, PAPER_NEAR_END, UNKNOWN;

	@JsonValue
	public String apiValue() {
		return name().toLowerCase().replace('_', ' ');
	}

	public boolean ready() {
		return this == READY;
	}

	/** The state a delivery result tells about the printer, or null when it tells nothing reliable. */
	static PrinterState from(EposClient.Result result) {
		return switch (result) {
			case EposClient.Result.Printed p -> READY;
			case EposClient.Result.NotPrinted n -> switch (n.error()) {
				case "paper end" -> PAPER_END;
				case "cover open" -> COVER_OPEN;
				default -> OFFLINE;
			};
			case EposClient.Result.Unclear u -> null;
			case EposClient.Result.Failed f -> null;
		};
	}

}
