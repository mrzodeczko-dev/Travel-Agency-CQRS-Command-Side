package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.avro.HotelUpsertedAvro;
import com.rzodeczko.infrastructure.kafka.properties.HotelTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.persistence.entity.OutboxEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Slf4j
public class HotelOutboxScheduler extends AbstractOutboxScheduler {

    private static final List<String> TYPES = List.of("HotelUpserted");

    private final HotelTopicProperties hotelTopicProperties;
    private final ObjectMapper objectMapper;

    public HotelOutboxScheduler(
            JpaOutboxRepository jpaOutboxRepository,
            JpaDeadLetterRepository jpaDeadLetterRepository,
            OutboxProperties outboxProperties,
            KafkaTemplate<String, SpecificRecordBase> kafkaTemplate,
            HotelTopicProperties hotelTopicProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        super(jpaOutboxRepository, jpaDeadLetterRepository, outboxProperties, kafkaTemplate, meterRegistry);
        this.hotelTopicProperties = hotelTopicProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval}")
    @SchedulerLock(name = "hotelOutboxLock", lockAtLeastFor = "PT1s", lockAtMostFor = "PT30s")
    protected void processOutbox() {
        super.processOutbox();
    }

    @Override
    protected List<String> supportedTypes() {
        return TYPES;
    }

    @Override
    protected String resolveTopic(OutboxEntity entry) {
        return hotelTopicProperties.name();
    }

    @Override
    protected SpecificRecordBase toAvro(OutboxEntity entry) {
        try {
            JsonNode node = objectMapper.readTree(entry.getPayload());
            return HotelUpsertedAvro.newBuilder()
                    .setHotelId(node.get("hotelId").asLong())
                    .setCapacity(node.get("capacity").asLong())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert outbox payload to HotelUpsertedAvro", e);
        }
    }
}
