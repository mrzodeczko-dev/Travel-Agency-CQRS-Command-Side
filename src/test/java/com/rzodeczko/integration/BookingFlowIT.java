package com.rzodeczko.integration;

import com.rzodeczko.domain.model.BookingStatus;
import com.rzodeczko.infrastructure.kafka.outbox.BookingOutboxScheduler;
import com.rzodeczko.infrastructure.kafka.outbox.OutboxTestHelper;
import com.rzodeczko.infrastructure.persistence.entity.BookingEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityEntity;
import com.rzodeczko.infrastructure.persistence.entity.DailyAvailabilityId;
import com.rzodeczko.infrastructure.persistence.entity.HotelEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaBookingRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaDailyAvailabilityRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaHotelRepository;
import com.rzodeczko.presentation.dto.CreateBookingRequestDto;
import com.rzodeczko.presentation.dto.CreateBookingResponseDto;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

@AutoConfigureRestTestClient
@IntegrationTest
class BookingFlowIT extends AbstractIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JpaHotelRepository hotelRepository;

    @Autowired
    private JpaBookingRepository bookingRepository;

    @Autowired
    private JpaDailyAvailabilityRepository dailyAvailabilityRepository;

    @Autowired
    private BookingOutboxScheduler bookingOutboxScheduler;

    // Create booking

    @Test
    void createBooking_fullFlow_persistsAndPublishesEvent() {
        HotelEntity hotel = hotelRepository.save(HotelEntity.builder().capacity(10L).build());
        LocalDate start = LocalDate.now().plusDays(30);
        LocalDate end = LocalDate.now().plusDays(32);

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            CreateBookingResponseDto responseBody = restTestClient.post()
                    .uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateBookingRequestDto(hotel.getId(), 1L, start, end))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(CreateBookingResponseDto.class)
                    .returnResult()
                    .getResponseBody();

            Long bookingId = responseBody.bookingId();
            assertThat(bookingId).isNotNull();

            // booking persisted
            BookingEntity booking = bookingRepository.findById(bookingId).orElseThrow();
            assertThat(booking.getHotelId()).isEqualTo(hotel.getId());
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);

            // availability updated (3 days: start, start+1, start+2)
            assertThat(dailyAvailabilityRepository.findAll()).hasSize(3);

            // Kafka event
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);
            List<GenericRecord> messages = consumer.drain(1, Duration.ofSeconds(10));

            assertThat(messages).hasSize(1);
            GenericRecord event = messages.getFirst();
            assertThat(event.get("id")).isEqualTo(bookingId);
            assertThat(event.get("hotelId")).isEqualTo(hotel.getId());
            assertThat(event.get("eventType").toString()).isEqualTo("BookingCreated");
        }
    }

    // Cancel booking

    @Test
    void cancelBooking_fullFlow_releasesAvailabilityAndPublishesCancellation() {
        HotelEntity hotel = hotelRepository.save(HotelEntity.builder().capacity(10L).build());
        LocalDate start = LocalDate.now().plusDays(40);
        LocalDate end = LocalDate.now().plusDays(41);

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            // create booking first
            CreateBookingResponseDto createResponse = restTestClient.post()
                    .uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreateBookingRequestDto(hotel.getId(), 1L, start, end))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(CreateBookingResponseDto.class)
                    .returnResult()
                    .getResponseBody();
            Long bookingId = createResponse.bookingId();

            // flush creation outbox to Kafka
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);

            // cancel
            restTestClient.delete()
                    .uri("/api/bookings/{id}", bookingId)
                    .exchange()
                    .expectStatus().isNoContent();

            // booking status updated
            BookingEntity cancelled = bookingRepository.findById(bookingId).orElseThrow();
            assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

            // availability released
            DailyAvailabilityEntity slot = dailyAvailabilityRepository
                    .findById(new DailyAvailabilityId(hotel.getId(), start))
                    .orElseThrow();
            assertThat(slot.getOccupiedRooms()).isEqualTo(0);

            // cancellation event on Kafka
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);
            List<GenericRecord> messages = consumer.drain(2, Duration.ofSeconds(10));

            assertThat(messages).hasSize(2);
            assertThat(messages)
                    .extracting(
                            g -> g.get("eventType").toString(),
                            g -> g.get("id")
                    ).containsExactlyInAnyOrder(
                            tuple("BookingCancelled", bookingId),
                            tuple("BookingCreated", bookingId));
        }
    }

    //  Overbooking via REST

    @Test
    void createBooking_overbooking_returns409() {
        HotelEntity hotel = hotelRepository.save(HotelEntity.builder().capacity(1L).build());
        LocalDate start = LocalDate.now().plusDays(50);
        LocalDate end = LocalDate.now().plusDays(50);

        // first booking succeeds
        restTestClient.post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBookingRequestDto(hotel.getId(), 1L, start, end))
                .exchange()
                .expectStatus().isCreated();

        // second booking on same date - 409 Conflict
        restTestClient.post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateBookingRequestDto(hotel.getId(), 2L, start, end))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
}
