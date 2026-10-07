package io.github.levij.orderflow.common.error;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
		return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
				"Unknown sort property '" + ex.getPropertyName() + "'.");
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return problem(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
		return unauthorized(ErrorCode.INVALID_CREDENTIALS, ex.getMessage(), "Bearer");
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		String challenge = ex instanceof OAuth2AuthenticationException ? "Bearer error=\"invalid_token\"" : "Bearer";
		return unauthorized(ErrorCode.UNAUTHENTICATED, "Authentication is required to access this resource.", challenge);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "You do not have permission to access this resource.");
	}

	@ExceptionHandler(AuthenticationServiceException.class)
	ProblemDetail handleAuthenticationServiceFailure(AuthenticationServiceException ex) {
		return handleUnexpected(ex);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, "The request conflicts with existing data.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message))
				.toList();

		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
				"The request body is malformed or contains an invalid value.");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
			problem.setProperty("code", codeFor(statusCode).name());
		}
		return response;
	}

	private static ErrorCode codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> ErrorCode.NOT_ACCEPTABLE;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			case 400 -> ErrorCode.MALFORMED_REQUEST;
			default -> ErrorCode.INTERNAL_ERROR;
		};
	}

	private static ResponseEntity<ProblemDetail> unauthorized(ErrorCode code, String detail, String challenge) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, challenge)
				.body(problem(HttpStatus.UNAUTHORIZED, code, detail));
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code.name());
		return problem;
	}
}
