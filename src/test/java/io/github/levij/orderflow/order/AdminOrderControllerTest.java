package io.github.levij.orderflow.order;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;

	@Test
	void optimisticLockConflictReturns409WithoutInternals() throws Exception {
		when(orderService.confirm(7L)).thenThrow(new ObjectOptimisticLockingFailureException(CustomerOrder.class, 7L));

		mockMvc.perform(post("/api/v1/admin/orders/7/confirm"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
				.andExpect(jsonPath("$.detail").value("The resource was changed by another request. Reload it and try again."))
				.andExpect(content().string(not(containsString("CustomerOrder"))))
				.andExpect(content().string(not(containsString("optimistic"))))
				.andExpect(content().string(not(containsString("Exception"))));
	}

	@Test
	void unknownStatusFilterIsAMalformedRequest() throws Exception {
		mockMvc.perform(get("/api/v1/admin/orders").queryParam("status", "SHIPPED"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

		verifyNoInteractions(orderService);
	}
}
