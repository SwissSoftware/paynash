package ch.paynash.pos.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@Tag(name = "Settings")
public class SettingsController {

	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	public record SettingsView(Mode mode, String businessName, String street, String postalCode, String city,
			String phone, String email, String website, String iban, String accountHolder, boolean vatEnabled,
			String vatUid) {

		static SettingsView of(Settings s) {
			return new SettingsView(s.mode(), s.businessName(), s.street(), s.postalCode(), s.city(), s.phone(),
					s.email(), s.website(), s.iban(), s.accountHolder(), s.vatEnabled(), s.vatUid());
		}

	}

	public record UpdateSettingsRequest(String businessName, String street, String postalCode, String city,
			String phone, String email, String website, String iban, String accountHolder, @NotNull Boolean vatEnabled,
			String vatUid) {
	}

	public record ChangeModeRequest(@NotNull Mode mode) {
	}

	@GetMapping("/settings")
	@Operation(summary = "Installation mode and business details",
			description = "Readable by every logged-in user. The apps build their navigation from the mode.")
	public SettingsView get() {
		return SettingsView.of(service.get());
	}

	@PutMapping("/admin/settings")
	@Operation(summary = "Save the business details and the VAT setting",
			description = "Street, postal code, and city are required in salon mode. Turning VAT on requires a "
					+ "valid UID (CHE-123.456.789 MWST). The IBAN must be Swiss or Liechtenstein.")
	public SettingsView update(@Valid @RequestBody UpdateSettingsRequest r) {
		return SettingsView.of(service.updateDetails(new SettingsService.DetailsInput(r.businessName(), r.street(),
				r.postalCode(), r.city(), r.phone(), r.email(), r.website(), r.iban(), r.accountHolder(),
				r.vatEnabled(), r.vatUid())));
	}

	@PutMapping("/admin/settings/mode")
	@Operation(summary = "Change the installation mode", description = "Only possible while no order exists (409).")
	public SettingsView changeMode(@Valid @RequestBody ChangeModeRequest r) {
		return SettingsView.of(service.changeMode(r.mode()));
	}

}
