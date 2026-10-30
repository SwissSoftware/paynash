package ch.paynash.pos.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A clock tests can move forward. It ticks in whole microseconds like PostgreSQL timestamps; with the nanoseconds
 * Linux clocks have, a stored time would be rounded and could end up just after the moment a test moves to.
 */
public class MutableClock extends Clock {

	private Instant now = start();

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	/** Sets the time, e.g. {@code set("2026-10-29T18:30:00Z")}. */
	public void set(String instant) {
		now = Instant.parse(instant);
	}

	public void reset() {
		now = start();
	}

	private static Instant start() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	@Override
	public Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
