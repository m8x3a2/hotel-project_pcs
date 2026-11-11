package ru.mirea.hotel.backend.service;

import org.junit.jupiter.api.Test;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Бизнес-правило: расчёт риска отмены брони и допустимые переходы статусов. */
class CancellationRiskCalculatorTest {
    private final CancellationRiskCalculator calculator = new CancellationRiskCalculator();

    @Test
    void prepaidConfirmedShortStayIsLow() {
        assertThat(calculator.calculate(BookingStatus.CONFIRMED, true, 3, new BigDecimal("7500")))
                .isEqualTo(CancellationRisk.LOW);
    }

    @Test
    void notPrepaidAloneIsMedium() {
        assertThat(calculator.calculate(BookingStatus.CONFIRMED, false, 2, new BigDecimal("5000")))
                .isEqualTo(CancellationRisk.MEDIUM);
    }

    @Test
    void notPrepaidAndNotConfirmedIsHigh() {
        assertThat(calculator.calculate(BookingStatus.CREATED, false, 2, new BigDecimal("9000")))
                .isEqualTo(CancellationRisk.HIGH);
    }

    @Test
    void longExpensiveUnpaidUnconfirmedIsCritical() {
        assertThat(calculator.calculate(BookingStatus.CREATED, false, 7, new BigDecimal("28000")))
                .isEqualTo(CancellationRisk.CRITICAL);
    }

    @Test
    void finishedBookingsHaveNoRisk() {
        assertThat(calculator.calculate(BookingStatus.COMPLETED, false, 10, new BigDecimal("70000")))
                .isEqualTo(CancellationRisk.NOT_APPLICABLE);
        assertThat(calculator.calculate(BookingStatus.CANCELLED, false, 1, BigDecimal.ONE))
                .isEqualTo(CancellationRisk.NOT_APPLICABLE);
    }

    @Test
    void statusTransitionsFollowLifecycle() {
        assertThat(BookingStatus.CREATED.canChangeTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canChangeTo(BookingStatus.COMPLETED)).isTrue();
        assertThat(BookingStatus.CREATED.canChangeTo(BookingStatus.COMPLETED)).isFalse();
        assertThat(BookingStatus.CANCELLED.canChangeTo(BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStatus.COMPLETED.canChangeTo(BookingStatus.CANCELLED)).isFalse();
    }
}
