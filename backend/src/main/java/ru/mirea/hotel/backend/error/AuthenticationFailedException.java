package ru.mirea.hotel.backend.error;

/**
 * Ошибка входа -> HTTP 401. Текст одинаковый для неверного email, неверного пароля
 * и блокировки, чтобы по ответу нельзя было понять, существует ли учётная запись.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() { super("Неверные учётные данные или доступ временно заблокирован"); }
}
