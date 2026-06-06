package com.rzodeczko.infrastructure.configuration;


import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.application.port.out.AvailabilityRepository;
import com.rzodeczko.application.port.out.BookingRepository;
import com.rzodeczko.application.port.out.HotelRepository;
import com.rzodeczko.application.port.out.OutboxRepository;
import com.rzodeczko.application.service.BookingService;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateDeserializer;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateSerializer;
import com.rzodeczko.infrastructure.kafka.properties.KafkaTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.tx.RetryingCreateBookingUseCase;
import com.rzodeczko.infrastructure.tx.TransactionalCreateBookingUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;

@Configuration
@EnableConfigurationProperties({KafkaTopicProperties.class, OutboxProperties.class})
public class BeansConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
        SimpleModule customDateModule = new SimpleModule();
        customDateModule.addSerializer(LocalDate.class, new CustomLocalDateSerializer());
        customDateModule.addDeserializer(LocalDate.class, new CustomLocalDateDeserializer());

        return JsonMapper.builder()
                .addModule(customDateModule)
                .build();
    }

    @Bean
    public BookingService bookingService(
            AvailabilityRepository availabilityRepository,
            HotelRepository hotelRepository,
            BookingRepository bookingRepository,
            OutboxRepository outboxRepository
    ) {
        return new BookingService(availabilityRepository, hotelRepository, bookingRepository, outboxRepository);
    }


    @Bean
    @Qualifier("plainTransactional")
    public CreateBookingUseCase plainTransactional(BookingService bookingService) {
        return new TransactionalCreateBookingUseCase(bookingService);
    }

    @Bean
    @Qualifier("transactionalCreateBookingUseCase")
    public CreateBookingUseCase transactionalCreateBookingUseCase(@Qualifier("plainTransactional") CreateBookingUseCase createBookingUseCase) {
        return new RetryingCreateBookingUseCase(createBookingUseCase);
    }
}
