package ru.mirea.hotel.model;

/**
 * Статус бронирования (enum).
 * Жизненный цикл: CREATED -> CONFIRMED -> COMPLETED, а из CREATED/CONFIRMED можно отменить (CANCELLED).
 */
public enum BookingStatus {
    CREATED("Создано"),
    CONFIRMED("Подтверждено"),
    COMPLETED("Завершено"),
    CANCELLED("Отменено");

    private final String title;

    BookingStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Бизнес-правило: какие переходы между статусами разрешены.
     * Например, CREATED -> COMPLETED нельзя (сначала надо подтвердить),
     * а завершённое или отменённое бронирование менять уже нельзя.
     */
    public boolean canChangeTo(BookingStatus next) {
        return switch (this) {
            case CREATED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Можно ли ещё менять даты/номер/количество гостей (только пока бронирование активно). */
    public boolean isEditable() {
        return this == CREATED || this == CONFIRMED;
    }

    @Override
    public String toString() {
        return title;
    }
}
