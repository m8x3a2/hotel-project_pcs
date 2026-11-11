package ru.mirea.hotel.backend.error;

/** Запись не найдена -> HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
