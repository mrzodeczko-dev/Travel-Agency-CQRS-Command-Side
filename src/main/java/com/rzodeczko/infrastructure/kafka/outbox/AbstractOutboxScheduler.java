package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.DeadLetterEntity;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
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

    protected AbstractOutboxScheduler(
            JpaOutboxRepository jpaOutboxRepository,
            JpaDeadLetterRepository jpaDeadLetterRepository,
            OutboxProperties outboxProperties,
            KafkaTemplate<String, SpecificRecordBase> kafkaTemplate) {
        this.jpaOutboxRepository = jpaOutboxRepository;
        this.jpaDeadLetterRepository = jpaDeadLetterRepository;
        this.outboxProperties = outboxProperties;
        this.kafkaTemplate = kafkaTemplate;
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
                sendToKafka(entry);
                jpaOutboxRepository.delete(entry);
            } catch (Exception e) {
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
