package com.app.poke.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/** Decimal degrees; ranges and precision are a domain rule (Coordinates). */
public record CoordinatesRequest(@NotNull BigDecimal latitude, @NotNull BigDecimal longitude) {
}
