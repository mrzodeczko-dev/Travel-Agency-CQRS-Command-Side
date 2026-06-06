package com.rzodeczko.domain.model;

import com.rzodeczko.domain.exception.OverbookingException;

import java.time.LocalDate;

public class DailyAvailability {
    private final Long hotelId;
    private final LocalDate date;
    private int occupiedRooms;

    public DailyAvailability(int occupiedRooms, Long hotelId, LocalDate date) {
        this.occupiedRooms = occupiedRooms;
        this.hotelId = hotelId;
        this.date = date;
    }

    public int occupiedRooms() {
        return occupiedRooms;
    }

    public void reserveOne(int capacity) {
        if(occupiedRooms >= capacity) {
            throw new OverbookingException("Hotel %d overbooked on %s. Capacity: %d, occupied: %d "
                    .formatted(hotelId, date, capacity, occupiedRooms));
        }
        ++occupiedRooms;
    }

    public LocalDate date() {
        return date;
    }

    public Long hotelId() {
        return hotelId;
    }
}
