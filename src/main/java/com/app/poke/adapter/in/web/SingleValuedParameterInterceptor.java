package com.app.poke.adapter.in.web;

import java.util.Collection;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects a repeated query parameter when the handler binds it to a single value. By default Spring silently
 * keeps the first value ({@code ?page=1&page=2} serves page 1), which hides client bugs.
 */
public class SingleValuedParameterInterceptor implements HandlerInterceptor {

	private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (handler instanceof HandlerMethod method) {
			for (MethodParameter parameter : method.getMethodParameters()) {
				var requestParam = parameter.getParameterAnnotation(RequestParam.class);
				if (requestParam == null || isMultiValued(parameter.getParameterType())) {
					continue;
				}
				var name = parameterName(requestParam, parameter);
				var values = request.getParameterValues(name);
				if (values != null && values.length > 1) {
					throw new RepeatedQueryParameterException(name);
				}
			}
		}
		return true;
	}

	private String parameterName(RequestParam requestParam, MethodParameter parameter) {
		if (!requestParam.name().isEmpty()) {
			return requestParam.name();
		}
		parameter.initParameterNameDiscovery(names);
		return parameter.getParameterName();
	}

	private static boolean isMultiValued(Class<?> type) {
		return type.isArray() || Collection.class.isAssignableFrom(type) || java.util.Map.class.isAssignableFrom(type);
	}
}
