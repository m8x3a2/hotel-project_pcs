package ru.mirea.hotel.backend.service;

import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;

import java.math.BigDecimal;

/**
 * Считает риск отмены брони (вычисляемый показатель). Одни и те же правила для всех клиентов
 * и для CSV-импорта; в миграции V4 они повторены на SQL для демо-данных.
 *
 * <p>Завершённая или отменённая бронь - {@link CancellationRisk#NOT_APPLICABLE}. Иначе баллы:</p>
 * <ul>
 *   <li>нет предоплаты: +2;</li>
 *   <li>бронь ещё не подтверждена (CREATED): +1;</li>
 *   <li>длинное проживание, от {@value #LONG_STAY_NIGHTS} ночей: +1;</li>
 *   <li>крупная сумма, от 30 000: +1.</li>
 * </ul>
 * <p>0 баллов - LOW, 1-2 - MEDIUM, 3 - HIGH, 4 и больше - CRITICAL.</p>
 */
@Component
public class CancellationRiskCalculator {
    static final long LONG_STAY_NIGHTS = 7;
    static final BigDecimal LARGE_AMOUNT = new BigDecimal("30000");

    public CancellationRisk calculate(BookingStatus status, boolean prepaid, long nights, BigDecimal totalPrice) {
        if (status == BookingStatus.COMPLETED || status == BookingStatus.CANCELLED) {
            return CancellationRisk.NOT_APPLICABLE;
        }
        int score = 0;
        if (!prepaid) score += 2;
        if (status == BookingStatus.CREATED) score += 1;
        if (nights >= LONG_STAY_NIGHTS) score += 1;
        if (totalPrice != null && totalPrice.compareTo(LARGE_AMOUNT) >= 0) score += 1;

        if (score == 0) return CancellationRisk.LOW;
        if (score <= 2) return CancellationRisk.MEDIUM;
        if (score == 3) return CancellationRisk.HIGH;
        return CancellationRisk.CRITICAL;
    }
}
