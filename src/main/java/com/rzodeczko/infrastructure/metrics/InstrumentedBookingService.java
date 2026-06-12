package com.rzodeczko.infrastructure.metrics;

import com.rzodeczko.application.command.CancelBookingCommand;
import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class InstrumentedBookingService
        implements CreateBookingUseCase, CancelBookingUseCase {

    private final CreateBookingUseCase createDelegate;
    private final CancelBookingUseCase cancelDelegate;

    private final Counter bookingsCreated;
    private final Counter bookingsCancelled;
    private final Counter bookingsFailed;
    private final Timer createTimer;
    private final Timer cancelTimer;

    public InstrumentedBookingService(
            CreateBookingUseCase createDelegate,
            CancelBookingUseCase cancelDelegate,
            MeterRegistry meterRegistry) {
        this.createDelegate = createDelegate;
        this.cancelDelegate = cancelDelegate;

        this.bookingsCreated = Counter.builder("bookings_total")
                .description("Total number of bookings created")
                .register(meterRegistry);
        this.bookingsCancelled = Counter.builder("bookings_cancelled_total")
                .description("Total number of bookings cancelled")
                .register(meterRegistry);
        this.bookingsFailed = Counter.builder("bookings_failed_total")
                .description("Total number of failed booking attempts")
                .register(meterRegistry);
        this.createTimer = Timer.builder("bookings_create_duration")
                .description("Time spent creating a booking")
                .register(meterRegistry);
        this.cancelTimer = Timer.builder("bookings_cancel_duration")
                .description("Time spent cancelling a booking")
                .register(meterRegistry);
    }

    @Override
    public Long createBooking(CreateBookingCommand command) {
        return createTimer.record(() -> {
            try {
                Long id = createDelegate.createBooking(command);
                bookingsCreated.increment();
                return id;
            } catch (Exception e) {
                bookingsFailed.increment();
                throw e;
            }
        });
    }

    @Override
    public void cancelBooking(CancelBookingCommand command) {
        cancelTimer.record(() -> {
            try {
                cancelDelegate.cancelBooking(command);
                bookingsCancelled.increment();
            } catch (Exception e) {
                bookingsFailed.increment();
                throw e;
            }
        });
    }
}
