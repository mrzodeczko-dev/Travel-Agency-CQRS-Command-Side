package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.infrastructure.kafka.properties.BookingsTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import org.apache.avro.specific.SpecificRecordBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingOutboxSchedulerTest {

    @Mock
    private JpaOutboxRepository jpaOutboxRepository;
    @Mock
    private JpaDeadLetterRepository jpaDeadLetterRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    private BookingOutboxScheduler scheduler;

    private static final OutboxProperties OUTBOX_PROPS = new OutboxProperties(1000L, 50, 5);
    private static final BookingsTopicProperties TOPIC_PROPS = new BookingsTopicProperties("travel.bookings");

    private static final String VALID_PAYLOAD =
            """
                    {"id":1,"hotelId":2,"userId":3,"start":"2027-01-10","end":"2027-01-15"}
                    """;
    private static final Booking DESERIALIZED_BOOKING =
            new Booking(1L, 2L, 3L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15));

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler = new BookingOutboxScheduler(
                jpaOutboxRepository,
                jpaDeadLetterRepository,
                OUTBOX_PROPS,
                kafkaTemplate,
                TOPIC_PROPS,
                objectMapper,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void processOutbox_emptyOutbox_doesNothing() {
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        assertThatCode(() -> scheduler.processOutbox()).doesNotThrowAnyException();

        verify(jpaOutboxRepository, never()).delete(any());
        verify(jpaOutboxRepository, never()).save(any());
    }

    @Test
    void processOutbox_successfulEntry_deletedFromOutbox() {
        OutboxEntity entry = buildEntry(0);
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), eq(Booking.class))).thenReturn(DESERIALIZED_BOOKING);

        scheduler.processOutbox();

        verify(jpaOutboxRepository).delete(entry);
        verify(jpaDeadLetterRepository, never()).save(any());
    }

    @Test
    void processOutbox_failingEntry_belowMaxRetries_incrementsRetryCountAndSavesBack() {
        OutboxEntity entry = buildEntry(2);
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), ArgumentMatchers.<Class<Booking>>any()))
                .thenThrow(new RuntimeException("deserialization error"));

        scheduler.processOutbox();

        assertThat(entry.getRetryCount()).isEqualTo(3);
        verify(jpaOutboxRepository).save(entry);
        verify(jpaOutboxRepository, never()).delete(any());
        verify(jpaDeadLetterRepository, never()).save(any());
    }

    @Test
    void processOutbox_failingEntry_exceedsMaxRetries_movedToDeadLetterAndDeletedFromOutbox() {
        OutboxEntity entry = buildEntry(5);
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), ArgumentMatchers.<Class<Booking>>any()))
                .thenThrow(new RuntimeException("deserialization error"));

        scheduler.processOutbox();

        verify(jpaDeadLetterRepository).save(any());
        verify(jpaOutboxRepository).delete(entry);
        verify(jpaOutboxRepository, never()).save(entry);
    }

    @Test
    void processOutbox_firstEntryFails_remainingEntriesSkippedToPreserveOrder() {
        OutboxEntity failing = buildEntry(0);
        OutboxEntity skipped = buildEntry(0);
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(failing, skipped));
        when(objectMapper.readValue(anyString(), eq(Booking.class)))
                .thenThrow(new RuntimeException("first fails"));

        scheduler.processOutbox();

        verify(jpaOutboxRepository).save(failing);
        verify(jpaOutboxRepository, never()).delete(any());
    }

    @Test
    void processOutbox_fetchesBatchWithConfiguredSize() {
        OutboxProperties customProps = new OutboxProperties(1000L, 25, 5);
        scheduler = new BookingOutboxScheduler(
                jpaOutboxRepository, jpaDeadLetterRepository,
                customProps, kafkaTemplate, TOPIC_PROPS, objectMapper,
                new SimpleMeterRegistry()
        );
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        scheduler.processOutbox();

        verify(jpaOutboxRepository).findAllByTypeInOrderByCreatedAtAsc(
                List.of("BookingCreated", "BookingCancelled"),
                PageRequest.of(0, 25));
    }

    @Test
    void supportedTypes_returnsBookingTypes() {
        assertThat(scheduler.supportedTypes())
                .containsExactly("BookingCreated", "BookingCancelled");
    }


    private OutboxEntity buildEntry(int retryCount) {
        return OutboxEntity.builder()
                .id(UUID.randomUUID())
                .aggregateId("1")
                .type("BookingCreated")
                .payload(VALID_PAYLOAD)
                .createdAt(LocalDateTime.now())
                .retryCount(retryCount)
                .build();
    }
}
