package com.rzodeczko.application.port.in;

import com.rzodeczko.application.command.CancelBookingCommand;

public interface CancelBookingUseCase {
    void cancelBooking(CancelBookingCommand command);
}
