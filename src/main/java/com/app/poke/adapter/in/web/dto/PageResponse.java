package com.app.poke.adapter.in.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic paginated envelope, shared by every listing endpoint for a consistent return structure.
 *
 * @param page zero-based page index
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public PageResponse {
		items = List.copyOf(items);
	}

	@JsonProperty
	public boolean hasNext() {
		return page + 1 < totalPages;
	}

	@JsonProperty
	public boolean hasPrevious() {
		return page > 0;
	}
}
