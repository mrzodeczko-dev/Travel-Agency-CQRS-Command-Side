package com.rzodeczko.infrastructure.tx;

import com.rzodeczko.application.command.CancelBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class TransactionalCancelBookingUseCase implements CancelBookingUseCase {

    private final CancelBookingUseCase delegate;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelBooking(CancelBookingCommand command) {
        delegate.cancelBooking(command);
    }
}
