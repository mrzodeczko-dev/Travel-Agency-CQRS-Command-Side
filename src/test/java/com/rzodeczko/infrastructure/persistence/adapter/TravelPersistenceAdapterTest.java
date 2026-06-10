package com.rzodeczko.infrastructure.persistence.adapter;

import com.rzodeczko.domain.exception.OverbookingException;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityEntity;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.mapper.TravelMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaBookingRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaDailyAvailabilityRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaHotelRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import com.rzodeczko.domain.model.DailyAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TravelPersistenceAdapterTest {

    @Mock private JpaHotelRepository jpaHotelRepository;
    @Mock private JpaBookingRepository jpaBookingRepository;
    @Mock private JpaOutboxRepository jpaOutboxRepository;
    @Mock private TravelMapper travelMapper;
    @Mock private JpaDailyAvailabilityRepository jpaDailyAvailabilityRepository;

    @InjectMocks
    private TravelPersistenceAdapter adapter;

    private static final LocalDate DATE     = LocalDate.of(2027, 6, 1);
    private static final LocalDate DATE_END = LocalDate.of(2027, 6, 3);

    @BeforeEach
    void setUp() {
        lenient().when(travelMapper.toDailyAvailabilityDomain(any())).thenAnswer(inv -> {
            DailyAvailabilityEntity e = inv.getArgument(0);
            return new DailyAvailability(e.getOccupiedRooms(), e.getHotelId(), e.getDate());
        });
        lenient().when(travelMapper.toDailyAvailabilityEntity(any())).thenAnswer(inv -> {
            DailyAvailability a = inv.getArgument(0);
            return DailyAvailabilityEntity.builder()
                    .hotelId(a.hotelId()).date(a.date()).occupiedRooms(a.occupiedRooms()).build();
        });
    }

    // ── findHotel ────────────────────────────────────────────────────────────

    @Test
    void findHotel_found_returnsMappedDomain() {
        HotelEntity entity = HotelEntity.builder().id(1L).capacity(10).build();
        Hotel domain = new Hotel(1L, 10);
        when(jpaHotelRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(travelMapper.toHotelDomain(entity)).thenReturn(domain);

        Optional<Hotel> result = adapter.findHotel(1L);

        assertThat(result).contains(domain);
    }

    @Test
    void findHotel_notFound_returnsEmpty() {
        when(jpaHotelRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Hotel> result = adapter.findHotel(99L);

        assertThat(result).isEmpty();
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToMapperAndRepo_returnsMappedDomain() {
        Booking input   = new Booking(null, 1L, 2L, DATE, DATE_END);
        BookingEntity entity  = BookingEntity.builder().hotelId(1L).userId(2L).startDate(DATE).endDate(DATE_END).build();
        BookingEntity saved   = BookingEntity.builder().id(7L).hotelId(1L).userId(2L).startDate(DATE).endDate(DATE_END).build();
        Booking expected      = new Booking(7L, 1L, 2L, DATE, DATE_END);

        when(travelMapper.toBookingEntity(input)).thenReturn(entity);
        when(jpaBookingRepository.save(entity)).thenReturn(saved);
        when(travelMapper.toBookingDomain(saved)).thenReturn(expected);

        Booking result = adapter.save(input);

        assertThat(result).isEqualTo(expected);
    }

    // ── saveOutbox ───────────────────────────────────────────────────────────

    @Test
    void saveOutbox_delegatesToMapperAndRepo() {
        Booking booking = new Booking(1L, 1L, 2L, DATE, DATE_END);
        OutboxEntity outbox = OutboxEntity.builder().build();
        when(travelMapper.toOutboxEntity(booking)).thenReturn(outbox);

        adapter.saveOutbox(booking);

        verify(jpaOutboxRepository).save(outbox);
    }

    // ── reserveAvailability — new slots ──────────────────────────────────────

    @Test
    void reserveAvailability_noExistingSlot_createsNewSlotWithOccupiedRoomsOne() {
        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, DATE, DATE))
                .thenReturn(List.of());

        adapter.reserveAvailability(1L, 5, DATE, DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyAvailabilityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaDailyAvailabilityRepository).saveAll(captor.capture());

        List<DailyAvailabilityEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getHotelId()).isEqualTo(1L);
        assertThat(saved.get(0).getDate()).isEqualTo(DATE);
        assertThat(saved.get(0).getOccupiedRooms()).isEqualTo(1);
    }

    @Test
    void reserveAvailability_existingSlotBelowCapacity_incrementsOccupiedRooms() {
        DailyAvailabilityEntity existing = DailyAvailabilityEntity.builder()
                .hotelId(1L).date(DATE).occupiedRooms(3).build();
        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, DATE, DATE))
                .thenReturn(List.of(existing));

        adapter.reserveAvailability(1L, 5, DATE, DATE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyAvailabilityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaDailyAvailabilityRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getOccupiedRooms()).isEqualTo(4);
    }

    @Test
    void reserveAvailability_existingSlotAtCapacity_throwsOverbookingException() {
        DailyAvailabilityEntity full = DailyAvailabilityEntity.builder()
                .hotelId(1L).date(DATE).occupiedRooms(5).build();
        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, DATE, DATE))
                .thenReturn(List.of(full));

        assertThatThrownBy(() -> adapter.reserveAvailability(1L, 5, DATE, DATE))
                .isInstanceOf(OverbookingException.class)
                .hasMessageContaining("Hotel 1 overbooked on " + DATE);
    }

    @Test
    void reserveAvailability_multiDateRange_savesAllDates() {
        // DATE..DATE_END = 3 nights: 1, 2, 3 June
        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, DATE, DATE_END))
                .thenReturn(List.of());

        adapter.reserveAvailability(1L, 10, DATE, DATE_END);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyAvailabilityEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(jpaDailyAvailabilityRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void reserveAvailability_secondDateOverbooked_throwsOverbookingExceptionBeforeSave() {
        LocalDate day1 = LocalDate.of(2027, 6, 1);
        LocalDate day2 = LocalDate.of(2027, 6, 2);
        DailyAvailabilityEntity fullDay2 = DailyAvailabilityEntity.builder()
                .hotelId(1L).date(day2).occupiedRooms(5).build();

        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, day1, day2))
                .thenReturn(List.of(fullDay2));

        assertThatThrownBy(() -> adapter.reserveAvailability(1L, 5, day1, day2))
                .isInstanceOf(OverbookingException.class)
                .hasMessageContaining("overbooked on " + day2);

        verify(jpaDailyAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void reserveAvailability_firstDateOverbooked_throwsImmediately() {
        DailyAvailabilityEntity full = DailyAvailabilityEntity.builder()
                .hotelId(1L).date(DATE).occupiedRooms(2).build();
        when(jpaDailyAvailabilityRepository.findAndLockByHotelAndDateRange(1L, DATE, DATE_END))
                .thenReturn(List.of(full));

        assertThatThrownBy(() -> adapter.reserveAvailability(1L, 2, DATE, DATE_END))
                .isInstanceOf(OverbookingException.class);

        verify(jpaDailyAvailabilityRepository, never()).saveAll(any());
    }
}
