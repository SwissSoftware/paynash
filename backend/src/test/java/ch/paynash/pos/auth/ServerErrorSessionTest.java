package ch.paynash.pos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.Filter;

import ch.paynash.pos.support.IntegrationTest;

/**
 * Unexpected server errors must not look like authentication failures (spec staff-access, "Server errors keep the
 * session"). Runs on a real port because MockMvc does not perform the servlet container's error dispatch, where the
 * 401 came from.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "pos.image-dir=target/test-images", "pos.printing.worker-enabled=false" })
class ServerErrorSessionTest extends IntegrationTest {

	private static final String SECRET = "SELECT card_number FROM secret_table";

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingEndpoints {

		@Bean
		FailingController failingController() {
			return new FailingController();
		}

		/** Fails outside Spring MVC, so only the error dispatch can render the response. */
		@Bean
		FilterRegistrationBean<Filter> failingFilter() {
			var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
				throw new IllegalStateException(SECRET);
			});
			registration.addUrlPatterns("/test-errors/filter");
			return registration;
		}

	}

	@RestController
	static class FailingController {

		@GetMapping("/test-errors/controller")
		String controller() {
			throw new IllegalStateException(SECRET);
		}

		@GetMapping("/test-errors/not-found")
		String notFound() {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such thing");
		}

	}

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		token = waiterToken();
	}

	@Test
	void errorInControllerIsServerErrorAndKeepsSession() throws Exception {
		assertServerErrorKeepsSession("/test-errors/controller");
	}

	@Test
	void errorOutsideControllerIsServerErrorAndKeepsSession() throws Exception {
		assertServerErrorKeepsSession("/test-errors/filter");
	}

	@Test
	void withoutSessionTheRequestIsStillUnauthenticated() throws Exception {
		var response = get("/test-errors/controller", null);
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).doesNotContain(SECRET);
	}

	@Test
	void exceptionsWithOwnStatusKeepIt() throws Exception {
		var response = get("/test-errors/not-found", token);
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat((String) JsonPath.read(response.body(), "$.detail")).isEqualTo("No such thing");
	}

	@Test
	void errorPathIsNotReachableDirectly() throws Exception {
		var response = get("/error", token);
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
	}

	private void assertServerErrorKeepsSession(String path) throws Exception {
		var response = get(path, token);
		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat((Integer) JsonPath.read(response.body(), "$.status")).isEqualTo(500);
		assertThat((String) JsonPath.read(response.body(), "$.detail")).isEqualTo("Unexpected server error");
		assertThat(response.body()).doesNotContain(SECRET)
			.doesNotContain("secret_table")
			.doesNotContain("IllegalStateException")
			.doesNotContain("Exception");

		assertThat(get("/auth/me", token).statusCode()).isEqualTo(200);
	}

	private HttpResponse<String> get(String path, String bearerToken) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
		if (bearerToken != null) {
			request.header(HttpHeaders.AUTHORIZATION, bearer(bearerToken));
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

}
