package com.rzodeczko.infrastructure.metrics;

import com.rzodeczko.application.command.CreateHotelCommand;
import com.rzodeczko.application.command.UpdateHotelCapacityCommand;
import com.rzodeczko.application.port.in.CreateHotelUseCase;
import com.rzodeczko.application.port.in.UpdateHotelCapacityUseCase;
import com.rzodeczko.domain.model.Hotel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class InstrumentedHotelService
        implements CreateHotelUseCase, UpdateHotelCapacityUseCase {

    private final CreateHotelUseCase createDelegate;
    private final UpdateHotelCapacityUseCase updateDelegate;

    private final Counter hotelsCreated;
    private final Counter hotelsUpdated;
    private final Counter createFailed;
    private final Counter updateFailed;
    private final Timer createTimer;
    private final Timer updateTimer;

    public InstrumentedHotelService(
            CreateHotelUseCase createDelegate,
            UpdateHotelCapacityUseCase updateDelegate,
            MeterRegistry meterRegistry) {
        this.createDelegate = createDelegate;
        this.updateDelegate = updateDelegate;

        this.hotelsCreated = Counter.builder("hotels_created_total")
                .description("Total number of hotels created")
                .register(meterRegistry);
        this.hotelsUpdated = Counter.builder("hotels_capacity_updated_total")
                .description("Total number of hotel capacity updates")
                .register(meterRegistry);
        this.createFailed = Counter.builder("hotels_create_failed_total")
                .description("Total number of failed hotel creations")
                .register(meterRegistry);
        this.updateFailed = Counter.builder("hotels_update_failed")
                .description("Total number of failed hotel capacity updates")
                .register(meterRegistry);
        this.createTimer = Timer.builder("hotels_create_duration")
                .description("Time spent creating a hotel")
                .register(meterRegistry);
        this.updateTimer = Timer.builder("hotels_update_duration")
                .description("Time spent updating hotel capacity")
                .register(meterRegistry);
    }

    @Override
    public Hotel createHotel(CreateHotelCommand command) {
        return createTimer.record(() -> {
            try {
                Hotel hotel = createDelegate.createHotel(command);
                hotelsCreated.increment();
                return hotel;
            } catch (Exception e) {
                createFailed.increment();
                throw e;
            }
        });
    }

    @Override
    public Hotel updateHotelCapacity(UpdateHotelCapacityCommand command) {
        return updateTimer.record(() -> {
            try {
                Hotel hotel = updateDelegate.updateHotelCapacity(command);
                hotelsUpdated.increment();
                return hotel;
            } catch (Exception e) {
                updateFailed.increment();
                throw e;
            }
        });
    }
}
