package com.rzodeczko.infrastructure.configuration;


import com.rzodeczko.application.port.in.CancelBookingUseCase;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.application.port.in.CreateHotelUseCase;
import com.rzodeczko.application.port.in.UpdateHotelCapacityUseCase;
import com.rzodeczko.application.port.out.AvailabilityRepository;
import com.rzodeczko.application.port.out.BookingRepository;
import com.rzodeczko.application.port.out.HotelRepository;
import com.rzodeczko.application.port.out.OutboxRepository;
import com.rzodeczko.application.service.BookingService;
import com.rzodeczko.application.service.HotelService;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateDeserializer;
import com.rzodeczko.infrastructure.configuration.serializer.CustomLocalDateSerializer;
import com.rzodeczko.infrastructure.kafka.properties.HotelTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.BookingsTopicProperties;
import com.rzodeczko.infrastructure.kafka.properties.OutboxProperties;
import com.rzodeczko.infrastructure.metrics.InstrumentedBookingService;
import com.rzodeczko.infrastructure.metrics.InstrumentedHotelService;
import com.rzodeczko.infrastructure.tx.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;

@Configuration
@EnableConfigurationProperties({BookingsTopicProperties.class, HotelTopicProperties.class, OutboxProperties.class})
public class BeansConfiguration {
    @Bean
    @Qualifier("objectMapper")
    public ObjectMapper objectMapper() {
        SimpleModule customDateModule = new SimpleModule();
        customDateModule.addSerializer(LocalDate.class, new CustomLocalDateSerializer());
        customDateModule.addDeserializer(LocalDate.class, new CustomLocalDateDeserializer());

        return JsonMapper.builder()
                .addModule(customDateModule)
                .build();
    }

    @Bean
    @Qualifier("bookingService")
    public BookingService bookingService(
            AvailabilityRepository availabilityRepository,
            HotelRepository hotelRepository,
            BookingRepository bookingRepository,
            OutboxRepository outboxRepository
    ) {
        return new BookingService(availabilityRepository, hotelRepository, bookingRepository, outboxRepository);
    }

    @Bean
    @Qualifier("hotelService")
    public HotelService hotelService(
            HotelRepository hotelRepository,
            OutboxRepository outboxRepository
    ) {
        return new HotelService(hotelRepository, outboxRepository);
    }

    @Bean
    @Qualifier("transactionalCreateHotel")
    public CreateHotelUseCase transactionalCreateHotel(
            @Qualifier("hotelService") HotelService hotelService) {
        return new TransactionalCreateHotelUseCase(hotelService);
    }

    @Bean
    @Qualifier("transactionalUpdateHotelCapacity")
    public UpdateHotelCapacityUseCase transactionalUpdateHotelCapacity(
            @Qualifier("hotelService") HotelService hotelService) {
        return new TransactionalUpdateHotelCapacityUseCase(hotelService);
    }

    @Bean
    @Qualifier("instrumentedHotelService")
    public InstrumentedHotelService instrumentedHotelService(
            @Qualifier("transactionalCreateHotel") CreateHotelUseCase createUseCase,
            @Qualifier("transactionalUpdateHotelCapacity") UpdateHotelCapacityUseCase updateUseCase,
            MeterRegistry meterRegistry) {
        return new InstrumentedHotelService(createUseCase, updateUseCase, meterRegistry);
    }

    @Bean
    @Qualifier("createHotelUseCase")
    public CreateHotelUseCase createHotelUseCase(
            @Qualifier("instrumentedHotelService") InstrumentedHotelService instrumented) {
        return instrumented;
    }

    @Bean
    @Qualifier("updateHotelCapacityUseCase")
    public UpdateHotelCapacityUseCase updateHotelCapacityUseCase(
            @Qualifier("instrumentedHotelService") InstrumentedHotelService instrumented) {
        return instrumented;
    }

    @Bean
    @Qualifier("plainTransactional")
    public CreateBookingUseCase plainTransactional(
            @Qualifier("bookingService") BookingService bookingService) {
        return new TransactionalCreateBookingUseCase(bookingService);
    }

    @Bean
    @Qualifier("retryingCreate")
    public CreateBookingUseCase retryingCreate(
            @Qualifier("plainTransactional") CreateBookingUseCase createBookingUseCase) {
        return new RetryingCreateBookingUseCase(createBookingUseCase);
    }

    @Bean
    @Qualifier("plainTransactionalCancel")
    public CancelBookingUseCase plainTransactionalCancel(
            @Qualifier("bookingService") BookingService bookingService) {
        return new TransactionalCancelBookingUseCase(bookingService);
    }

    @Bean
    @Qualifier("retryingCancel")
    public CancelBookingUseCase retryingCancel(
            @Qualifier("plainTransactionalCancel") CancelBookingUseCase cancelBookingUseCase) {
        return new RetryingCancelBookingUseCase(cancelBookingUseCase);
    }

    @Bean
    @Qualifier("instrumentedBookingService")
    public InstrumentedBookingService instrumentedBookingService(
            @Qualifier("retryingCreate") CreateBookingUseCase createUseCase,
            @Qualifier("retryingCancel") CancelBookingUseCase cancelUseCase,
            MeterRegistry meterRegistry) {
        return new InstrumentedBookingService(createUseCase, cancelUseCase, meterRegistry);
    }

    @Bean
    @Qualifier("instrumentedCreate")
    public CreateBookingUseCase instrumentedCreate(
            @Qualifier("instrumentedBookingService") InstrumentedBookingService instrumented) {
        return instrumented;
    }

    @Bean
    @Qualifier("instrumentedCancel")
    public CancelBookingUseCase instrumentedCancel(
            @Qualifier("instrumentedBookingService") InstrumentedBookingService instrumented) {
        return instrumented;
    }
}
