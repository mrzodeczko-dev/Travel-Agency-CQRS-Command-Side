package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.infrastructure.persistence.repository.JpaDeadLetterRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.CrudRepository;

@Configuration
public class ObservabilityConfiguration {

    public ObservabilityConfiguration(
            MeterRegistry meterRegistry,
            JpaOutboxRepository outboxRepository,
            JpaDeadLetterRepository deadLetterRepository) {

        Gauge.builder("outbox_backlog", outboxRepository, CrudRepository::count)
                .description("Number of pending outbox entries waiting to be published")
                .register(meterRegistry);

        Gauge.builder("outbox_dead_letter_backlog", deadLetterRepository, CrudRepository::count)
                .description("Number of entries in the dead letter table")
                .register(meterRegistry);
    }
}
