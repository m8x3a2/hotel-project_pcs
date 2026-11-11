package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;
import ru.mirea.hotel.backend.domain.RoomType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTO бронирований и массовых операций. */
public final class BookingDtos {
    private BookingDtos() {
    }

    /**
     * Создание и изменение брони. Стоимость и риск клиент не передаёт - их считает сервер.
     * {@code code} можно не указывать: тогда сервер выдаст следующий BK-000NNN.
     */
    public record BookingRequest(
            @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]{3,20}$", message = "код: 3-20 символов, латиница, цифры и дефис")
            String code,
            @NotNull UUID guestId,
            @NotNull UUID roomId,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut,
            @NotNull @Min(1) @Max(6) Integer guestsCount,
            @NotNull BookingStatus status,
            boolean prepaid,
            @Size(max = 1000) String specialRequests,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            Long version) {
    }

    /** Карточка брони. Наружу никогда не отдаётся сама JPA-сущность. */
    public record BookingResponse(
            UUID id,
            String code,
            UUID guestId,
            String guestName,
            String guestEmail,
            String guestPhone,
            UUID roomId,
            int roomNumber,
            RoomType roomType,
            LocalDate checkIn,
            LocalDate checkOut,
            long nights,
            int guestsCount,
            BookingStatus status,
            BigDecimal totalPrice,
            boolean prepaid,
            String specialRequests,
            CancellationRisk riskLevel,
            boolean archived,
            Instant archivedAt,
            Set<String> tags,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public enum BulkAction { ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG }

    public record BulkRequest(
            @NotNull @Size(min = 1, max = 500) Set<@NotNull UUID> ids,
            @NotNull BulkAction action,
            BookingStatus status,
            @Size(max = 50) String tag) {
    }

    /** Итог массовой операции: сколько получилось и что не получилось по каждой записи. */
    public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {
    }

    public record BulkFailure(UUID id, String code, String message) {
    }
}
