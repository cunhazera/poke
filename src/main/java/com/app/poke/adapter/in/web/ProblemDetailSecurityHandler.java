package com.app.poke.adapter.in.web;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401, 403 and firewall rejections (400) as RFC 9457 problem details, like every other error. Spring Security rejects these requests in
 * its filter chain, before {@link GlobalExceptionHandler} could see them, so they need their own writer.
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailSecurityHandler.class);

	private final JsonMapper json;

	public ProblemDetailSecurityHandler(JsonMapper json) {
		this.json = json;
	}

	/** No token, or an invalid or expired one. The cause stays in the server log; the client gets a generic message. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(response, request, HttpStatus.UNAUTHORIZED, "Authentication required",
				"A valid bearer token is required. Log in at /api/v1/auth/login.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
			throws IOException {
		write(response, request, HttpStatus.FORBIDDEN, "Access denied", "Your role does not allow this operation.");
	}

	/** Spring Security's firewall rejected the URL (e.g. encoded slashes, blank segments) before any controller ran. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException e)
			throws IOException {
		log.debug("Request rejected by the firewall: {}", e.getMessage());
		write(response, request, HttpStatus.BAD_REQUEST, "Invalid request", "The request URL is not allowed.");
	}

	private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String title,
			String detail) throws IOException {
		var problem = new LinkedHashMap<String, Object>();
		problem.put("type", "about:blank");
		problem.put("title", title);
		problem.put("status", status.value());
		problem.put("detail", detail);
		problem.put("instance", request.getRequestURI());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		json.writeValue(response.getOutputStream(), problem);
	}
}
