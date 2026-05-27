package com.rzodeczko.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * W tej encji nie ma jednej kolumny jako klucz główny (np. auto-generowanego Long id). Zamiast tego klucz główny
 * składa się z dwóch kolumn jednocześnie: hotel_id + date. To ma sens biznesowy — w tabeli daily_availabilities
 * chcesz przechowywać informację o zajętości pokoi dla konkretnego hotelu w konkretnym dniu. Para (hotelId, date)
 * naturalnie identyfikuje jeden wiersz — nie może istnieć dwa razy ten sam hotel z tą samą datą.
 *
 * Co to jest @IdClass?
 * @IdClass(DailyAvailabilityId.class) mówi JPA: „klucz główny tej encji jest złożony i jego strukturę opisuje
 * klasa DailyAvailabilityId". Mechanizm działa tak:
 * a. W encji DailyAvailabilityEntity oznaczasz dwa pola adnotacją @Id — hotelId i date.
 * b. Klasa DailyAvailabilityId musi mieć dokładnie te same pola (te same nazwy i typy).
 * c. Klasa ID musi implementować Serializable, mieć equals() / hashCode() (tu zapewnia to Lombok @EqualsAndHashCode)
 *    oraz bezargumentowy konstruktor.
 * Dzięki temu JPA wie, jak stworzyć obiekt klucza, porównywać klucze i np. wyszukiwać encję po złożonym ID.
 */

@Entity
@Table(
        name="daily_availabilities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "date"})
)
@IdClass(DailyAvailabilityId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyAvailabilityEntity {
    @Id
    @Column(name = "hotel_id")
    private Long hotelId;

    @Id
    @Column(name = "date")
    private LocalDate date;

    @Column(name = "occupied_rooms", nullable = false)
    private Integer occupiedRooms;
}
