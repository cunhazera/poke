package com.app.poke.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.TreeMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.exception.InvalidCredentialsException;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.InvalidPageRequestException;
import com.app.poke.domain.exception.InvalidPokemonIdException;
import com.app.poke.domain.exception.InvalidUserDataException;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.exception.UserNotFoundException;
import com.app.poke.domain.exception.UsernameTakenException;

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
