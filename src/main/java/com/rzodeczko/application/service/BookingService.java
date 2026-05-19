package com.rzodeczko.application.service;

import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.application.port.out.TravelRepository;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;

import java.time.LocalDate;

public class BookingService implements CreateBookingUseCase {
    private final TravelRepository travelRepository;

    public BookingService(TravelRepository travelRepository) {
        this.travelRepository = travelRepository;
    }

    // TODO [ 3 ] Walidacja - tutaj czy w warstwie web
    @Override
    public Long createBooking(Long hotelId, Long userId, LocalDate start, LocalDate end) {
        // 1. Walidacja logincza parametrow wejsciowych
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        // 2. Pobieranie agregatu (Hotel) z bazy
        Hotel hotel = travelRepository.findHotel(hotelId)
                .orElseThrow(() -> new IllegalArgumentException("Hotel not found"));

        // 3. Pobierani z bazy listy potencjalnych konfliktow
        var conflicts = travelRepository.findOverlapping(hotelId, start, end);

        // 4. Walidacja domentowa (metoda pudelkowa)
        hotel.validateAvailability(conflicts, start, end);

        // 5. Zapis rezerwacji
        Booking newBooking = new Booking(null, hotelId, userId, start, end);
        Booking saved = travelRepository.save(newBooking);

        // 6. Zapis Outbox (Pattern Transactional Outbox)
        // Zapisujemy zdarzenie BookingCreated do tabeli outbox w tej samej transakcji.
        // Gwarantuje to, ze jesli baza padnie to mamy spojnosc i nic sie nie wykona.
        travelRepository.saveOutbox(saved);

        // 7. Optimistic locking (Concurrency Control)
        // TODO [ 4 ] Czy to ma byc w tym miejscu
        // "Dotykamy" hotelu, by wymusic sprawdzenie jego wersji.
        // Jesli inny watek zmienil wersje w miedzyczasie bedzie blad od optimistic locking.
        travelRepository.forceOptimisticLocking(hotel);

        return saved.id();
    }
}
