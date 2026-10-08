package io.github.levij.orderflow.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI orderFlowOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("OrderFlow API")
						.version("v1")
						.description("REST API for the OrderFlow backend: product catalog, inventory, customer orders, "
								+ "customer registration and JWT authentication. Admin endpoints require a user with "
								+ "the ADMIN role; order endpoints require a user with the CUSTOMER role."))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")
								.description("Access token returned by POST /api/v1/auth/login.")));
	}
}
