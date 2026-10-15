package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPageRequestException;

class PageRequestTest {

	@Test
	void offsetIsPageTimesSize() {
		assertThat(new PageRequest(0, 20).offset()).isZero();
		assertThat(new PageRequest(3, 20).offset()).isEqualTo(60);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, PageRequest.MAX_SIZE })
	void acceptsSizesAtTheBoundaries(int size) {
		assertThat(new PageRequest(0, size).size()).isEqualTo(size);
	}

	@ParameterizedTest
	@CsvSource({ "-1, 20", "0, 0", "0, -5", "0, " + (PageRequest.MAX_SIZE + 1) })
	void rejectsOutOfRangeValues(int page, int size) {
		assertThatThrownBy(() -> new PageRequest(page, size))
				.isInstanceOf(InvalidPageRequestException.class);
	}

	@Test
	void rejectsPagesWhoseOffsetWouldOverflow() {
		assertThatThrownBy(() -> new PageRequest(Integer.MAX_VALUE, PageRequest.MAX_SIZE))
				.isInstanceOf(InvalidPageRequestException.class)
				.hasMessageContaining("page");
	}

	@Test
	void errorMessageNamesTheOffendingParameter() {
		assertThatThrownBy(() -> new PageRequest(0, 500))
				.hasMessageContaining("size")
				.hasMessageContaining(String.valueOf(PageRequest.MAX_SIZE));
	}
}
