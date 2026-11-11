package ru.mirea.hotel.backend.domain;

/**
 * Вычисляемый показатель бронирования - риск отмены (неявки гостя).
 * Считается сервером в {@code CancellationRiskCalculator}, клиент его не передаёт.
 */
public enum CancellationRisk {
    /** Бронь уже завершена или отменена - риск не считается. */
    NOT_APPLICABLE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
