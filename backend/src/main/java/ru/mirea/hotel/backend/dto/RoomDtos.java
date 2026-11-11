package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.mirea.hotel.backend.domain.RoomType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** DTO справочника номеров. */
public final class RoomDtos {
    private RoomDtos() {
    }

    public record RoomRequest(
            @NotNull @Min(1) @Max(9999) Integer roomNumber,
            @NotNull RoomType roomType,
            @NotNull @Min(1) @Max(6) Integer capacity,
            @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerNight,
            Boolean active,
            Long version) {
    }

    public record RoomResponse(
            UUID id,
            int roomNumber,
            RoomType roomType,
            int capacity,
            BigDecimal pricePerNight,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
