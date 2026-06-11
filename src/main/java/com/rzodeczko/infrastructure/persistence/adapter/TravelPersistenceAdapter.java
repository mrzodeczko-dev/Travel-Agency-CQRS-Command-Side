package com.rzodeczko.infrastructure.persistence.adapter;


import com.rzodeczko.application.port.out.AvailabilityRepository;
import com.rzodeczko.application.port.out.BookingRepository;
import com.rzodeczko.application.port.out.HotelRepository;
import com.rzodeczko.application.port.out.OutboxRepository;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.DailyAvailability;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityEntity;
import com.rzodeczko.infrastructure.persistence.mapper.TravelMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaBookingRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaDailyAvailabilityRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaHotelRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Component
@RequiredArgsConstructor
public class TravelPersistenceAdapter implements
        HotelRepository,
        BookingRepository,
        AvailabilityRepository,
        OutboxRepository {

    private final JpaHotelRepository jpaHotelRepository;
    private final JpaBookingRepository jpaBookingRepository;
    private final JpaOutboxRepository jpaOutboxRepository;
    private final TravelMapper travelMapper;
    private final JpaDailyAvailabilityRepository jpaDailyAvailabilityRepository;

    @Override
    public Optional<Hotel> findHotel(Long id) {
        return jpaHotelRepository
                .findById(id)
                .map(travelMapper::toHotelDomain);
    }

    @Override
    public Hotel saveHotel(Hotel hotel) {
        HotelEntity entity = travelMapper.toHotelEntity(hotel);
        HotelEntity saved = jpaHotelRepository.save(entity);
        return travelMapper.toHotelDomain(saved);
    }

    @Override
    public Booking save(Booking booking) {
        var entity = travelMapper.toBookingEntity(booking);
        var saved = jpaBookingRepository.save(entity);
        return travelMapper.toBookingDomain(saved);
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return jpaBookingRepository.findById(id)
                .map(travelMapper::toBookingDomain);
    }

    @Override
    public void saveOutbox(Booking booking) {
        var outbox = travelMapper.toOutboxEntity(booking);
        jpaOutboxRepository.save(outbox);
    }

    @Override
    public void saveOutboxCancellation(Booking booking) {
        var outbox = travelMapper.toCancellationOutboxEntity(booking);
        jpaOutboxRepository.save(outbox);
    }

    @Override
    public void saveHotelOutbox(Hotel hotel) {
        var outbox = travelMapper.toHotelOutboxEntity(hotel);
        jpaOutboxRepository.save(outbox);
    }


    @Override
    public void reserveAvailability(Long hotelId, int capacity, LocalDate start, LocalDate end) {

        Map<LocalDate, DailyAvailabilityEntity> existingSlots = jpaDailyAvailabilityRepository
                .findAndLockByHotelAndDateRange(hotelId, start, end)
                .stream()
                .collect(Collectors.toMap(DailyAvailabilityEntity::getDate, Function.identity()));


        List<DailyAvailabilityEntity> toSave = start
                .datesUntil(end.plusDays(1))
                .map(date -> reserveSlot(existingSlots, hotelId, date, capacity))
                .toList();

        jpaDailyAvailabilityRepository.saveAll(toSave);
    }

    @Override
    public void releaseAvailability(Long hotelId, LocalDate start, LocalDate end) {
        List<DailyAvailabilityEntity> slots = jpaDailyAvailabilityRepository
                .findAndLockByHotelAndDateRange(hotelId, start, end);

        for (DailyAvailabilityEntity slot : slots) {
            DailyAvailability availability = travelMapper.toDailyAvailabilityDomain(slot);
            availability.releaseOne();
            slot.setOccupiedRooms(availability.occupiedRooms());
        }

        jpaDailyAvailabilityRepository.saveAll(slots);
    }

    private DailyAvailabilityEntity reserveSlot(
            Map<LocalDate, DailyAvailabilityEntity> existingSlots,
            Long hotelId,
            LocalDate date,
            int capacity
    ) {

        DailyAvailabilityEntity existing = existingSlots.get(date);
        DailyAvailability availability = existing != null ? travelMapper.toDailyAvailabilityDomain(existing) : new DailyAvailability(0, hotelId, date);

        availability.reserveOne(capacity);

        if (existing != null) {
            existing.setOccupiedRooms(availability.occupiedRooms());
            return existing;
        }

        return travelMapper.toDailyAvailabilityEntity(availability);
    }

}
