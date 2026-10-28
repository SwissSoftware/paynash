package ch.paynash.pos.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

@RestController
public class HealthController {

	@GetMapping("/health")
	@SecurityRequirements
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}

}
