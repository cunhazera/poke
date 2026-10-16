package com.example.tasks.error;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.tasks.task.InvalidTaskException;
import com.example.tasks.task.TaskRuleException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Every error is an RFC 9457 {@code application/problem+json} body with a status, a title and a detail written for
 * the API's users; errors about specific fields or parameters list them in {@code errors}. Never a stack trace.
 *
 * <ul>
 * <li>400: a broken rule or a malformed request (body, path or query parameter)</li>
 * <li>404: the task or user in the path doesn't exist</li>
 * <li>409: the task's state doesn't allow the change (Todo → Done, unassigning work in progress…)</li>
 * <li>422: the body refers to something that doesn't exist (an unknown user)</li>
 * <li>405, 415, 500: Spring's own, with the same shape</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(InvalidTaskException.class)
	ProblemDetail handleInvalidTask(InvalidTaskException e) {
		return withErrors(problem(HttpStatus.BAD_REQUEST, "Invalid task", "One or more fields are invalid."), e.errors());
	}

	@ExceptionHandler(TaskRuleException.class)
	ProblemDetail handleRule(TaskRuleException e) {
		return problem(HttpStatus.CONFLICT, "Not allowed", e.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(UnknownReferenceException.class)
	ProblemDetail handleUnknownReference(UnknownReferenceException e) {
		return withErrors(problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reference", e.getMessage()),
				Map.of(e.field(), e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception e) {
		log.error("Unhandled error", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
	}

	/** A body Jackson can't read: an unknown field, a value of the wrong type or format, or not JSON at all. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem;
		if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
			var field = path(unknown.getPath());
			problem = withErrors(problem(HttpStatus.BAD_REQUEST, "Unknown field", "'%s' is not a field of this request.".formatted(field)),
					Map.of(field, "is not a field of this request"));
		} else if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			var field = path(mismatch.getPath());
			problem = withErrors(problem(HttpStatus.BAD_REQUEST, "Invalid field value", "'%s' has an invalid value.".formatted(field)),
					Map.of(field, expected(mismatch.getTargetType(), mismatch)));
		} else {
			problem = problem(HttpStatus.BAD_REQUEST, "Malformed request body", "The request body is missing or is not valid JSON.");
		}
		return ResponseEntity.badRequest().body(problem);
	}

	/** A path or query parameter of the wrong type: {@code /api/tasks/abc}, {@code ?status=FINISHED}. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		var name = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : e.getPropertyName();
		var problem = withErrors(problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "'%s' is not a valid value for '%s'."
				.formatted(e.getValue(), name)), Map.of(String.valueOf(name), expected(e.getRequiredType(), null)));
		return ResponseEntity.badRequest().body(problem);
	}

	/** A parameter that breaks a constraint: {@code size=500}, {@code page=-1}, a non-positive id. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var errors = new TreeMap<String, String>();
		e.getParameterValidationResults().forEach(result -> errors.putIfAbsent(result.getMethodParameter().getParameterName(),
				result.getResolvableErrors().getFirst().getDefaultMessage()));
		var problem = withErrors(problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "One or more parameters are invalid."), errors);
		return ResponseEntity.badRequest().body(problem);
	}

	private static String expected(Class<?> type, MismatchedInputException cause) {
		if (type != null && type.isEnum()) {
			return "must be one of " + Arrays.stream(type.getEnumConstants()).map(String::valueOf).collect(Collectors.joining(", "));
		}
		if (type != null && java.time.temporal.Temporal.class.isAssignableFrom(type)) {
			return "must be an ISO-8601 date and time with an offset, e.g. 2026-10-20T18:00:00Z";
		}
		if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
			return "must be a whole number";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "must be true or false";
		}
		if (cause instanceof InvalidFormatException) {
			return "has an invalid format";
		}
		return "has an invalid value";
	}

	/** {@code title}, {@code owner.id}, {@code items[0]}: the field's path in the request. */
	private static String path(List<JacksonException.Reference> references) {
		var path = new StringBuilder();
		for (var reference : references) {
			if (reference.getPropertyName() != null) {
				path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
			} else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

	private static ProblemDetail withErrors(ProblemDetail problem, Map<String, String> errors) {
		problem.setProperty("errors", new TreeMap<>(errors));
		return problem;
	}
}
