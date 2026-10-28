package ch.paynash.pos.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Resolves {@code Authorization: Bearer <token>} to a server-side session. */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";

	private final AuthService auth;

	public TokenAuthenticationFilter(AuthService auth) {
		this.auth = auth;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(PREFIX)) {
			auth.authenticate(header.substring(PREFIX.length()).trim()).ifPresent(staff -> {
				var authority = new SimpleGrantedAuthority("ROLE_" + staff.role().name());
				var authentication = new UsernamePasswordAuthenticationToken(staff, null, List.of(authority));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}
		chain.doFilter(request, response);
	}

}
