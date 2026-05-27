package com.rzodeczko.presentation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequestDto(
        @NotNull Long hotelId,
        @NotNull Long userId,
        @NotNull @Future LocalDate start,
        @NotNull @Future LocalDate end
) { }
