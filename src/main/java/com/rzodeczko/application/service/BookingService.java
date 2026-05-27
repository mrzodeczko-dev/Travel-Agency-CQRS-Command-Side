package com.rzodeczko.application.service;

import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.application.port.out.TravelRepository;
import com.rzodeczko.domain.exception.ResourceNotFoundException;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;

public class BookingService implements CreateBookingUseCase {
    private final TravelRepository travelRepository;

    public BookingService(TravelRepository travelRepository) {
        this.travelRepository = travelRepository;
    }

    @Override
    public Long createBooking(CreateBookingCommand command) {

        if (command.start().isAfter(command.end())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        Hotel hotel = travelRepository.findHotel(command.hotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found"));


        travelRepository.reserveAvailability(
                hotel.getId(),
                hotel.getCapacity(),
                command.start(),
                command.end()
        );

        Booking newBooking = new Booking(
                null, command.hotelId(), command.userId(), command.start(), command.end());
        Booking saved = travelRepository.save(newBooking);

        travelRepository.saveOutbox(saved);

        return saved.id();
    }
}
