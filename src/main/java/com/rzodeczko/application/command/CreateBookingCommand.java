package com.rzodeczko.application.command;

import java.time.LocalDate;

public record CreateBookingCommand(
        Long hotelId,
        Long userId,
        LocalDate start,
        LocalDate end
) {
    public CreateBookingCommand {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Dates required");
        }
    }
}
