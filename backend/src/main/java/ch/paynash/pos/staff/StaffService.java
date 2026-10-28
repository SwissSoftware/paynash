package ch.paynash.pos.staff;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.paynash.pos.auth.PinService;
import ch.paynash.pos.auth.SessionRepository;
import ch.paynash.pos.common.ApiException;

@Service
public class StaffService {

	private final StaffRepository staff;

	private final SessionRepository sessions;

	private final PinService pins;

	public StaffService(StaffRepository staff, SessionRepository sessions, PinService pins) {
		this.staff = staff;
		this.sessions = sessions;
		this.pins = pins;
	}

	public List<Staff> list() {
		return staff.findAll();
	}

	@Transactional
	public Staff create(String username, String displayName, Role role, String pin) {
		String name = username.trim();
		if (staff.existsByUsername(name)) {
			throw usernameTaken();
		}
		try {
			long id = staff.insert(name, displayName.trim(), role, pins.hash(pin));
			return get(id);
		}
		catch (DuplicateKeyException ex) {
			throw usernameTaken();
		}
	}

	/** Deactivating an account ends all its sessions immediately (design D7). */
	@Transactional
	public Staff update(long id, String displayName, Role role, boolean active) {
		Staff current = get(id);
		boolean losesAdmin = current.role() == Role.ADMIN && current.active() && (role != Role.ADMIN || !active);
		if (losesAdmin && staff.countActiveAdmins() <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "The last active admin cannot be deactivated or demoted");
		}
		staff.update(id, displayName.trim(), role, active);
		if (!active) {
			sessions.deleteAllForStaff(id);
		}
		return get(id);
	}

	@Transactional
	public void resetPin(long id, String pin) {
		get(id);
		staff.updatePin(id, pins.hash(pin));
	}

	private Staff get(long id) {
		return staff.findById(id).orElseThrow(() -> ApiException.notFound("Staff member"));
	}

	private static ApiException usernameTaken() {
		return ApiException.validation("username", "Username already exists");
	}

}
