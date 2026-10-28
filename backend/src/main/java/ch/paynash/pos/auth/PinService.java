package ch.paynash.pos.auth;

import java.util.regex.Pattern;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/** Validates and hashes staff PINs (design D7: Argon2id). */
@Service
public class PinService {

	private static final Pattern PIN_FORMAT = Pattern.compile("\\d{4,8}");

	private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

	/** Hash of a fixed PIN, checked against for unknown usernames so timing does not reveal them. */
	private final String dummyHash = encoder.encode("00000000");

	public static boolean isValidFormat(String pin) {
		return pin != null && PIN_FORMAT.matcher(pin).matches();
	}

	public String hash(String pin) {
		if (!isValidFormat(pin)) {
			throw new IllegalArgumentException("PIN must be 4 to 8 digits");
		}
		return encoder.encode(pin);
	}

	public boolean matches(String pin, String hash) {
		return pin != null && encoder.matches(pin, hash);
	}

	public void burnTimeForUnknownUser(String pin) {
		encoder.matches(pin == null ? "" : pin, dummyHash);
	}

}
