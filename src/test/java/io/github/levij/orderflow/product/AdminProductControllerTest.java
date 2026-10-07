package io.github.levij.orderflow.product;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.levij.orderflow.common.error.ConflictException;
import io.github.levij.orderflow.common.error.ErrorCode;
import io.github.levij.orderflow.common.error.NotFoundException;

@WebMvcTest(AdminProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProductControllerTest {

	private static final String PRODUCTS_URL = "/api/v1/admin/products";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	@Test
	void createWithSeveralInvalidFieldsReturnsAllFieldErrors() throws Exception {
		String body = """
				{"sku": "bad sku!", "name": "  ", "price": -1}
				""";

		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("sku", "name", "price")));
	}

	@Test
	void createWithEmptyObjectReportsEveryRequiredField() throws Exception {
		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("sku", "name", "price")));
	}

	@Test
	void createWithTooManyFractionDigitsIsRejected() throws Exception {
		String body = """
				{"sku": "ABC-1", "name": "Thing", "price": 12.345}
				""";

		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("price")));
	}

	@Test
	void updateWithInvalidBodyReturnsFieldErrors() throws Exception {
		String body = """
				{"name": "", "price": 0}
				""";

		mockMvc.perform(put(PRODUCTS_URL + "/1").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "price", "active")));
	}

	@Test
	void brokenJsonReturnsMalformedRequestWithoutInternals() throws Exception {
		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content("{\"sku\": "))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("jackson"))));
	}

	@Test
	void wrongValueTypeReturnsMalformedRequest() throws Exception {
		String body = """
				{"sku": "ABC-1", "name": "Thing", "price": "not a number"}
				""";

		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void duplicateSkuFromServiceReturns409() throws Exception {
		when(productService.create(any()))
				.thenThrow(new ConflictException(ErrorCode.DUPLICATE_SKU, "A product with SKU ABC-1 already exists."));
		String body = """
				{"sku": "ABC-1", "name": "Thing", "price": 1.00}
				""";

		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("DUPLICATE_SKU"))
				.andExpect(jsonPath("$.detail").value("A product with SKU ABC-1 already exists."));
	}

	@Test
	void unhandledConstraintViolationReturnsGeneric409WithoutDatabaseDetails() throws Exception {
		when(productService.create(any())).thenThrow(new DataIntegrityViolationException(
				"ERROR: duplicate key value violates unique constraint \"uk_products_sku\""));
		String body = """
				{"sku": "ABC-1", "name": "Thing", "price": 1.00}
				""";

		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
				.andExpect(content().string(not(containsString("uk_products_sku"))))
				.andExpect(content().string(not(containsString("duplicate key"))));
	}

	@Test
	void notFoundFromServiceReturns404() throws Exception {
		when(productService.getForAdmin(anyLong())).thenThrow(new NotFoundException("Product 7 not found"));

		mockMvc.perform(get(PRODUCTS_URL + "/7"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Product 7 not found"));
	}

	@Test
	void unexpectedExceptionReturnsGeneric500WithoutLeakingDetails() throws Exception {
		when(productService.getForAdmin(anyLong()))
				.thenThrow(new IllegalStateException("jdbc:postgresql://secret-host/db password=hunter2"));

		mockMvc.perform(get(PRODUCTS_URL + "/7"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(content().string(not(containsString("hunter2"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))))
				.andExpect(content().string(not(containsString("postgresql"))));
	}

	@Test
	void nonNumericIdReturns400() throws Exception {
		mockMvc.perform(get(PRODUCTS_URL + "/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void unsupportedMethodReturns405() throws Exception {
		mockMvc.perform(delete(PRODUCTS_URL + "/1"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void unsupportedContentTypeReturns415() throws Exception {
		mockMvc.perform(post(PRODUCTS_URL).contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}
}
