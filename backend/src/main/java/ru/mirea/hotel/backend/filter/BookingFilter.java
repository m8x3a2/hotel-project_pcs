package ru.mirea.hotel.backend.filter;

import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;
import ru.mirea.hotel.backend.domain.RoomType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Разобранная строка поиска: каждое условие уже типизировано (enum, число, дата).
 * Значение {@code null} означает «условие не задано».
 *
 * @param archived false - только активные (по умолчанию), true - только архив, null - все
 */
public record BookingFilter(
        String freeText,
        Integer roomNumber,
        RoomType roomType,
        BookingStatus status,
        Boolean prepaid,
        CancellationRisk risk,
        String tag,
        Boolean archived,
        List<Comparison<BigDecimal>> price,
        List<Comparison<LocalDate>> checkIn) {

    public enum Operator { LT, LTE, EQ, GTE, GT }

    /** Условие «поле ОПЕРАТОР значение», например сумма >= 20000. */
    public record Comparison<T extends Comparable<? super T>>(Operator operator, T value) { }

    public static BookingFilter activeOnly() {
        return new BookingFilter(null, null, null, null, null, null, null, false, List.of(), List.of());
    }
}
