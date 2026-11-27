package ch.paynash.pos.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 problem responses. Validation errors carry an {@code errors} map of field to message.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
		if (!ex.fieldErrors().isEmpty()) {
			problem.setProperty("errors", ex.fieldErrors());
		}
		ex.properties().forEach(problem::setProperty);
		return ResponseEntity.status(ex.status()).body(problem);
	}

	/**
	 * Last resort for unexpected errors (spec staff-access, "Server errors keep the session"): logs the exception and
	 * answers 500 with a generic detail, so no SQL or class names reach the client. Security exceptions are rethrown so
	 * Spring Security still answers 401/403, and exceptions that carry their own status keep it.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) throws Exception {
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		if (ex instanceof ErrorResponse errorResponse) {
			return ResponseEntity.status(errorResponse.getStatusCode())
				.headers(errorResponse.getHeaders())
				.body(errorResponse.getBody());
		}
		log.error("Unexpected error on {}", request.getDescription(false), ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Unexpected server error");
		return ResponseEntity.internalServerError().body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
				"Image must be at most 5 MB");
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

}
