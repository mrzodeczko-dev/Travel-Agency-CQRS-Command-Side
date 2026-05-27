package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;

import java.time.LocalDate;
import java.util.Optional;


public interface TravelRepository {
    Optional<Hotel> findHotel(Long id);

    Booking save(Booking booking);

    void saveOutbox(Booking booking);

    void reserveAvailability(Long hotelId, int capacity, LocalDate start, LocalDate end);
}
