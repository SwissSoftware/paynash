package ch.paynash.pos.settings;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.paynash.pos.common.ApiException;

/**
 * Installation mode and business details (spec venue-mode, design D1). The mode is cached for the mode gate and
 * refreshed after each committed change; this backend runs as a single instance on site (design D2 of
 * add-pos-ordering-system).
 */
@Service
public class SettingsService {

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final SettingsRepository repository;

	private final Clock clock;

	private volatile Mode cachedMode;

	public SettingsService(SettingsRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Settings get() {
		return repository.get();
	}

	public Mode mode() {
		Mode mode = cachedMode;
		if (mode == null) {
			mode = repository.get().mode();
			cachedMode = mode;
		}
		return mode;
	}

	/** Forgets the cached mode, e.g. after tests reset the settings row. */
	public void clearCache() {
		cachedMode = null;
	}

	/** The input of {@link #updateDetails}; blank strings count as not set. */
	public record DetailsInput(String businessName, String street, String postalCode, String city, String phone,
			String email, String website, String iban, String accountHolder, boolean vatEnabled, String vatUid) {
	}

	@Transactional
	public Settings updateDetails(DetailsInput in) {
		Settings current = repository.lockForUpdate();
		Map<String, String> errors = new LinkedHashMap<>();
		String name = text(in.businessName(), "businessName", 80, errors);
		String street = text(in.street(), "street", 80, errors);
		String postalCode = text(in.postalCode(), "postalCode", 10, errors);
		String city = text(in.city(), "city", 60, errors);
		String phone = text(in.phone(), "phone", 30, errors);
		String email = text(in.email(), "email", 120, errors);
		String website = text(in.website(), "website", 120, errors);
		String accountHolder = text(in.accountHolder(), "accountHolder", 80, errors);
		if (name == null) {
			errors.putIfAbsent("businessName", "Business name is required");
		}
		if (current.mode() == Mode.SALON) {
			if (street == null) {
				errors.putIfAbsent("street", "Street is required in salon mode");
			}
			if (postalCode == null) {
				errors.putIfAbsent("postalCode", "Postal code is required in salon mode");
			}
			if (city == null) {
				errors.putIfAbsent("city", "City is required in salon mode");
			}
		}
		if (email != null && !EMAIL.matcher(email).matches()) {
			errors.putIfAbsent("email", "Not a valid email address");
		}
		String iban = null;
		String ibanInput = blankToNull(in.iban());
		if (ibanInput != null) {
			iban = SwissIds.normalizeIban(ibanInput);
			if (iban == null) {
				errors.put("iban", "Not a valid Swiss or Liechtenstein IBAN");
			}
		}
		String uid = null;
		String uidInput = blankToNull(in.vatUid());
		if (uidInput != null) {
			uid = SwissIds.normalizeVatUid(uidInput);
			if (uid == null) {
				errors.put("vatUid", "Not a valid VAT number, expected CHE-123.456.789 MWST");
			}
		}
		if (in.vatEnabled() && uidInput == null) {
			errors.put("vatUid", "A VAT number is required when VAT is turned on");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
		}
		repository.updateDetails(new Settings(current.mode(), name, street, postalCode, city, phone, email, website,
				iban, accountHolder, in.vatEnabled(), uid), clock.instant());
		return repository.get();
	}

	/**
	 * Changes the mode only while no order exists (spec venue-mode, "Mode change only without orders"). The row lock
	 * waits for running order sends, which hold a shared lock on the same row.
	 */
	@Transactional
	public Settings changeMode(Mode mode) {
		Settings current = repository.lockForUpdate();
		if (current.mode() != mode) {
			if (repository.anyOrderExists()) {
				throw ApiException.conflict("The installation already has orders, so its mode can no longer change");
			}
			repository.updateMode(mode, clock.instant());
			refreshAfterCommit();
		}
		return repository.get();
	}

	/** Called by every order send before it saves the order; see {@link SettingsRepository#lockModeForShare()}. */
	public Mode lockModeForOrder() {
		return repository.lockModeForShare();
	}

	private void refreshAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					cachedMode = null;
				}
			});
		}
		else {
			cachedMode = null;
		}
	}

	private static String text(String value, String field, int max, Map<String, String> errors) {
		String v = blankToNull(value);
		if (v != null && v.length() > max) {
			errors.put(field, "At most " + max + " characters");
		}
		return v;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

}
