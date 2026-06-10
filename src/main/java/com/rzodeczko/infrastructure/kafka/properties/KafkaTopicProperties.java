package com.rzodeczko.infrastructure.kafka.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topics.bookings")
public record KafkaTopicProperties (
        String name
) { }
