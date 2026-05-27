package com.rzodeczko.domain.model;

public class Hotel {
    private final Long id;
    private final int capacity;

    public Hotel(Long id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public Long getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }
}
