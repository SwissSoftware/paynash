package ch.paynash.pos.settings;

import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.paynash.pos.common.ApiException;

/**
 * Features, template, terms (spec business-features) and business details (spec venue-mode). The features are cached
 * for the feature gate and refreshed after each committed change; this backend runs as a single instance on site
 * (design D2 of add-pos-ordering-system).
 */
@Service
public class SettingsService {

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private final SettingsRepository repository;

	private final Clock clock;

	private volatile Set<Feature> cachedFeatures;

	public SettingsService(SettingsRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Settings get() {
		return repository.get();
	}

	/** The enabled features, read-only. */
	public Set<Feature> features() {
		Set<Feature> features = cachedFeatures;
		if (features == null) {
			features = Collections.unmodifiableSet(EnumSet.copyOf(withNone(repository.get().features())));
			cachedFeatures = features;
		}
		return features;
	}

	public boolean enabled(Feature feature) {
		return features().contains(feature);
	}

	/** Rejects the request with 409 {@code feature_disabled} unless the feature is on (design D2). */
	public void require(Feature feature) {
		if (!enabled(feature)) {
			throw FeatureGate.disabled(feature);
		}
	}

	/** Forgets the cached features, e.g. after tests reset the settings row. */
	public void clearCache() {
		cachedFeatures = null;
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
		if (current.has(Feature.INVOICES)) {
			if (street == null) {
				errors.putIfAbsent("street", "Street is required while invoices are on");
			}
			if (postalCode == null) {
				errors.putIfAbsent("postalCode", "Postal code is required while invoices are on");
			}
			if (city == null) {
				errors.putIfAbsent("city", "City is required while invoices are on");
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
		repository.updateDetails(new Settings(current.features(), current.template(), current.terms(), name, street,
				postalCode, city, phone, email, website, iban, accountHolder, in.vatEnabled(), uid), clock.instant());
		return repository.get();
	}

	/**
	 * Switches features on and off (spec business-features). Rejects sets that break a dependency. Any change marks the
	 * template as {@link Template#CUSTOM}. Existing data is never touched. The row lock waits for running order sends,
	 * which hold a shared lock on the same row (design D3).
	 */
	@Transactional
	public Settings updateFeatures(Set<Feature> requested) {
		Settings current = repository.lockForUpdate();
		Set<Feature> features = EnumSet.copyOf(withNone(requested));
		checkDependencies(current.features(), features);
		if (!features.equals(current.features())) {
			repository.updateFeaturesAndTerms(features, Template.CUSTOM, current.terms(), clock.instant());
			refreshAfterCommit();
		}
		return repository.get();
	}

	/** Renames the three terms (spec business-features, "Configurable terms"). */
	@Transactional
	public Settings updateTerms(Terms requested) {
		Settings current = repository.lockForUpdate();
		Map<String, String> errors = new LinkedHashMap<>();
		Terms terms = new Terms(term(requested.waiter(), "waiter", errors), term(requested.items(), "items", errors),
				term(requested.send(), "send", errors));
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
		}
		if (!terms.equals(current.terms())) {
			repository.updateFeaturesAndTerms(current.features(), Template.CUSTOM, terms, clock.instant());
		}
		return repository.get();
	}

	/** Sets features and terms from a template in one step (spec business-features, "Business templates"). */
	@Transactional
	public Settings applyTemplate(Template template) {
		if (template == Template.CUSTOM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed",
					Map.of("template", "Custom cannot be applied; change features or terms instead"));
		}
		repository.lockForUpdate();
		repository.updateFeaturesAndTerms(template.features(), template, template.terms(), clock.instant());
		refreshAfterCommit();
		return repository.get();
	}

	/** Called by every order send before it saves the order; see {@link SettingsRepository#lockFeaturesForShare()}. */
	public Set<Feature> lockFeaturesForOrder() {
		return repository.lockFeaturesForShare();
	}

	/**
	 * Names the first broken dependency (spec business-features, "Feature dependencies"). If the admin is switching
	 * off the needed feature, the message asks to switch off the dependent one first.
	 */
	private static void checkDependencies(Set<Feature> current, Set<Feature> requested) {
		for (Feature feature : requested) {
			Feature needed = feature.requires().orElse(null);
			if (needed != null && !requested.contains(needed)) {
				String message = current.contains(needed)
						? "Switch off " + feature.key() + " first, because it needs " + needed.key()
						: "The feature " + feature.key() + " needs " + needed.key() + "; switch on " + needed.key()
								+ " as well";
				throw new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(feature.key(), message));
			}
		}
	}

	private void refreshAfterCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					cachedFeatures = null;
				}
			});
		}
		else {
			cachedFeatures = null;
		}
	}

	private static Set<Feature> withNone(Set<Feature> features) {
		return features.isEmpty() ? EnumSet.noneOf(Feature.class) : features;
	}

	private static String term(String value, String field, Map<String, String> errors) {
		String v = blankToNull(value);
		if (v == null) {
			errors.put(field, "Required");
		}
		else if (v.length() > Terms.MAX_LENGTH) {
			errors.put(field, "At most " + Terms.MAX_LENGTH + " characters");
		}
		return v;
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
