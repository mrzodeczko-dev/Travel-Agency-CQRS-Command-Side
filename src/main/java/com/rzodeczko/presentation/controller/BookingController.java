package com.rzodeczko.presentation.controller;


import com.rzodeczko.application.command.CancelBookingCommand;
import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.presentation.dto.CreateBookingRequestDto;
import com.rzodeczko.presentation.dto.CreateBookingResponseDto;
import com.rzodeczko.presentation.dto.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@Validated
@Tag(name = "Bookings", description = "Hotel booking creation and cancellation")
@Slf4j
public class BookingController {
    private final CreateBookingUseCase createBookingUseCase;
    private final CancelBookingUseCase cancelBookingUseCase;

    public BookingController(
            @Qualifier("instrumentedCreate") CreateBookingUseCase createBookingUseCase,
            @Qualifier("instrumentedCancel") CancelBookingUseCase cancelBookingUseCase) {
        this.createBookingUseCase = createBookingUseCase;
        this.cancelBookingUseCase = cancelBookingUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create a booking",
            description = "Reserves hotel availability for the given date range using pessimistic locking. "
                    + "Publishes a BookingCreated event via the transactional outbox.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Booking created",
                            content = @Content(schema = @Schema(implementation = CreateBookingResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "Hotel overbooked or pessimistic lock timeout",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    public ResponseEntity<CreateBookingResponseDto> create(
            @RequestBody @Valid CreateBookingRequestDto request) {
        log.info(">>> Received booking creation request: hotelId={}, userId={}, start={}, end={}",
                request.hotelId(), request.userId(), request.start(), request.end());
        var command = new CreateBookingCommand(
                request.hotelId(),
                request.userId(),
                request.start(),
                request.end()
        );

        Long bookingId = createBookingUseCase.createBooking(command);
        log.info("<<< Created booking: bookingId={}", bookingId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CreateBookingResponseDto(bookingId));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Cancel a booking",
            description = "Marks the booking as CANCELLED, releases daily availability, "
                    + "and publishes a BookingCancelled event via the transactional outbox.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Booking cancelled"),
                    @ApiResponse(responseCode = "404", description = "Booking not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "409", description = "Booking already cancelled",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    public ResponseEntity<Void> cancel(
            @PathVariable
            @Positive(message = "Booking ID must be a positive number")
            @Parameter(description = "ID of the booking to cancel", example = "17")
            Long id) {
        cancelBookingUseCase.cancelBooking(new CancelBookingCommand(id));
        return ResponseEntity.noContent().build();
    }
}
