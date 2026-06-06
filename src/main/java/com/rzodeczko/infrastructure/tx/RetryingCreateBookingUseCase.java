package com.rzodeczko.infrastructure.tx;

import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@RequiredArgsConstructor
public class RetryingCreateBookingUseCase implements CreateBookingUseCase {

    private final CreateBookingUseCase delegate;

    @Override
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    public Long createBooking(CreateBookingCommand command) {
        return delegate.createBooking(command);
    }
}
