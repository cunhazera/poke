package com.app.poke.adapter.in.web;

class RepeatedQueryParameterException extends RuntimeException {

	RepeatedQueryParameterException(String parameter) {
		super("Query parameter '%s' must not be repeated".formatted(parameter));
	}
}
