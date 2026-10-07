package io.github.levij.orderflow.product;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.levij.orderflow.auth.JsonAccessDeniedHandler;
import io.github.levij.orderflow.auth.JsonAuthenticationEntryPoint;
import io.github.levij.orderflow.auth.JwtConfig;
import io.github.levij.orderflow.auth.JwtTokenService;
import io.github.levij.orderflow.auth.SecurityConfig;
import io.github.levij.orderflow.user.Role;

@WebMvcTest(AdminProductController.class)
@Import({ SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class,
		JwtConfig.class, JwtTokenService.class })
class AdminProductAuthorizationTest {

	private static final String PRODUCT_URL = "/api/v1/admin/products/1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private ProductService productService;

	@Test
	void anonymousRequestIsUnauthenticated() throws Exception {
		mockMvc.perform(get(PRODUCT_URL))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		verifyNoInteractions(productService);
	}

	@Test
	void invalidTokenIsUnauthenticated() throws Exception {
		mockMvc.perform(get(PRODUCT_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		verifyNoInteractions(productService);
	}

	@Test
	void customerIsForbidden() throws Exception {
		mockMvc.perform(get(PRODUCT_URL).header(HttpHeaders.AUTHORIZATION, bearer(Role.CUSTOMER)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.detail").value("You do not have permission to access this resource."));

		verifyNoInteractions(productService);
	}

	@Test
	void customerCannotCreateProducts() throws Exception {
		String body = """
				{"sku": "ABC-1", "name": "Thing", "price": 1.00}
				""";

		mockMvc.perform(post("/api/v1/admin/products")
						.header(HttpHeaders.AUTHORIZATION, bearer(Role.CUSTOMER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		verifyNoInteractions(productService);
	}

	@Test
	void adminIsAllowedThrough() throws Exception {
		when(productService.getForAdmin(1L))
				.thenReturn(Product.create("ABC-1", "Thing", null, new BigDecimal("1.00")));

		mockMvc.perform(get(PRODUCT_URL).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sku").value("ABC-1"));
	}

	private String bearer(Role role) {
		return "Bearer " + jwtTokenService.issue(1L, role).value();
	}
}
