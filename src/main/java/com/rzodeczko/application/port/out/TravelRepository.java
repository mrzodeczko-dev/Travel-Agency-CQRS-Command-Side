package com.rzodeczko.application.port.out;



import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Interfejs (port), ktory definiuje, czego domena potrzebuje od bazy danych.
 * TODO [ 2 ] Czy robi jedno repo
 */
public interface TravelRepository {
    Optional<Hotel> findHotel(Long id);
    List<Booking> findOverlapping(Long hotelId, LocalDate start, LocalDate end);
    Booking save(Booking booking);
    void saveOutbox(Booking booking);
    // Metoda techniczna: Wymuszenie sprawdzenia wersja dla optimistic locking
    // TODO [ 1 ] Kiedy skoncze calosc wroc tutaj i sprawdz czy ta metoda tutaj zostac
    void forceOptimisticLocking(Hotel hotel);
}
