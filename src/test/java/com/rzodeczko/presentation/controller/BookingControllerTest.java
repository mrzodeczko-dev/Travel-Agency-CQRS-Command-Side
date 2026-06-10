package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.command.CreateBookingCommand;
import com.rzodeczko.application.port.in.CancelBookingUseCase;
import com.rzodeczko.application.port.in.CreateBookingUseCase;
import com.rzodeczko.presentation.dto.CreateBookingRequestDto;
import com.rzodeczko.presentation.dto.CreateBookingResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private CreateBookingUseCase createBookingUseCase;
    @Mock
    private CancelBookingUseCase cancelBookingUseCase;

    private BookingController controller;

    private static final LocalDate START = LocalDate.now().plusDays(1);
    private static final LocalDate END   = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        controller = new BookingController(createBookingUseCase, cancelBookingUseCase);
    }

    @Test
    void create_validRequest_returns201Created() {
        CreateBookingRequestDto request = new CreateBookingRequestDto(1L, 42L, START, END);
        when(createBookingUseCase.createBooking(new CreateBookingCommand(1L, 42L, START, END))).thenReturn(99L);

        ResponseEntity<CreateBookingResponseDto> response = controller.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void create_validRequest_returnsBookingIdInBody() {
        CreateBookingRequestDto request = new CreateBookingRequestDto(1L, 42L, START, END);
        when(createBookingUseCase.createBooking(new CreateBookingCommand(1L, 42L, START, END))).thenReturn(17L);

        ResponseEntity<CreateBookingResponseDto> response = controller.create(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().bookingId()).isEqualTo(17L);
    }

    @Test
    void create_validRequest_passesCorrectCommandToUseCase() {
        CreateBookingRequestDto request = new CreateBookingRequestDto(5L, 3L, START, END);
        when(createBookingUseCase.createBooking(new CreateBookingCommand(5L, 3L, START, END))).thenReturn(1L);

        controller.create(request);

        ArgumentCaptor<CreateBookingCommand> captor = ArgumentCaptor.forClass(CreateBookingCommand.class);
        verify(createBookingUseCase).createBooking(captor.capture());
        CreateBookingCommand sent = captor.getValue();
        assertThat(sent.hotelId()).isEqualTo(5L);
        assertThat(sent.userId()).isEqualTo(3L);
        assertThat(sent.start()).isEqualTo(START);
        assertThat(sent.end()).isEqualTo(END);
    }
}
