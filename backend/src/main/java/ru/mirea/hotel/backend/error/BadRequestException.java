package ru.mirea.hotel.backend.error;

/** Неверные данные запроса или нарушение бизнес-правила -> HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
