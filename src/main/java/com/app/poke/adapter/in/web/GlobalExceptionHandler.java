package com.app.poke.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.domain.exception.InvalidPageRequestException;
import com.app.poke.domain.exception.PokemonNotFoundException;

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
