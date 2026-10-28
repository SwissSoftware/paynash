package ch.paynash.pos.auth;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ch.paynash.pos.staff.Role;
import ch.paynash.pos.staff.Staff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth")
public class AuthController {

	private final AuthService auth;

	public AuthController(AuthService auth) {
		this.auth = auth;
	}

	public record LoginRequest(@NotNull String username, @NotNull String pin) {
	}

	public record Me(long id, String username, String displayName, Role role) {

		static Me of(Staff s) {
			return new Me(s.id(), s.username(), s.displayName(), s.role());
		}

		static Me of(AuthenticatedStaff s) {
			return new Me(s.id(), s.username(), s.displayName(), s.role());
		}

	}

	public record LoginResponse(String token, Instant expiresAt, Me staff) {
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Log in with username and PIN",
			description = "Returns a bearer token valid for 12 hours. Any failure answers 401 \"Invalid credentials\".")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		var result = auth.login(request.username(), request.pin());
		return new LoginResponse(result.token(), result.expiresAt(), Me.of(result.staff()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "End the current session")
	public void logout(@AuthenticationPrincipal AuthenticatedStaff current) {
		auth.logout(current);
	}

	@GetMapping("/me")
	@Operation(summary = "The logged-in staff member")
	public Me me(@AuthenticationPrincipal AuthenticatedStaff current) {
		return Me.of(current);
	}

}
