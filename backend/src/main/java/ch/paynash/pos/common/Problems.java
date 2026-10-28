package ch.paynash.pos.common;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/** Writes RFC 9457 problem responses from filters, which run outside {@link ApiExceptionHandler}. */
public final class Problems {

	private Problems() {
	}

	public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
				.formatted(status.getReasonPhrase(), status.value(), detail));
	}

}
