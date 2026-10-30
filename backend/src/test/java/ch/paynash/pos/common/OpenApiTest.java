package ch.paynash.pos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ch.paynash.pos.support.IntegrationTest;
import io.swagger.parser.OpenAPIParser;

/** Validates the generated OpenAPI document and writes it to target/openapi.json. */
class OpenApiTest extends IntegrationTest {

	@Test
	void generatedSpecIsValidAndDocumentsTheApi() throws Exception {
		String spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();

		var result = new OpenAPIParser().readContents(spec, null, null);
		assertThat(result.getMessages()).isEmpty();
		var api = result.getOpenAPI();
		assertThat(api.getPaths()).containsKeys("/auth/login", "/auth/logout", "/auth/me", "/admin/staff",
				"/admin/staff/{id}", "/admin/staff/{id}/pin");
		assertThat(api.getPaths()).containsKeys("/orders", "/orders/{id}", "/admin/orders/{id}/lines/{lineId}/cancel",
				"/admin/orders/{id}/void");
		assertThat(api.getPaths()).containsKeys("/tables", "/admin/tables", "/admin/tables/{id}");
		assertThat(api.getPaths()).containsKeys("/admin/printers", "/admin/printers/{id}", "/admin/printers/{id}/test",
				"/orders/{orderId}/tickets/{ticketId}/reprint");
		assertThat(api.getPaths()).containsKey("/exports/orders.csv");
		assertThat(api.getPaths()).doesNotContainKeys("/orders/{id}/lines", "/orders/{id}/send");
		assertThat(api.getPaths().get("/orders").getPost().getParameters())
			.anySatisfy(p -> assertThat(p.getName()).isEqualTo(IdempotencyFilter.HEADER));
		assertThat(api.getPaths().get("/orders/{id}").getGet().getParameters())
			.noneSatisfy(p -> assertThat(p.getName()).isEqualTo(IdempotencyFilter.HEADER));
		assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearer");
		assertThat(api.getPaths().get("/auth/login").getPost().getSecurity()).isEmpty();

		Files.writeString(Path.of("target/openapi.json"), spec);
	}

}
