package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.DailyAvailability;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityEntity;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

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
                entity.getEndDate(),
                entity.getStatus()
        );
    }

    public BookingEntity toBookingEntity(Booking booking) {
        return BookingEntity.builder()
                .id(booking.id())
                .hotelId(booking.hotelId())
                .userId(booking.userId())
                .startDate(booking.start())
                .endDate(booking.end())
                .status(booking.status())
                .build();
    }

    public OutboxEntity toOutboxEntity(Booking booking) {
        return toOutboxEntity(booking, "BookingCreated");
    }

    public OutboxEntity toCancellationOutboxEntity(Booking booking) {
        return toOutboxEntity(booking, "BookingCancelled");
    }

    private OutboxEntity toOutboxEntity(Booking booking, String eventType) {
        try {
            String payloadJson = objectMapper.writeValueAsString(booking);

            return OutboxEntity
                    .builder()
                    .aggregateId(booking.hotelId().toString())
                    .type(eventType)
                    .payload(payloadJson)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (JacksonException e) {
            throw new RuntimeException("Error serializing Booking to JSON for Outbox", e);
        }
    }

    public HotelEntity toHotelEntity(Hotel hotel) {
        return HotelEntity.builder()
                .id(hotel.getId())
                .capacity(hotel.getCapacity())
                .build();
    }

    public OutboxEntity toHotelOutboxEntity(Hotel hotel) {
        try {
            String payloadJson = objectMapper.writeValueAsString(
                    Map.of("hotelId", hotel.getId(), "capacity", hotel.getCapacity()));

            return OutboxEntity.builder()
                    .aggregateId(hotel.getId().toString())
                    .type("HotelUpserted")
                    .payload(payloadJson)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (JacksonException e) {
            throw new RuntimeException("Error serializing Hotel to JSON for Outbox", e);
        }
    }

    public DailyAvailability toDailyAvailabilityDomain(DailyAvailabilityEntity entity) {
        return new DailyAvailability(
                entity.getOccupiedRooms(),
                entity.getHotelId(),
                entity.getDate()
        );
    }

    public DailyAvailabilityEntity toDailyAvailabilityEntity(DailyAvailability dailyAvailability) {
        return DailyAvailabilityEntity.builder()
                .occupiedRooms(dailyAvailability.occupiedRooms())
                .hotelId(dailyAvailability.hotelId())
                .date(dailyAvailability.date())
                .build();
    }
}
