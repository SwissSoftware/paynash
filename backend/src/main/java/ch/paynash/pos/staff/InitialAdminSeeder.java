package ch.paynash.pos.staff;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ch.paynash.pos.auth.PinService;

/**
 * On an empty database, creates the first admin with a random PIN and prints it once to the log. The admin should
 * change it via "reset PIN" right after the first login.
 */
@Component
public class InitialAdminSeeder implements ApplicationRunner {

	static final String USERNAME = "admin";

	private static final Logger log = LoggerFactory.getLogger(InitialAdminSeeder.class);

	private final StaffRepository staff;

	private final PinService pins;

	public InitialAdminSeeder(StaffRepository staff, PinService pins) {
		this.staff = staff;
		this.pins = pins;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!staff.findAll().isEmpty()) {
			return;
		}
		String pin = String.format("%06d", new SecureRandom().nextInt(1_000_000));
		staff.insert(USERNAME, "Administrator", Role.ADMIN, pins.hash(pin));
		log.warn("""

				==========================================================
				 First admin created. Username: {}  One-time PIN: {}
				 Log in and set a new PIN. This PIN is not shown again.
				==========================================================""", USERNAME, pin);
	}

}
