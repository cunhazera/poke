package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PageTest {

	@ParameterizedTest(name = "{0} items / size {1} -> {2} pages")
	@CsvSource({ "0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "1025, 20, 52", "1025, 50, 21" })
	void totalPagesRoundsUp(long totalItems, int size, int expectedPages) {
		var page = new Page<>(List.of(), new PageRequest(0, size), totalItems);

		assertThat(page.totalPages()).isEqualTo(expectedPages);
	}

	@Test
	void itemsAreImmutableCopies() {
		var source = new java.util.ArrayList<>(List.of("a"));
		var page = new Page<>(source, new PageRequest(0, 20), 1);

		source.add("b");

		assertThat(page.items()).containsExactly("a");
		assertThatThrownBy(() -> page.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsNegativeTotal() {
		assertThatThrownBy(() -> new Page<>(List.of(), new PageRequest(0, 20), -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMoreItemsThanThePageSize() {
		assertThatThrownBy(() -> new Page<>(List.of("a", "b"), new PageRequest(0, 1), 10))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
