package ch.paynash.pos.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.common.ApiException;
import ch.paynash.pos.staff.Staff;
import ch.paynash.pos.staff.StaffRepository;

/** Login, logout, and session lookup (design D7). */
@Service
public class AuthService {

	static final Duration SESSION_LIFETIME = Duration.ofHours(12);

	static final int MAX_FAILED_ATTEMPTS = 5;

	static final Duration LOCKOUT = Duration.ofMinutes(5);

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final StaffRepository staff;

	private final SessionRepository sessions;

	private final PinService pins;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public AuthService(StaffRepository staff, SessionRepository sessions, PinService pins, Clock clock) {
		this.staff = staff;
		this.sessions = sessions;
		this.pins = pins;
		this.clock = clock;
	}

	public record LoginResult(String token, Instant expiresAt, Staff staff) {
	}

	/**
	 * Every failure answers with the same message, so callers cannot tell whether a username exists, is
	 * deactivated, or is locked. The failed-attempt counter must be committed even though the login fails, so
	 * {@link ApiException} does not roll back.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public LoginResult login(String username, String pin) {
		Instant now = clock.instant();
		Staff account = staff.findByUsername(username == null ? "" : username.trim()).orElse(null);
		if (account == null || !account.active() || account.isLockedAt(now)) {
			pins.burnTimeForUnknownUser(pin);
			throw invalidCredentials();
		}
		if (!pins.matches(pin, account.pinHash())) {
			if (staff.incrementFailedAttempts(account.id()) >= MAX_FAILED_ATTEMPTS) {
				staff.lock(account.id(), now.plus(LOCKOUT));
				log.warn("Account '{}' locked for {} after {} failed logins", account.username(), LOCKOUT,
						MAX_FAILED_ATTEMPTS);
			}
			throw invalidCredentials();
		}
		staff.resetFailedLogins(account.id());
		sessions.deleteExpired(now);
		String token = newToken();
		Instant expiresAt = now.plus(SESSION_LIFETIME);
		sessions.insert(hashToken(token), account.id(), now, expiresAt);
		return new LoginResult(token, expiresAt, account);
	}

	public Optional<AuthenticatedStaff> authenticate(String token) {
		return sessions.findValid(hashToken(token), clock.instant());
	}

	public void logout(AuthenticatedStaff current) {
		sessions.delete(current.tokenHash());
	}

	private String newToken() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
	}

}
