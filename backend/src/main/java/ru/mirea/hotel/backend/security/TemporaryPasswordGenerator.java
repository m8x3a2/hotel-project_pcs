package ru.mirea.hotel.backend.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Генерирует временный пароль, который заведомо проходит {@link PasswordPolicy}:
 * 16 символов, есть заглавные, строчные, цифры и спецсимволы. Похожие символы (0/O, 1/l/I) исключены.
 */
@Component
public class TemporaryPasswordGenerator {
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%*-_+?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIAL));
        while (chars.size() < LENGTH) chars.add(pick(ALL));
        Collections.shuffle(chars, random);
        StringBuilder result = new StringBuilder(LENGTH);
        chars.forEach(result::append);
        return result.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
