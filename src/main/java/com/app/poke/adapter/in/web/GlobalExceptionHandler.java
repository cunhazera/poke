package com.app.poke.adapter.in.web;

import java.util.List;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.exception.InvalidCredentialsException;
import com.app.poke.domain.exception.CatalogFieldsNotEditableException;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.InvalidPageRequestException;
import com.app.poke.domain.exception.InvalidPokemonDataException;
import com.app.poke.domain.exception.InvalidPokemonIdException;
import com.app.poke.domain.exception.InvalidUserDataException;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.exception.PokemonNameTakenException;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.exception.UserNotFoundException;
import com.app.poke.domain.exception.UsernameTakenException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Maps every failure to an RFC 9457 {@link ProblemDetail}, so all errors share one structure. Framework errors
 * (type mismatches, missing parameters, ...) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(InvalidPageRequestException.class)
	ProblemDetail handleInvalidPage(InvalidPageRequestException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid pagination", e.getMessage());
	}

	@ExceptionHandler(InvalidPokemonIdException.class)
	ProblemDetail handleInvalidPokemonId(InvalidPokemonIdException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid Pokémon id", e.getMessage());
	}

	@ExceptionHandler(RepeatedQueryParameterException.class)
	ProblemDetail handleRepeatedParameter(RepeatedQueryParameterException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid query parameter", e.getMessage());
	}

	@ExceptionHandler(InvalidPathVariableException.class)
	ProblemDetail handleInvalidPathVariable(InvalidPathVariableException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid id", e.getMessage());
	}

	@ExceptionHandler(LocalPokemonNotFoundException.class)
	ProblemDetail handleLocalPokemonNotFound(LocalPokemonNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Local Pokémon not found", e.getMessage());
	}

	@ExceptionHandler(ConcurrentUpdateException.class)
	ProblemDetail handleConcurrentUpdate(ConcurrentUpdateException e) {
		return problem(HttpStatus.CONFLICT, "Concurrent update",
				"The Pokémon was changed by another request meanwhile. Reload it and try again.");
	}

	@ExceptionHandler(InvalidPokemonDataException.class)
	ProblemDetail handleInvalidPokemonData(InvalidPokemonDataException e) {
		var problem = problem(HttpStatus.BAD_REQUEST, "Invalid Pokémon data", e.getMessage());
		problem.setProperty("field", e.field());
		return problem;
	}

	@ExceptionHandler(CatalogFieldsNotEditableException.class)
	ProblemDetail handleCatalogFieldsNotEditable(CatalogFieldsNotEditableException e) {
		return problem(HttpStatus.BAD_REQUEST, "Catalog fields not editable", e.getMessage());
	}

	@ExceptionHandler(PokemonNameTakenException.class)
	ProblemDetail handlePokemonNameTaken(PokemonNameTakenException e) {
		return problem(HttpStatus.CONFLICT, "Name taken", e.getMessage());
	}

	@ExceptionHandler(InvalidUserDataException.class)
	ProblemDetail handleInvalidUserData(InvalidUserDataException e) {
		var problem = problem(HttpStatus.BAD_REQUEST, "Invalid user data", e.getMessage());
		problem.setProperty("field", e.field());
		return problem;
	}

	@ExceptionHandler(UsernameTakenException.class)
	ProblemDetail handleUsernameTaken(UsernameTakenException e) {
		return problem(HttpStatus.CONFLICT, "Username taken", e.getMessage());
	}

	@ExceptionHandler(EmailTakenException.class)
	ProblemDetail handleEmailTaken(EmailTakenException e) {
		return problem(HttpStatus.CONFLICT, "Email already registered", e.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
				.body(problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", e.getMessage()));
	}

	@ExceptionHandler(UserNotFoundException.class)
	ProblemDetail handleUserNotFound(UserNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "User not found", "The user this token belongs to no longer exists.");
	}

	/** Bean Validation on request bodies: lists every missing or malformed field. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var errors = new TreeMap<String, String>();
		e.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		var problem = problem(HttpStatus.BAD_REQUEST, "Invalid request body", "One or more fields are invalid.");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	/**
	 * A body Jackson can't read. An unknown field or a value of the wrong type names the field (as a path such as
	 * {@code stats.hp}); anything else (empty, not JSON) gets a generic message, never Jackson's internals.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem;
		if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
			problem = problem(HttpStatus.BAD_REQUEST, "Unknown field",
					"'%s' is not a field of this request.".formatted(path(unknown.getPath())));
			problem.setProperty("field", path(unknown.getPath()));
		} else if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
			problem = problem(HttpStatus.BAD_REQUEST, "Invalid field value",
					"'%s' has a value of the wrong type.".formatted(path(mismatch.getPath())));
			problem.setProperty("field", path(mismatch.getPath()));
		} else {
			problem = problem(HttpStatus.BAD_REQUEST, "Malformed request body", "The request body is missing or is not valid JSON.");
		}
		return ResponseEntity.badRequest().body(problem);
	}

	/** {@code stats.hp}, {@code abilities[0].name}: the same paths Bean Validation reports. */
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

	@ExceptionHandler(PokemonNotFoundException.class)
	ProblemDetail handleNotFound(PokemonNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Pokémon not found", e.getMessage());
	}

	@ExceptionHandler(CatalogUnavailableException.class)
	ProblemDetail handleCatalogUnavailable(CatalogUnavailableException e) {
		log.warn("Pokémon catalog unavailable: {}", e.getMessage(), e);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Pokémon catalog unavailable",
				"The upstream Pokémon catalog is not responding. Please try again later.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception e) {
		log.error("Unhandled error", e);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
