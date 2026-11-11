package ru.mirea.hotel.backend.error;

/** Конфликт: устаревшая версия записи или дубликат уникального значения -> HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
