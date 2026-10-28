package ch.paynash.pos.auth;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ch.paynash.pos.common.IdempotencyFilter;
import ch.paynash.pos.common.IdempotencyRepository;
import ch.paynash.pos.common.Problems;

/**
 * Central permission matrix (spec staff-access, "Role-based permissions"). Admins inherit every waiter permission
 * through the role hierarchy.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService auth, IdempotencyRepository idempotencyKeys,
			Clock clock) throws Exception {
		http.csrf(csrf -> csrf.disable()) // bearer tokens, no cookies
			.cors(Customizer.withDefaults())
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(b -> b.disable())
			.formLogin(f -> f.disable())
			.logout(l -> l.disable())
			.addFilterBefore(new TokenAuthenticationFilter(auth), AnonymousAuthenticationFilter.class)
			// After authorization, so only permitted requests claim a key and keys are scoped per staff member.
			.addFilterAfter(new IdempotencyFilter(idempotencyKeys, clock), AuthorizationFilter.class)
			.authorizeHttpRequests(routes -> routes
				.requestMatchers(HttpMethod.GET, "/health", "/images/*").permitAll()
				.requestMatchers(HttpMethod.HEAD, "/health", "/images/*").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
				.requestMatchers("/v3/api-docs/**").permitAll()
				.requestMatchers("/admin/**", "/exports/**").hasRole("ADMIN")
				.anyRequest().hasRole("WAITER"))
			.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> Problems.write(res, HttpStatus.UNAUTHORIZED,
						"Authentication required"))
				.accessDeniedHandler((req, res, ex) -> Problems.write(res, HttpStatus.FORBIDDEN, "Forbidden")));
		return http.build();
	}

	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_WAITER");
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${pos.cors-allowed-origins}") List<String> origins) {
		var config = new CorsConfiguration();
		config.setAllowedOrigins(origins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		config.setAllowedHeaders(List.of("*"));
		config.setExposedHeaders(List.of("ETag", "Content-Disposition", IdempotencyFilter.REPLAYED_HEADER));
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

}
