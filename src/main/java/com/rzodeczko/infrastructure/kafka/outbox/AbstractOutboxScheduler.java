package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.DeadLetterEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
public abstract class AbstractOutboxScheduler {

    protected final JpaOutboxRepository jpaOutboxRepository;
    protected final JpaDeadLetterRepository jpaDeadLetterRepository;
    protected final OutboxProperties outboxProperties;
    protected final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private final Timer publishTimer;

    protected AbstractOutboxScheduler(
            JpaOutboxRepository jpaOutboxRepository,
            JpaDeadLetterRepository jpaDeadLetterRepository,
            OutboxProperties outboxProperties,
            KafkaTemplate<String, SpecificRecordBase> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.jpaOutboxRepository = jpaOutboxRepository;
        this.jpaDeadLetterRepository = jpaDeadLetterRepository;
        this.outboxProperties = outboxProperties;
        this.kafkaTemplate = kafkaTemplate;

        String schedulerName = getClass().getSimpleName();
        this.publishedCounter = Counter.builder("outbox_events_published")
                .tag("scheduler", schedulerName)
                .description("Number of outbox events successfully published to Kafka")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("outbox_events_failed")
                .tag("scheduler", schedulerName)
                .description("Number of outbox event publish failures")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("outbox_events_dead_lettered")
                .tag("scheduler", schedulerName)
                .description("Number of outbox events moved to dead letter table")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("outbox_publish_duration")
                .tag("scheduler", schedulerName)
                .description("Time spent publishing a single outbox event to Kafka")
                .register(meterRegistry);
    }

    protected abstract List<String> supportedTypes();

    protected abstract String resolveTopic(OutboxEntity entry);

    protected abstract SpecificRecordBase toAvro(OutboxEntity entry);

    protected void processOutbox() {
        List<OutboxEntity> entries = jpaOutboxRepository.findAllByTypeInOrderByCreatedAtAsc(
                supportedTypes(),
                PageRequest.of(0, outboxProperties.batchSize()));

        for (OutboxEntity entry : entries) {
            try {
                publishTimer.record(() -> sendToKafka(entry));
                jpaOutboxRepository.delete(entry);
                publishedCounter.increment();
            } catch (Exception e) {
                failedCounter.increment();
                handleFailure(entry, e);
                break;
            }
        }
    }

    private void sendToKafka(OutboxEntity entry) {
        var record = new ProducerRecord<>(
                resolveTopic(entry),
                entry.getAggregateId(),
                toAvro(entry));
        try {
            kafkaTemplate.send(record).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending outbox entry", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka send failed for outbox entry " + entry.getId(), e.getCause());
        }
    }

    private void handleFailure(OutboxEntity entry, Exception e) {
        entry.incrementRetryCount();
        if (entry.hasExceededMaxRetries(outboxProperties.maxRetries())) {
            deadLetterCounter.increment();
            moveToDeadLetter(entry, e);
            jpaOutboxRepository.delete(entry);
            log.error("Outbox entry {} moved to DLT after {} retries: {}",
                    entry.getId(), outboxProperties.maxRetries(), e.getMessage(), e);
        } else {
            jpaOutboxRepository.save(entry);
            log.warn("Outbox entry {} failed (attempt {}/{}): {}",
                    entry.getId(), entry.getRetryCount(), outboxProperties.maxRetries(), e.getMessage(), e);
        }
    }

    private void moveToDeadLetter(OutboxEntity entry, Exception e) {
        var deadLetter = DeadLetterEntity.builder()
                .originalOutboxId(entry.getId())
                .aggregateId(entry.getAggregateId())
                .type(entry.getType())
                .payload(entry.getPayload())
                .errorMessage(e.getMessage())
                .createdAt(entry.getCreatedAt())
                .failedAt(LocalDateTime.now())
                .retryCount(entry.getRetryCount())
                .build();
        jpaDeadLetterRepository.save(deadLetter);
    }
}
