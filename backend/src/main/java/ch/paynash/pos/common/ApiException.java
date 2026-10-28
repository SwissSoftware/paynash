package ch.paynash.pos.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** An error that maps directly to an RFC 9457 problem response. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final Map<String, String> fieldErrors;

	private final Map<String, Object> properties = new LinkedHashMap<>();

	public ApiException(HttpStatus status, String message) {
		this(status, message, Map.of());
	}

	public ApiException(HttpStatus status, String message, Map<String, String> fieldErrors) {
		super(message);
		this.status = status;
		this.fieldErrors = fieldErrors;
	}

	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, what + " not found");
	}

	public static ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of(field, message));
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

	/** Adds an extra member to the problem response, e.g. the names of blocking items. */
	public ApiException with(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public Map<String, Object> properties() {
		return properties;
	}

	public HttpStatus status() {
		return status;
	}

	public Map<String, String> fieldErrors() {
		return fieldErrors;
	}

}
