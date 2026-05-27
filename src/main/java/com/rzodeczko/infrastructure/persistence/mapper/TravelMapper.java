package com.rzodeczko.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TravelMapper {
    private final ObjectMapper objectMapper;

    public Hotel toHotelDomain(HotelEntity entity) {
        return new Hotel(
                entity.getId(),
                entity.getCapacity()
        );
    }

    public Booking toBookingDomain(BookingEntity entity) {
        return new Booking(
                entity.getId(),
                entity.getHotelId(),
                entity.getUserId(),
                entity.getStartDate(),
                entity.getEndDate()
        );
    }

    public BookingEntity toBookingEntity(Booking booking) {
        return new BookingEntity(
                booking.id(),
                booking.hotelId(),
                booking.userId(),
                booking.start(),
                booking.end()
        );
    }

    public OutboxEntity toOutboxEntity(Booking booking) {
        try {
            String payloadJson = objectMapper.writeValueAsString(booking);

            return OutboxEntity
                    .builder()
                    .aggregateId(booking.hotelId().toString())
                    .type("BookingCreated")
                    .payload(payloadJson)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing Booking to JSON for Outbox", e);
        }
    }
}
