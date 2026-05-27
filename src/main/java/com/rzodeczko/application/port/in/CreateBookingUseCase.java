package com.rzodeczko.application.port.in;


import com.rzodeczko.application.command.CreateBookingCommand;

public interface CreateBookingUseCase {
    Long createBooking(CreateBookingCommand command);
}
