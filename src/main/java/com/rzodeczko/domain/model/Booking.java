package com.rzodeczko.domain.model;

import java.time.LocalDate;

public record Booking(Long id, Long hotelId, Long userId, LocalDate start, LocalDate end, BookingStatus status) {

    public Booking(Long id, Long hotelId, Long userId, LocalDate start, LocalDate end) {
        this(id, hotelId, userId, start, end, BookingStatus.ACTIVE);
    }
}
