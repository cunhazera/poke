package com.app.poke.domain.model;

import java.util.List;
import java.util.Objects;

public record Page<T>(List<T> items, PageRequest request, long totalItems) {

	public Page {
		items = List.copyOf(items);
		Objects.requireNonNull(request, "request");
		if (totalItems < 0) {
			throw new IllegalArgumentException("totalItems must be >= 0, was " + totalItems);
		}
		if (items.size() > request.size()) {
			throw new IllegalArgumentException("page holds %d items but size is %d".formatted(items.size(), request.size()));
		}
	}

	public int totalPages() {
		return (int) ((totalItems + request.size() - 1) / request.size());
	}
}
