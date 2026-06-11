package com.rzodeczko.application.port.out;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;

public interface OutboxRepository {
    void saveOutbox(Booking booking);
    void saveOutboxCancellation(Booking booking);
    void saveHotelOutbox(Hotel hotel);
}
