package com.rzodeczko.application.command;

public record CancelBookingCommand(Long bookingId) {
    public CancelBookingCommand {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID is required");
        }
    }
}
