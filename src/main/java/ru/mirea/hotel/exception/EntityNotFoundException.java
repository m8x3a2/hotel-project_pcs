package ru.mirea.hotel.exception;

/**
 * Своё исключение: запись с указанным ID не найдена в базе данных.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
