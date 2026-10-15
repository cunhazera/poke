package com.app.poke.application.port.out;

import java.util.List;

public record CatalogSlice(List<Integer> ids, long totalCount) {

	public CatalogSlice {
		ids = List.copyOf(ids);
	}
}
