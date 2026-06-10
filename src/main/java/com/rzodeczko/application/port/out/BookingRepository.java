package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.Booking;

import java.util.Optional;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(Long id);
}
