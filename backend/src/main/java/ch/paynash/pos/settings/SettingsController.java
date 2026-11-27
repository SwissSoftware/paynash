package ch.paynash.pos.settings;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Features, template, terms, and business details (change configurable-business-features, design D5). */
@RestController
@Tag(name = "Settings")
public class SettingsController {

	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	/** {@code features} maps every feature key (e.g. {@code sales-report}) to whether it is on. */
	public record SettingsView(Map<String, Boolean> features, Template template, Terms terms, String businessName,
			String street, String postalCode, String city, String phone, String email, String website, String iban,
			String accountHolder, boolean vatEnabled, String vatUid) {

		static SettingsView of(Settings s) {
			Map<String, Boolean> features = new LinkedHashMap<>();
			for (Feature f : Feature.values()) {
				features.put(f.key(), s.has(f));
			}
			return new SettingsView(features, s.template(), s.terms(), s.businessName(), s.street(), s.postalCode(),
					s.city(), s.phone(), s.email(), s.website(), s.iban(), s.accountHolder(), s.vatEnabled(),
					s.vatUid());
		}

	}

	public record UpdateSettingsRequest(String businessName, String street, String postalCode, String city,
			String phone, String email, String website, String iban, String accountHolder, @NotNull Boolean vatEnabled,
			String vatUid) {
	}

	public record ApplyTemplateRequest(@NotNull Template template) {
	}

	@GetMapping("/settings")
	@Operation(summary = "Features, template, terms, and business details",
			description = "Readable by every logged-in user. The apps build their navigation from the features and "
					+ "use the terms as labels.")
	public SettingsView get() {
		return SettingsView.of(service.get());
	}

	@PutMapping("/admin/settings")
	@Operation(summary = "Save the business details and the VAT setting",
			description = "Street, postal code, and city are required while invoices are on. Turning VAT on requires "
					+ "a valid UID (CHE-123.456.789 MWST). The IBAN must be Swiss or Liechtenstein.")
	public SettingsView update(@Valid @RequestBody UpdateSettingsRequest r) {
		return SettingsView.of(service.updateDetails(new SettingsService.DetailsInput(r.businessName(), r.street(),
				r.postalCode(), r.city(), r.phone(), r.email(), r.website(), r.iban(), r.accountHolder(),
				r.vatEnabled(), r.vatUid())));
	}

	@PutMapping("/admin/settings/features")
	@Operation(summary = "Switch features on and off",
			description = "Takes every feature key with true or false. invoices needs customers, sales-report needs "
					+ "invoices (400 otherwise). Switching a feature off keeps its data.")
	public SettingsView updateFeatures(@RequestBody Map<String, Boolean> features) {
		return SettingsView.of(service.updateFeatures(parse(features)));
	}

	@PutMapping("/admin/settings/terms")
	@Operation(summary = "Rename the waiter role, menu items, and sending an order",
			description = "Each term 1 to 30 characters.")
	public SettingsView updateTerms(@RequestBody Terms terms) {
		return SettingsView.of(service.updateTerms(terms));
	}

	@PostMapping("/admin/settings/template")
	@Operation(summary = "Apply a business template",
			description = "Sets features and terms of event, restaurant, salon, or counter. Other data is unchanged.")
	public SettingsView applyTemplate(@Valid @RequestBody ApplyTemplateRequest r) {
		return SettingsView.of(service.applyTemplate(r.template()));
	}

	/** Requires every feature key exactly once, so a client cannot switch a feature off by forgetting it. */
	private static Set<Feature> parse(Map<String, Boolean> input) {
		Map<String, String> errors = new LinkedHashMap<>();
		Set<Feature> features = EnumSet.noneOf(Feature.class);
		for (Map.Entry<String, Boolean> e : input.entrySet()) {
			Feature feature;
			try {
				feature = Feature.fromKey(e.getKey());
			}
			catch (IllegalArgumentException ex) {
				errors.put(e.getKey(), "Unknown feature");
				continue;
			}
			if (e.getValue() == null) {
				errors.put(e.getKey(), "Must be true or false");
			}
			else if (e.getValue()) {
				features.add(feature);
			}
		}
		for (Feature f : Feature.values()) {
			if (!input.containsKey(f.key())) {
				errors.put(f.key(), "Required");
			}
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", errors);
		}
		return features;
	}

}
