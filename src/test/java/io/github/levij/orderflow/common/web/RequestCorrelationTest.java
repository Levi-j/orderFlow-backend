package io.github.levij.orderflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import io.github.levij.orderflow.auth.JsonAccessDeniedHandler;
import io.github.levij.orderflow.auth.JsonAuthenticationEntryPoint;
import io.github.levij.orderflow.auth.JwtConfig;
import io.github.levij.orderflow.auth.JwtTokenService;
import io.github.levij.orderflow.auth.SecurityConfig;
import io.github.levij.orderflow.product.ProductController;
import io.github.levij.orderflow.product.ProductService;

@WebMvcTest(ProductController.class)
@Import({ SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class,
		JwtConfig.class, JwtTokenService.class })
@ExtendWith(OutputCaptureExtension.class)
class RequestCorrelationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	@Test
	void eachRequestWritesOneAccessLogWithoutTheQueryString(CapturedOutput output) throws Exception {
		when(productService.listActive(any())).thenReturn(Page.empty());

		mockMvc.perform(get("/api/v1/products?page=0&size=1").header(RequestIdFilter.HEADER, "access-log-1"))
				.andExpect(status().isOk())
				.andExpect(header().string(RequestIdFilter.HEADER, "access-log-1"));

		List<String> accessLogs = output.getOut().lines()
				.filter(line -> line.contains("[access-log-1]") && line.contains("eventName=http.request"))
				.toList();
		assertThat(accessLogs).hasSize(1);
		assertThat(accessLogs.get(0))
				.contains(" INFO ", "method=GET", "path=/api/v1/products ", "status=200", "durationMs=")
				.doesNotContain("page=", "size=", "?");
	}

	@Test
	void authorizationHeaderNeverReachesTheLogsAndTheUnauthorizedResponseIsCorrelated(CapturedOutput output)
			throws Exception {
		mockMvc.perform(get("/api/v1/products")
						.header(RequestIdFilter.HEADER, "auth-header-1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer marker-token-not-for-logs"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(RequestIdFilter.HEADER, "auth-header-1"))
				.andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.requestId").value("auth-header-1"));

		assertThat(output.getOut())
				.contains("[auth-header-1]", "status=401")
				.doesNotContain("marker-token-not-for-logs");
	}

	@Test
	void unexpectedErrorIsCorrelatedInTheResponseAndLoggedOnceWithTheSameId(CapturedOutput output) throws Exception {
		when(productService.listActive(any())).thenThrow(new IllegalStateException("simulated failure"));

		mockMvc.perform(get("/api/v1/products").header(RequestIdFilter.HEADER, "error-500-demo"))
				.andExpect(status().isInternalServerError())
				.andExpect(header().string(RequestIdFilter.HEADER, "error-500-demo"))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(jsonPath("$.requestId").value("error-500-demo"))
				.andExpect(content().string(not(containsString("simulated failure"))));

		String logs = output.getOut();
		assertThat(logs).containsPattern("ERROR .*\\[error-500-demo\\] .*GlobalExceptionHandler.*Unexpected error")
				.containsPattern("\\[error-500-demo\\] .*eventName=http.request .*status=500");
		assertThat(StringUtils.countOccurrencesOf(logs, "java.lang.IllegalStateException: simulated failure"))
				.isEqualTo(1);
	}
}
