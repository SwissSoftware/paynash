package ch.paynash.pos.common;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Renders errors that escape Spring MVC (e.g. thrown in a servlet filter) as RFC 9457 problems, like
 * {@link ApiExceptionHandler} does for controllers. Replaces Spring Boot's default error controller. Server errors get a
 * generic detail so no internals reach the client; the container has already logged the exception.
 */
@RestController
public class ProblemErrorController implements ErrorController {

	@RequestMapping("${server.error.path:${error.path:/error}}")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		if (request.getDispatcherType() != DispatcherType.ERROR) {
			return problem(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase());
		}
		HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
				&& HttpStatus.resolve(code) != null ? HttpStatus.resolve(code) : HttpStatus.INTERNAL_SERVER_ERROR;
		return problem(status, status.is5xxServerError() ? "Unexpected server error" : status.getReasonPhrase());
	}

	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(ProblemDetail.forStatusAndDetail(status, detail));
	}

}
