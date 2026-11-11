package ru.mirea.hotel.backend.security;

import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.error.BadRequestException;

import java.util.Locale;

/**
 * Политика паролей: 12-128 символов, заглавная и строчная буква, цифра, спецсимвол,
 * пароль не содержит имя учётной записи (часть email до @).
 */
@Component
public class PasswordPolicy {

    public void validate(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new BadRequestException("Пароль должен содержать от 12 до 128 символов");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean special = !password.chars().allMatch(Character::isLetterOrDigit);
        if (!upper || !lower || !digit || !special) {
            throw new BadRequestException("Пароль должен содержать заглавную и строчную букву, цифру и спецсимвол");
        }
        String login = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (login.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(login)) {
            throw new BadRequestException("Пароль не должен содержать имя учётной записи");
        }
    }
}
