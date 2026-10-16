package com.app.poke.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record AbilityRequest(@NotNull String name, @NotNull Boolean hidden) {
}
