package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.infrastructure.kafka.properties.HotelTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelOutboxSchedulerTest {

    @Mock
    private JpaOutboxRepository jpaOutboxRepository;
    @Mock
    private JpaDeadLetterRepository jpaDeadLetterRepository;
    @Mock
    private KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    private HotelOutboxScheduler scheduler;

    private static final OutboxProperties OUTBOX_PROPS = new OutboxProperties(1000L, 50, 5);
    private static final HotelTopicProperties HOTEL_TOPIC_PROPS = new HotelTopicProperties("travel.hotels");

    private static final String VALID_PAYLOAD = """
            {"hotelId":10,"capacity":200}
            """;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler = new HotelOutboxScheduler(
                jpaOutboxRepository,
                jpaDeadLetterRepository,
                OUTBOX_PROPS,
                kafkaTemplate,
                HOTEL_TOPIC_PROPS,
                JsonMapper.builder().build(),
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

        scheduler.processOutbox();

        verify(jpaOutboxRepository).delete(entry);
        verify(jpaDeadLetterRepository, never()).save(any());
    }

    @Test
    void processOutbox_failingEntry_belowMaxRetries_incrementsAndSaves() {
        OutboxEntity entry = buildEntry(2);
        entry.setPayload("invalid-json");
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(entry));

        scheduler.processOutbox();

        assertThat(entry.getRetryCount()).isEqualTo(3);
        verify(jpaOutboxRepository).save(entry);
        verify(jpaOutboxRepository, never()).delete(any());
    }

    @Test
    void processOutbox_failingEntry_exceedsMaxRetries_movedToDeadLetter() {
        OutboxEntity entry = buildEntry(5);
        entry.setPayload("invalid-json");
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(entry));

        scheduler.processOutbox();

        verify(jpaDeadLetterRepository).save(any());
        verify(jpaOutboxRepository).delete(entry);
    }

    @Test
    void processOutbox_fetchesBatchWithConfiguredSize() {
        OutboxProperties customProps = new OutboxProperties(1000L, 30, 5);
        scheduler = new HotelOutboxScheduler(
                jpaOutboxRepository, jpaDeadLetterRepository,
                customProps, kafkaTemplate, HOTEL_TOPIC_PROPS,
                JsonMapper.builder().build(),
                new SimpleMeterRegistry()
        );
        when(jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        scheduler.processOutbox();

        verify(jpaOutboxRepository).findAllByTypeInOrderByCreatedAtAsc(
                List.of("HotelUpserted"),
                PageRequest.of(0, 30));
    }

    @Test
    void supportedTypes_returnsHotelUpserted() {
        assertThat(scheduler.supportedTypes()).containsExactly("HotelUpserted");
    }

    private OutboxEntity buildEntry(int retryCount) {
        return OutboxEntity.builder()
                .id(UUID.randomUUID())
                .aggregateId("10")
                .type("HotelUpserted")
                .payload(VALID_PAYLOAD)
                .createdAt(LocalDateTime.now())
                .retryCount(retryCount)
                .build();
    }
}
