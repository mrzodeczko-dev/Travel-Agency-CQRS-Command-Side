package com.rzodeczko.integration;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Postgres via Testcontainers (singleton per JVM), Kafka via {@code @EmbeddedKafka}
 * (in-process, no Docker), Schema Registry via {@code mock://} (in-memory).
 */
@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

    static final String MOCK_SCHEMA_REGISTRY_URL = "mock://test-registry";

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        System.setProperty("api.version", "1.43");
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
        POSTGRES.start();
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.properties.schema.registry.url", () -> MOCK_SCHEMA_REGISTRY_URL);
    }

    // Kafka test consumer helper

    /**
     * Creates a consumer assigned to partition 0 of the given topic, positioned
     * at the current end offset so old messages from previous tests are ignored.
     * Use inside try-with-resources.
     */
    protected TestKafkaConsumer subscribeToTopic(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        props.put("schema.registry.url", MOCK_SCHEMA_REGISTRY_URL);

        var consumer = new KafkaConsumer<String, GenericRecord>(props);
        var tp = new TopicPartition(topic, 0);
        consumer.assign(List.of(tp));
        long endOffset = consumer.endOffsets(List.of(tp)).get(tp);
        consumer.seek(tp, endOffset);
        return new TestKafkaConsumer(consumer);
    }

    protected static class TestKafkaConsumer implements AutoCloseable {
        private final KafkaConsumer<String, GenericRecord> consumer;

        TestKafkaConsumer(KafkaConsumer<String, GenericRecord> consumer) {
            this.consumer = consumer;
        }

        /**
         * Polls until expectedCount records arrive or timeout expires.
         */
        public List<GenericRecord> drain(int expectedCount, Duration timeout) {
            List<GenericRecord> messages = new ArrayList<>();
            long deadline = System.currentTimeMillis() + timeout.toMillis();

            while (messages.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(200));
                records.forEach(r -> messages.add(r.value()));
            }
            return messages.size() > expectedCount ? messages.subList(0, expectedCount) : messages;
        }

        @Override
        public void close() {
            consumer.close();
        }
    }
}
