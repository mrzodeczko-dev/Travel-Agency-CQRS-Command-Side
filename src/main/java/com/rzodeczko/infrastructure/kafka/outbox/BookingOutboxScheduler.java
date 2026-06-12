package com.rzodeczko.infrastructure.kafka.outbox;

import com.rzodeczko.avro.BookingEventAvro;
import com.rzodeczko.avro.EventType;
import com.rzodeczko.domain.model.Booking;
import com.rzodeczko.infrastructure.kafka.properties.BookingsTopicProperties;
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
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Slf4j
public class BookingOutboxScheduler extends AbstractOutboxScheduler {

    private static final List<String> TYPES = List.of("BookingCreated", "BookingCancelled");

    private final BookingsTopicProperties bookingsTopicProperties;
    private final ObjectMapper objectMapper;

    public BookingOutboxScheduler(
            JpaOutboxRepository jpaOutboxRepository,
            JpaDeadLetterRepository jpaDeadLetterRepository,
            OutboxProperties outboxProperties,
            KafkaTemplate<String, SpecificRecordBase> kafkaTemplate,
            BookingsTopicProperties bookingsTopicProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        super(jpaOutboxRepository, jpaDeadLetterRepository, outboxProperties, kafkaTemplate, meterRegistry);
        this.bookingsTopicProperties = bookingsTopicProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval}")
    @SchedulerLock(name = "bookingOutboxLock", lockAtLeastFor = "PT1s", lockAtMostFor = "PT30s")
    protected void processOutbox() {
        super.processOutbox();
    }

    @Override
    protected List<String> supportedTypes() {
        return TYPES;
    }

    @Override
    protected String resolveTopic(OutboxEntity entry) {
        return bookingsTopicProperties.name();
    }

    @Override
    protected SpecificRecordBase toAvro(OutboxEntity entry) {
        try {
            Booking booking = objectMapper.readValue(entry.getPayload(), Booking.class);
            EventType eventType = EventType.valueOf(entry.getType());

            return BookingEventAvro.newBuilder()
                    .setId(booking.id())
                    .setEventType(eventType)
                    .setHotelId(booking.hotelId())
                    .setUserId(booking.userId())
                    .setStart(booking.start().toString())
                    .setEnd(booking.end().toString())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert outbox payload to BookingEventAvro", e);
        }
    }
}
