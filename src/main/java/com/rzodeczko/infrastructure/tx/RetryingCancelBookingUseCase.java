package com.rzodeczko.infrastructure.tx;

import com.rzodeczko.application.command.CancelBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@RequiredArgsConstructor
public class RetryingCancelBookingUseCase implements CancelBookingUseCase {

    private final CancelBookingUseCase delegate;

    @Override
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    public void cancelBooking(CancelBookingCommand command) {
        delegate.cancelBooking(command);
    }
}
