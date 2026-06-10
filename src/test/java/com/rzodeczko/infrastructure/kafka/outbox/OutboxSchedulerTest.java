package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.infrastructure.kafka.properties.KafkaTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import org.apache.avro.specific.SpecificRecordBase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock private JpaOutboxRepository jpaOutboxRepository;
    @Mock private JpaDeadLetterRepository jpaDeadLetterRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    private OutboxScheduler outboxScheduler;

    private static final OutboxProperties OUTBOX_PROPS = new OutboxProperties(1000L, 50, 5);
    private static final KafkaTopicProperties TOPIC_PROPS = new KafkaTopicProperties("travel.bookings");

    private static final String VALID_PAYLOAD =
            """
            {"id":1,"hotelId":2,"userId":3,"start":"2027-01-10","end":"2027-01-15"}
            """;
    private static final Booking DESERIALIZED_BOOKING =
            new Booking(1L, 2L, 3L, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 15));

    @BeforeEach
    void setUp() {
        outboxScheduler = new OutboxScheduler(
                jpaOutboxRepository,
                jpaDeadLetterRepository,
                TOPIC_PROPS,
                OUTBOX_PROPS,
                objectMapper,
                kafkaTemplate
        );
    }

    @Test
    void processOutbox_emptyOutbox_doesNothing() {
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of());

        assertThatCode(() -> outboxScheduler.processOutbox()).doesNotThrowAnyException();

        verify(jpaOutboxRepository, never()).delete(any());
        verify(jpaOutboxRepository, never()).save(any());
    }

    @Test
    void processOutbox_successfulEntry_deletedFromOutbox() {
        OutboxEntity entry = buildEntry(0);
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), eq(Booking.class))).thenReturn(DESERIALIZED_BOOKING);

        outboxScheduler.processOutbox();

        verify(jpaOutboxRepository).delete(entry);
        verify(jpaDeadLetterRepository, never()).save(any());
    }

    @Test
    void processOutbox_failingEntry_belowMaxRetries_incrementsRetryCountAndSavesBack() {
        OutboxEntity entry = buildEntry(2);
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), ArgumentMatchers.<Class<Booking>>any()))
                .thenThrow(new RuntimeException("deserialization error"));

        outboxScheduler.processOutbox();

        assertThat(entry.getRetryCount()).isEqualTo(3);
        verify(jpaOutboxRepository).save(entry);
        verify(jpaOutboxRepository, never()).delete(any());
        verify(jpaDeadLetterRepository, never()).save(any());
    }

    @Test
    void processOutbox_failingEntry_exceedsMaxRetries_movedToDeadLetterAndDeletedFromOutbox() {
        OutboxEntity entry = buildEntry(5);
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), ArgumentMatchers.<Class<Booking>>any()))
                .thenThrow(new RuntimeException("deserialization error"));

        outboxScheduler.processOutbox();

        verify(jpaDeadLetterRepository).save(any());
        verify(jpaOutboxRepository).delete(entry);
        verify(jpaOutboxRepository, never()).save(entry);
    }

    @Test
    void processOutbox_firstEntryFails_remainingEntriesStillProcessed() {
        OutboxEntity failing = buildEntry(0);
        OutboxEntity succeeding = buildEntry(0);
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(failing, succeeding));
        when(objectMapper.readValue(anyString(), eq(Booking.class)))
                .thenThrow(new RuntimeException("first fails"))
                .thenReturn(DESERIALIZED_BOOKING);

        outboxScheduler.processOutbox();

        verify(jpaOutboxRepository).save(failing);
        verify(jpaOutboxRepository).delete(succeeding);
    }

    @Test
    void processOutbox_fetchesBatchWithConfiguredSize() {
        OutboxProperties customProps = new OutboxProperties(1000L, 25, 5);
        outboxScheduler = new OutboxScheduler(
                jpaOutboxRepository, jpaDeadLetterRepository,
                TOPIC_PROPS, customProps, objectMapper, kafkaTemplate
        );
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of());

        outboxScheduler.processOutbox();

        verify(jpaOutboxRepository).findAllByOrderByCreatedAtAsc(PageRequest.of(0, 25));
    }

    @Test
    void processOutbox_unknownEventType_treatedAsFailureAndSavedBack() {
        // readValue succeeds, ale switch default rzuca IllegalArgumentException
        // która jest złapana przez toAvro i zawinięta w RuntimeException → handleFailure
        OutboxEntity entry = buildEntryWithType(0, "UnknownEventType");
        when(jpaOutboxRepository.findAllByOrderByCreatedAtAsc(any())).thenReturn(List.of(entry));
        when(objectMapper.readValue(anyString(), eq(Booking.class))).thenReturn(DESERIALIZED_BOOKING);

        outboxScheduler.processOutbox();

        assertThat(entry.getRetryCount()).isEqualTo(1);
        verify(jpaOutboxRepository).save(entry);
        verify(jpaOutboxRepository, never()).delete(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OutboxEntity buildEntry(int retryCount) {
        return buildEntryWithType(retryCount, "BookingCreated");
    }

    private OutboxEntity buildEntryWithType(int retryCount, String type) {
        return OutboxEntity.builder()
                .id(UUID.randomUUID())
                .aggregateId("1")
                .type(type)
                .payload(VALID_PAYLOAD)
                .createdAt(LocalDateTime.now())
                .retryCount(retryCount)
                .build();
    }
}
