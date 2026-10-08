package io.github.levij.orderflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class RequestIdFilterTest {

	private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	private final RequestIdFilter filter = new RequestIdFilter();

	private final AtomicReference<String> requestIdSeenByTheApplication = new AtomicReference<>();

	private final FilterChain application = (request, response) ->
			requestIdSeenByTheApplication.set(MDC.get(RequestIdFilter.MDC_KEY));

	@BeforeEach
	void startWithoutRequestId() {
		MDC.remove(RequestIdFilter.MDC_KEY);
	}

	@Test
	void validClientIdIsUsedDuringTheRequestAndReturned() throws Exception {
		MockHttpServletResponse response = run(requestWithId("demo-123"));

		assertThat(requestIdSeenByTheApplication).hasValue("demo-123");
		assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("demo-123");
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void missingIdIsGenerated() throws Exception {
		MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/api/v1/products"));

		String generated = response.getHeader(RequestIdFilter.HEADER);
		assertThat(generated).matches(UUID_FORMAT);
		assertThat(requestIdSeenByTheApplication).hasValue(generated);
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@ParameterizedTest
	@MethodSource("invalidIds")
	void invalidClientIdIsReplacedAndNeverEchoed(String invalidId) throws Exception {
		MockHttpServletResponse response = run(requestWithId(invalidId));

		String replacement = response.getHeader(RequestIdFilter.HEADER);
		assertThat(replacement).matches(UUID_FORMAT).isNotEqualTo(invalidId);
		assertThat(requestIdSeenByTheApplication).hasValue(replacement);
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void sixtyFourCharactersAreAccepted() throws Exception {
		String longestValidId = "A1b2_C3-d4".repeat(6) + "Z9_-";

		MockHttpServletResponse response = run(requestWithId(longestValidId));

		assertThat(longestValidId).hasSize(64);
		assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(longestValidId);
		assertThat(requestIdSeenByTheApplication).hasValue(longestValidId);
	}

	@Test
	void requestIdDoesNotLeakIntoTheNextRequestOnTheSameThread() throws Exception {
		run(requestWithId("first-request"));

		MockHttpServletResponse second = run(new MockHttpServletRequest("GET", "/api/v1/products"));

		assertThat(second.getHeader(RequestIdFilter.HEADER)).isNotEqualTo("first-request").matches(UUID_FORMAT);
		assertThat(requestIdSeenByTheApplication).hasValue(second.getHeader(RequestIdFilter.HEADER));
	}

	@Test
	void requestIdIsRemovedEvenWhenTheRequestFails() {
		FilterChain failingApplication = (request, response) -> {
			throw new ServletException("simulated failure");
		};

		assertThatThrownBy(() -> filter.doFilter(requestWithId("failing-request"), new MockHttpServletResponse(),
				failingApplication)).isInstanceOf(ServletException.class);

		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	static Stream<String> invalidIds() {
		return Stream.of("", " ", "has space", "bad!id", "dot.ted", "line\nbreak", "x".repeat(65));
	}

	private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, application);
		return response;
	}

	private static MockHttpServletRequest requestWithId(String requestId) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
		request.addHeader(RequestIdFilter.HEADER, requestId);
		return request;
	}
}
