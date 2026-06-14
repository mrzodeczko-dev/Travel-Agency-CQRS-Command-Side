package com.rzodeczko.integration.outbox;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.infrastructure.kafka.outbox.BookingOutboxScheduler;
import com.rzodeczko.infrastructure.kafka.outbox.OutboxTestHelper;
import com.rzodeczko.infrastructure.persistence.adapter.TravelPersistenceAdapter;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import com.rzodeczko.integration.AbstractIntegrationTest;
import com.rzodeczko.integration.IntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class BookingOutboxSchedulerIT extends AbstractIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2027, 8, 1);
    private static final LocalDate DATE_END = LocalDate.of(2027, 8, 3);

    @Autowired
    private TravelPersistenceAdapter adapter;

    @Autowired
    private BookingOutboxScheduler bookingOutboxScheduler;

    @Autowired
    private JpaOutboxRepository outboxRepository;

    @Test
    @Sql(statements = "truncate table shedlock cascade")
    void processOutbox_publishesBookingCreatedAvroToKafka() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 10L));
        Booking booking = adapter.save(new Booking(null, hotel.getId(), 1L, DATE, DATE_END));
        adapter.saveOutbox(booking);

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);

            List<GenericRecord> messages = consumer.drain(1, Duration.ofSeconds(10));

            assertThat(messages).hasSize(1);
            GenericRecord event = messages.getFirst();
            assertThat(event.get("id")).isEqualTo(booking.id());
            assertThat(event.get("hotelId")).isEqualTo(hotel.getId());
            assertThat(event.get("userId")).isEqualTo(1L);
            assertThat(event.get("start").toString()).isEqualTo(DATE.toString());
            assertThat(event.get("end").toString()).isEqualTo(DATE_END.toString());
            assertThat(event.get("eventType").toString()).isEqualTo("BookingCreated");
        }

        assertThat(outboxRepository.findAll())
                .as("outbox entry should be deleted after successful publish")
                .isEmpty();
    }

    @Test
    void processOutbox_publishesCancellationAvroToKafka() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 10L));
        Booking booking = adapter.save(new Booking(null, hotel.getId(), 2L, DATE, DATE_END));
        adapter.saveOutboxCancellation(booking);

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);

            List<GenericRecord> messages = consumer.drain(1, Duration.ofSeconds(10));

            assertThat(messages).hasSize(1);
            assertThat(messages.getFirst().get("eventType").toString()).isEqualTo("BookingCancelled");
        }
    }

    @Test
    void processOutbox_withNoEntries_doesNothing() {
        assertThat(outboxRepository.findAll()).isEmpty();

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);

            List<GenericRecord> messages = consumer.drain(1, Duration.ofSeconds(2));
            assertThat(messages).isEmpty();
        }
    }

    @Test
    void processOutbox_multipleBatchEntries_publishesInOrder() {
        Hotel hotel = adapter.saveHotel(new Hotel(null, 50L));

        for (int i = 0; i < 3; i++) {
            Booking booking = adapter.save(
                    new Booking(null, hotel.getId(), (long) i, DATE.plusDays(i), DATE_END.plusDays(i)));
            adapter.saveOutbox(booking);
        }

        assertThat(outboxRepository.findAll()).hasSize(3);

        try (var consumer = subscribeToTopic("travel.bookings.test")) {
            OutboxTestHelper.triggerOutbox(bookingOutboxScheduler);

            List<GenericRecord> messages = consumer.drain(3, Duration.ofSeconds(10));

            assertThat(messages).hasSize(3);
            List<Long> userIds = messages.stream()
                    .map(m -> (Long) m.get("userId"))
                    .toList();
            assertThat(userIds).containsExactly(0L, 1L, 2L);
        }
    }
}
