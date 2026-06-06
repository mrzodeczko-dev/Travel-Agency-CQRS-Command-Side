package com.rzodeczko.presentation.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequestDto(
        @NotNull Long hotelId,
        @NotNull Long userId,
        @NotNull @FutureOrPresent LocalDate start,
        @NotNull @FutureOrPresent LocalDate end
) {
}
