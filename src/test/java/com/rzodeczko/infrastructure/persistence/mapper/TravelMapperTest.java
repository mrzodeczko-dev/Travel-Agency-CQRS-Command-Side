package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelMapperTest {

    private static final LocalDate START = LocalDate.of(2027, 6, 1);
    private static final LocalDate END = LocalDate.of(2027, 6, 7);

    @Mock
    private ObjectMapper mockObjectMapper;

    /**
     * Mapper z prawdziwym JsonMapper - do happy-path testów
     */
    private TravelMapper mapper;

    /**
     * Mapper z mockiem ObjectMapper - do testowania ścieżki błędu
     */
    private TravelMapper failingMapper;

    @BeforeEach
    void setUp() {
        mapper = new TravelMapper(JsonMapper.builder().build());
        failingMapper = new TravelMapper(mockObjectMapper);
    }


    @Test
    void toHotelDomain_mapsIdAndCapacity() {
        HotelEntity entity = HotelEntity.builder().id(1L).capacity(5L).build();

        Hotel hotel = mapper.toHotelDomain(entity);

        assertThat(hotel.getId()).isEqualTo(1L);
        assertThat(hotel.getCapacity()).isEqualTo(5);
    }


    @Test
    void toBookingDomain_mapsAllFields() {
        BookingEntity entity = BookingEntity.builder().id(10L).hotelId(1L).userId(2L).startDate(START).endDate(END).build();

        Booking booking = mapper.toBookingDomain(entity);

        assertThat(booking.id()).isEqualTo(10L);
        assertThat(booking.hotelId()).isEqualTo(1L);
        assertThat(booking.userId()).isEqualTo(2L);
        assertThat(booking.start()).isEqualTo(START);
        assertThat(booking.end()).isEqualTo(END);
    }


    @Test
    void toBookingEntity_mapsAllFields() {
        Booking booking = new Booking(7L, 3L, 4L, START, END);

        BookingEntity entity = mapper.toBookingEntity(booking);

        assertThat(entity.getId()).isEqualTo(7L);
        assertThat(entity.getHotelId()).isEqualTo(3L);
        assertThat(entity.getUserId()).isEqualTo(4L);
        assertThat(entity.getStartDate()).isEqualTo(START);
        assertThat(entity.getEndDate()).isEqualTo(END);
    }

    @Test
    void toBookingEntity_nullId_mapsNullId() {
        Booking booking = new Booking(null, 1L, 2L, START, END);

        BookingEntity entity = mapper.toBookingEntity(booking);

        assertThat(entity.getId()).isNull();
    }


    @Test
    void toOutboxEntity_setsAggregateIdFromHotelId() {
        Booking booking = new Booking(1L, 99L, 2L, START, END);

        OutboxEntity outbox = mapper.toOutboxEntity(booking);

        assertThat(outbox.getAggregateId()).isEqualTo("99");
    }

    @Test
    void toOutboxEntity_setsTypeBookingCreated() {
        Booking booking = new Booking(1L, 1L, 2L, START, END);

        OutboxEntity outbox = mapper.toOutboxEntity(booking);

        assertThat(outbox.getType()).isEqualTo("BookingCreated");
    }

    @Test
    void toOutboxEntity_payloadContainsBookingFields() {
        Booking booking = new Booking(5L, 1L, 2L, START, END);

        OutboxEntity outbox = mapper.toOutboxEntity(booking);

        assertThat(outbox.getPayload()).contains("\"id\":5");
        assertThat(outbox.getPayload()).contains("\"hotelId\":1");
        assertThat(outbox.getPayload()).contains("\"userId\":2");
    }

    @Test
    void toOutboxEntity_createdAtIsSet() {
        Booking booking = new Booking(1L, 1L, 2L, START, END);

        OutboxEntity outbox = mapper.toOutboxEntity(booking);

        assertThat(outbox.getCreatedAt()).isNotNull();
    }

    @Test
    void toOutboxEntity_serializationFailure_throwsRuntimeException() {
        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(new JacksonException("forced failure") {
                });

        Booking booking = new Booking(1L, 1L, 2L, START, END);

        assertThatThrownBy(() -> failingMapper.toOutboxEntity(booking))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error serializing Booking to JSON for Outbox");
    }
}
