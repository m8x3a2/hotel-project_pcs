package ru.mirea.hotel.exception;

/**
 * Своё исключение: нарушено бизнес-правило (например, номер уже занят на эти даты).
 * Наследуется от RuntimeException, поэтому его не нужно указывать в "throws" у каждого метода.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
