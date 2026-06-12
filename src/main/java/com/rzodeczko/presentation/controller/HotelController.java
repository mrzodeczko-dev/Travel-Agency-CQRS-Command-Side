package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.command.CreateHotelCommand;
import com.rzodeczko.application.command.UpdateHotelCapacityCommand;
import com.rzodeczko.application.port.in.CreateHotelUseCase;
import com.rzodeczko.application.port.in.UpdateHotelCapacityUseCase;
import com.rzodeczko.domain.model.Hotel;
import com.rzodeczko.presentation.dto.CreateHotelRequestDto;
import com.rzodeczko.presentation.dto.ErrorResponseDto;
import com.rzodeczko.presentation.dto.HotelResponseDto;
import com.rzodeczko.presentation.dto.UpdateHotelCapacityRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hotels")
@Validated
@Tag(name = "Hotels", description = "Hotel creation and capacity management")
public class HotelController {

    private final CreateHotelUseCase createHotelUseCase;
    private final UpdateHotelCapacityUseCase updateHotelCapacityUseCase;

    public HotelController(
            @Qualifier("createHotelUseCase") CreateHotelUseCase createHotelUseCase,
            @Qualifier("updateHotelCapacityUseCase") UpdateHotelCapacityUseCase updateHotelCapacityUseCase) {
        this.createHotelUseCase = createHotelUseCase;
        this.updateHotelCapacityUseCase = updateHotelCapacityUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create a hotel",
            description = "Creates a new hotel with the given room capacity.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Hotel created",
                            content = @Content(schema = @Schema(implementation = HotelResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    public ResponseEntity<HotelResponseDto> createHotel(
            @RequestBody @Valid CreateHotelRequestDto request) {
        Hotel hotel = createHotelUseCase.createHotel(new CreateHotelCommand(request.capacity()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new HotelResponseDto(hotel.getId(), hotel.getCapacity()));
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Update hotel capacity",
            description = "Updates the room capacity of an existing hotel.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Hotel capacity updated",
                            content = @Content(schema = @Schema(implementation = HotelResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Hotel not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
            }
    )
    public ResponseEntity<HotelResponseDto> updateHotelCapacity(
            @PathVariable
            @Positive(message = "Hotel ID must be a positive number")
            @Parameter(description = "ID of the hotel to update", example = "1")
            Long id,
            @RequestBody @Valid UpdateHotelCapacityRequestDto request) {
        Hotel hotel = updateHotelCapacityUseCase.updateHotelCapacity(
                new UpdateHotelCapacityCommand(id, request.capacity()));
        return ResponseEntity.ok(new HotelResponseDto(hotel.getId(), hotel.getCapacity()));
    }
}
