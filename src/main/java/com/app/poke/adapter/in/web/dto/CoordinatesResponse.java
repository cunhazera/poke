package com.app.poke.adapter.in.web.dto;

import java.math.BigDecimal;

/** Decimal degrees. */
public record CoordinatesResponse(BigDecimal latitude, BigDecimal longitude) {
}
