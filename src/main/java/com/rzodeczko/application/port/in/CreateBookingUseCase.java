package com.rzodeczko.application.port.in;

import java.time.LocalDate;

/**
 * Interfejs definiujacy, co swiat zewnetrzny moze zrobic z nasza aplikacja.
 * TODO [ 2 ] Wprowadzic DTO.
 */
public interface CreateBookingUseCase {
    Long createBooking(Long hotelId, Long userId, LocalDate start, LocalDate end);
}
