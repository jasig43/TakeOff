package com.takeoff.backend.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.takeoff.backend.exception.ApiErrorResponse.FieldViolation;

/**
 * Turns every failure into an {@link ApiErrorResponse}. Extending {@link ResponseEntityExceptionHandler}
 * routes Spring MVC's own errors (malformed JSON, 404, 405, 415 ...) through the same shape.
 * Internal details and stack traces are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
		if (ex.getStatus().is5xxServerError()) {
			log.warn("{} ({}): {}", ex.getCode(), ex.getStatus().value(), ex.getMessage(), ex.getCause());
		}
		return ResponseEntity.status(ex.getStatus())
			.body(ApiErrorResponse.of(ex.getStatus(), ex.getCode(), ex.getMessage(), path(request)));
	}

	/** Lost race on a unique constraint (two simultaneous registrations with the same email/phone). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiErrorResponse.of(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
					"An account with these details already exists.", path(request)));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiErrorResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
					"Authentication is required to access this resource.", path(request)));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ApiErrorResponse.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
					"You do not have permission to access this resource.", path(request)));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Unhandled exception for {}", path(request), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
					"Something went wrong on our side. Please try again later.", path(request)));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
			.toList();
		ApiErrorResponse body = ApiErrorResponse.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
				"Some of the information you entered is not valid.", path(request), violations);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	/** Every other Spring MVC error: same body shape, generic message (framework messages leak internals). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		String message = switch (status) {
			case BAD_REQUEST -> "The request could not be understood. Check the request and try again.";
			case NOT_FOUND -> "The requested resource was not found.";
			case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this resource.";
			case UNSUPPORTED_MEDIA_TYPE -> "Unsupported content type. Send the request as application/json.";
			default -> status.is5xxServerError() ? "Something went wrong on our side. Please try again later."
					: "The request could not be processed.";
		};
		if (status.is5xxServerError()) {
			log.error("Spring MVC error for {}", path(request), ex);
		}
		String code = status.name();
		return ResponseEntity.status(status).headers(headers).body(ApiErrorResponse.of(status, code, message, path(request)));
	}

	private static String path(WebRequest request) {
		return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
	}
}
