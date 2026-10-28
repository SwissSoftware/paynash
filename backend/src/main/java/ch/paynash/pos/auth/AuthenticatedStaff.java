package ch.paynash.pos.auth;

import ch.paynash.pos.staff.Role;

/** The principal of an authenticated request. */
public record AuthenticatedStaff(long id, String username, String displayName, Role role, String tokenHash) {
}
