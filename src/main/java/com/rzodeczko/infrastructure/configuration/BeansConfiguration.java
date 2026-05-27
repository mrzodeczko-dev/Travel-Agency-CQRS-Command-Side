package com.rzodeczko.infrastructure.configuration;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.application.port.out.TravelRepository;
import com.rzodeczko.application.service.BookingService;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateDeserializer;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateSerializer;
import com.rzodeczko.infrastructure.kafka.properties.KafkaTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.tx.TransactionalCreateBookingUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
@EnableConfigurationProperties({KafkaTopicProperties.class, OutboxProperties.class})
public class BeansConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        SimpleModule customDateModule = new SimpleModule();
        customDateModule.addSerializer(LocalDate.class, new CustomLocalDateSerializer());
        customDateModule.addDeserializer(LocalDate.class, new CustomLocalDateDeserializer());
        mapper.registerModule(customDateModule);

        return mapper;
    }

    @Bean
    public BookingService bookingService(TravelRepository travelRepository) {
        return new BookingService(travelRepository);
    }

    @Bean
    @Qualifier("transactionalCreateBookingUseCase")
    public CreateBookingUseCase transactionalCreateBookingUseCase(BookingService bookingService) {
        return new TransactionalCreateBookingUseCase(bookingService);
    }
}
