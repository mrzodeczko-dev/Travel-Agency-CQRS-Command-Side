package com.rzodeczko.infrastructure.tx;

import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class TransactionalCreateBookingUseCase implements CreateBookingUseCase {

    private final CreateBookingUseCase createBookingUseCase;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long createBooking(CreateBookingCommand command) {
        return createBookingUseCase.createBooking(command);
    }
}
