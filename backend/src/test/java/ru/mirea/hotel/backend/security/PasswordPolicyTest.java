package ru.mirea.hotel.backend.security;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.mirea.hotel.backend.error.BadRequestException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Политика паролей и генератор временных паролей. */
class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();
    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("Hotel-Booking-2026!", "manager@hotel.local")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortOrSimplePasswords() {
        assertThatThrownBy(() -> policy.validate("Ab1!", "a@b.c")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policy.validate("alllowercase-2026", "a@b.c")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policy.validate("NoDigitsOrSpecial", "a@b.c")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsPasswordContainingLogin() {
        assertThatThrownBy(() -> policy.validate("Manager-Strong-2026!", "manager@hotel.local"))
                .isInstanceOf(BadRequestException.class);
    }

    @RepeatedTest(20)
    void generatedTemporaryPasswordAlwaysPassesPolicy() {
        assertThatCode(() -> policy.validate(generator.generate(), "someone@hotel.local")).doesNotThrowAnyException();
    }
}
