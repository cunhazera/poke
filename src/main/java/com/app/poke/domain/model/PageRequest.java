package com.app.poke.domain.model;

import com.app.poke.domain.exception.InvalidPageRequestException;

/**
 * A request for one page of results.
 *
 * @param page zero-based page index
 * @param size number of items per page, between 1 and {@link #MAX_SIZE}
 */
public record PageRequest(int page, int size) {

	/** Each listed entry costs two upstream calls, so the page size is capped. */
	public static final int MAX_SIZE = 50;

	public PageRequest {
		if (page < 0) {
			throw new InvalidPageRequestException("page must be >= 0, was " + page);
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new InvalidPageRequestException("size must be between 1 and %d, was %d".formatted(MAX_SIZE, size));
		}
		if ((long) page * size > Integer.MAX_VALUE) {
			throw new InvalidPageRequestException("page " + page + " is too large");
		}
	}

	public int offset() {
		return page * size;
	}
}
