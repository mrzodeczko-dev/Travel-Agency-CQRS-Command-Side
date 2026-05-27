package com.rzodeczko.infrastructure.kafka.topic;

import com.rzodeczko.infrastructure.kafka.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaTopicConfig {
    private final KafkaTopicProperties kafkaTopicProperties;

    @Bean
    public NewTopic bookingsTopic() {
        return TopicBuilder
                .name(kafkaTopicProperties.name())
                .partitions(kafkaTopicProperties.partitions())
                .replicas(kafkaTopicProperties.replicas())
                .build();
    }
}
