package com.rzodeczko.presentation.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CreateBookingRequestDto(
        @NotNull(message = "Hotel ID is required")
        @Positive(message = "Hotel ID must be a positive number")
        Long hotelId,

        @NotNull(message = "User ID is required")
        @Positive(message = "User ID must be a positive number")
        Long userId,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date must be today or in the future")
        LocalDate start,

        @NotNull(message = "End date is required")
        @FutureOrPresent(message = "End date must be today or in the future")
        LocalDate end
) {
}
