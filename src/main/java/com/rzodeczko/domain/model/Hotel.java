package com.rzodeczko.domain.model;

import com.rzodeczko.domain.exception.OverbookingException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class Hotel {
    private Long id;
    private final int capacity;

    // Wersja sluzy tylko do przekazywania jej do warstwy infrastruktury
    // w celu weryfikacji przy zapisie
    private final Long version;

    public Hotel(Long id, int capacity, Long version) {
        this.id = id;
        this.capacity = capacity;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Algorytm BUCKET COUNTING (metoda pudełkowa) — O(N×D), gdzie N = liczba rezerwacji, D = długość pobytu.
     * D jest biznesowo ograniczone (1-30 dni), więc w praktyce traktujemy to jako O(N).
     *
     * Złożoność nie jest O(N^2), bo pętla zewnętrzna zależy od N (rezerwacje w bazie),
     * a wewnętrzna od D (długość pobytu) — dwie niezależne zmienne.
     *
     * Ograniczenia przy dużej skali: duży narzut I/O przy popularnych hotelach + Optimistic Locking
     * blokuje cały hotel nawet przy rezerwacjach w różnych terminach. Alternatywa: tabela pomocnicza
     * z liczbą wolnych pokoi per dzień (Row-Level Locking, szybszy odczyt). Nadal zgodne z CQRS,
     * o ile tabela jest częścią Write Model.
     *
     * @param existingBookings rezerwacje kolidujące z podanym terminem
     * @param reqStart         początek nowej rezerwacji
     * @param reqEnd           koniec nowej rezerwacji
     */
    public void validateAvailability(List<Booking> existingBookings, LocalDate reqStart, LocalDate reqEnd) {
        // Tablica liczników zajętości: indeks 0 = reqStart, ostatni = reqEnd
        int days = (int) ChronoUnit.DAYS.between(reqStart, reqEnd) + 1;
        int[] dailyOccupancy = new int[days];

        for (var existing : existingBookings) {
            // Część wspólna dat (intersection)
            LocalDate overlapStart = existing.start().isAfter(reqStart) ? existing.start() : reqStart;
            LocalDate overlapEnd = existing.end().isBefore(reqEnd) ? existing.end() : reqEnd;

            // Brak części wspólnej gdy overlapStart > overlapEnd (np. req: 1-5.01, existing: 10-15.01)
            if (!overlapStart.isAfter(overlapEnd)) {
                int startIndex = (int) ChronoUnit.DAYS.between(reqStart, overlapStart);
                int endIndex = (int) ChronoUnit.DAYS.between(reqStart, overlapEnd);

                for (int i = startIndex; i <= endIndex; ++i) {
                    // Fail fast: sprawdzamy niezmiennik w locie
                    if (++dailyOccupancy[i] >= capacity) {
                        throw new OverbookingException("Hotel %d overbooked on date %s. Capacity: %d".formatted(
                                id,
                                reqStart.plusDays(i),
                                capacity
                        ));
                    }
                }
            }
        }
    }
}