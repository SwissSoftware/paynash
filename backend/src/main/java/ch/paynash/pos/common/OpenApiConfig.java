package ch.paynash.pos.common;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Paynash POS API", version = "v1"),
		security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class OpenApiConfig {

	/** Documents the optional Idempotency-Key header on every operation {@link IdempotencyFilter} covers. */
	@Bean
	OpenApiCustomizer idempotencyKeyHeader() {
		return api -> api.getPaths().forEach((path, item) -> {
			if (!(path.startsWith("/orders") || path.startsWith("/admin/orders"))) {
				return;
			}
			item.readOperationsMap().forEach((method, operation) -> {
				if (method.name().equals("GET") || method.name().equals("HEAD")) {
					return;
				}
				operation.addParametersItem(new HeaderParameter().name(IdempotencyFilter.HEADER)
					.required(false)
					.description("Client-generated UUID. A repeated request with the same key within 24 hours "
							+ "returns the stored response without running again.")
					.schema(new StringSchema().maxLength(100)));
			});
		});
	}

}
