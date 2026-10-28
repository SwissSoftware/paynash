package ch.paynash.pos.common;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import ch.paynash.pos.auth.AuthenticatedStaff;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Makes mutating order and payment requests safe to retry (design D5). The first request with a given
 * {@code Idempotency-Key} is processed and its response stored for 24 hours; a repeat of the same request gets the
 * stored response without running again. Keys are scoped per staff member. Requests without the header are processed
 * normally.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String HEADER = "Idempotency-Key";

	public static final String REPLAYED_HEADER = "Idempotent-Replayed";

	static final Duration RETENTION = Duration.ofHours(24);

	private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

	private final IdempotencyRepository keys;

	private final Clock clock;

	public IdempotencyFilter(IdempotencyRepository keys, Clock clock) {
		this.keys = keys;
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !MUTATING.contains(request.getMethod()) || request.getHeader(HEADER) == null
				|| !(path.startsWith("/orders") || path.startsWith("/admin/orders"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String key = request.getHeader(HEADER).trim();
		if (key.isEmpty() || key.length() > 100) {
			Problems.write(response, HttpStatus.BAD_REQUEST, HEADER + " must be 1 to 100 characters");
			return;
		}
		if (!(SecurityContextHolder.getContext().getAuthentication()
			.getPrincipal() instanceof AuthenticatedStaff staff)) {
			chain.doFilter(request, response);
			return;
		}

		var cached = new CachedBodyRequest(request);
		String fingerprint = fingerprint(request, cached.body);
		Instant now = clock.instant();
		keys.deleteCreatedBefore(now.minus(RETENTION));

		if (!keys.tryClaim(staff.id(), key, fingerprint, now)) {
			replay(staff.id(), key, fingerprint, response);
			return;
		}

		var wrapped = new ContentCachingResponseWrapper(response);
		try {
			chain.doFilter(cached, wrapped);
		}
		catch (IOException | ServletException | RuntimeException ex) {
			keys.release(staff.id(), key);
			throw ex;
		}
		if (wrapped.getStatus() >= 500) {
			// Server errors are not final; the client may retry with the same key.
			keys.release(staff.id(), key);
		}
		else {
			keys.complete(staff.id(), key, wrapped.getStatus(), wrapped.getContentType(),
					wrapped.getContentAsByteArray());
		}
		wrapped.copyBodyToResponse();
	}

	private void replay(long staffId, String key, String fingerprint, HttpServletResponse response)
			throws IOException {
		var stored = keys.find(staffId, key).orElse(null);
		if (stored == null) {
			// Released between the claim attempt and now (the first request failed); let the client retry.
			Problems.write(response, HttpStatus.CONFLICT, "Please retry the request");
		}
		else if (!stored.fingerprint().equals(fingerprint)) {
			Problems.write(response, HttpStatus.UNPROCESSABLE_CONTENT,
					HEADER + " was already used for a different request");
		}
		else if (stored.status() == null) {
			Problems.write(response, HttpStatus.CONFLICT, "The same request is still being processed");
		}
		else {
			response.setStatus(stored.status());
			if (stored.contentType() != null) {
				response.setContentType(stored.contentType());
			}
			response.setHeader(REPLAYED_HEADER, "true");
			if (stored.body() != null) {
				response.setContentLength(stored.body().length);
				response.getOutputStream().write(stored.body());
			}
		}
	}

	private static String fingerprint(HttpServletRequest request, byte[] body) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			digest.update((request.getMethod() + " " + request.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8));
			digest.update(body);
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Reads the body once so it can be fingerprinted and still be read by the controller. */
	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		CachedBodyRequest(HttpServletRequest request) throws IOException {
			super(request);
			this.body = request.getInputStream().readAllBytes();
		}

		@Override
		public ServletInputStream getInputStream() {
			var in = new ByteArrayInputStream(body);
			return new ServletInputStream() {

				@Override
				public int read() {
					return in.read();
				}

				@Override
				public int read(byte[] b, int off, int len) {
					return in.read(b, off, len);
				}

				@Override
				public boolean isFinished() {
					return in.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException();
				}

			};
		}

		@Override
		public BufferedReader getReader() {
			Charset charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8
					: Charset.forName(getCharacterEncoding());
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

	}

}
