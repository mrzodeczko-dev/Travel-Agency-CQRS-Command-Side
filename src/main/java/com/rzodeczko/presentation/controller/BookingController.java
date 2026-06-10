package com.rzodeczko.presentation.controller;


import com.rzodeczko.application.command.CancelBookingCommand;
import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.presentation.dto.CreateBookingRequestDto;
import com.rzodeczko.presentation.dto.CreateBookingResponseDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final CreateBookingUseCase createBookingUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(
            @Qualifier("transactionalCreateBookingUseCase") CreateBookingUseCase createBookingUseCase,
            @Qualifier("transactionalCancelBookingUseCase") CancelBookingUseCase cancelBookingUseCase) {
        this.createBookingUseCase = createBookingUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateBookingResponseDto> create(
            @RequestBody @Valid CreateBookingRequestDto request) {
        var command = new CreateBookingCommand(
                request.hotelId(),
                request.userId(),
                request.start(),
                request.end()
        );

        Long bookingId = createBookingUseCase.createBooking(command);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CreateBookingResponseDto(bookingId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelBookingUseCase.cancelBooking(new CancelBookingCommand(id));
        return ResponseEntity.noContent().build();
    }
}
