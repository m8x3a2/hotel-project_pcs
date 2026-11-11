package ru.mirea.hotel.backend.service;

import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingResponse;

import java.util.Set;

/** Сущность -> DTO. Вызывается внутри транзакции, пока ленивые связи (гость, номер, теги) доступны. */
@Component
public class BookingMapper {
    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getCode(),
                booking.getGuest().getId(), booking.getGuest().getFullName(),
                booking.getGuest().getEmail(), booking.getGuest().getPhone(),
                booking.getRoom().getId(), booking.getRoom().getRoomNumber(), booking.getRoom().getRoomType(),
                booking.getCheckIn(), booking.getCheckOut(), booking.getNights(), booking.getGuestsCount(),
                booking.getStatus(), booking.getTotalPrice(), booking.isPrepaid(), booking.getSpecialRequests(),
                booking.getRiskLevel(), booking.isArchived(), booking.getArchivedAt(),
                Set.copyOf(booking.getTags()), booking.getCreatedAt(), booking.getUpdatedAt(), booking.getVersion());
    }
}
