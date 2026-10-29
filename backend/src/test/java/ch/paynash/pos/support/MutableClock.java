package ch.paynash.pos.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward. */
public class MutableClock extends Clock {

	private Instant now = Instant.now();

	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	/** Sets the time, e.g. {@code set("2026-10-29T18:30:00Z")}. */
	public void set(String instant) {
		now = Instant.parse(instant);
	}

	public void reset() {
		now = Instant.now();
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
