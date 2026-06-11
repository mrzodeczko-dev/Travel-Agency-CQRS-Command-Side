package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.Hotel;

import java.util.Optional;

public interface HotelRepository {
    Optional<Hotel> findHotel(Long id);
    Hotel saveHotel(Hotel hotel);
}
