package ru.mirea.hotel.backend.domain;

/**
 * Статус бронирования - тот же жизненный цикл, что в КР1:
 * CREATED -> CONFIRMED -> COMPLETED, отмена (CANCELLED) возможна из CREATED и CONFIRMED.
 */
public enum BookingStatus {
    CREATED,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    /** Разрешён ли переход в {@code next}. Переход в тот же статус изменением не считается. */
    public boolean canChangeTo(BookingStatus next) {
        if (next == this) return true;
        return switch (this) {
            case CREATED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Пока бронь активна, можно менять гостя, номер, даты и число гостей. */
    public boolean isEditable() {
        return this == CREATED || this == CONFIRMED;
    }

    /** Занимает ли бронь номер (отменённая - нет). */
    public boolean occupiesRoom() {
        return this != CANCELLED;
    }
}
