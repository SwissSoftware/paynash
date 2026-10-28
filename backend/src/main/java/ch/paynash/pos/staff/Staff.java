package ch.paynash.pos.staff;

import java.time.Instant;

public record Staff(long id, String username, String displayName, Role role, boolean active, String pinHash,
		int failedAttempts, Instant lockedUntil) {

	public boolean isLockedAt(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

}
