package io.github.levij.orderflow.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
	private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

	public static String currentRequestId() {
		return MDC.get(MDC_KEY);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startNanos = System.nanoTime();
		String requestId = requestIdFrom(request);
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			log.atInfo()
					.addKeyValue("eventName", "http.request")
					.addKeyValue("method", request.getMethod())
					.addKeyValue("path", request.getRequestURI())
					.addKeyValue("status", response.getStatus())
					.addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos))
					.log("HTTP request");
			MDC.remove(MDC_KEY);
		}
	}

	private static String requestIdFrom(HttpServletRequest request) {
		String supplied = request.getHeader(HEADER);
		if (supplied != null && VALID_REQUEST_ID.matcher(supplied).matches()) {
			return supplied;
		}
		return UUID.randomUUID().toString();
	}
}
