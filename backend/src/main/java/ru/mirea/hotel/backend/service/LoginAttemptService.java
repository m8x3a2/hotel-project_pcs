package ru.mirea.hotel.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.repository.UserAccountRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Защита от подбора пароля: после {@value #MAX_FAILURES} неудачных попыток подряд вход
 * блокируется на 5 минут, при каждой следующей неудаче - дольше (до 40 минут).
 *
 * <p>Счётчик пишется в отдельной транзакции (REQUIRES_NEW): запрос входа завершится
 * ошибкой и его транзакция откатится, а увеличенный счётчик останется.</p>
 */
@Service
public class LoginAttemptService {
    static final int MAX_FAILURES = 5;
    private final UserAccountRepository users;

    public LoginAttemptService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= MAX_FAILURES) {
                int multiplier = Math.min(8, failures - MAX_FAILURES + 1);
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5L * multiplier)));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        });
    }

    public boolean isLocked(UserAccount user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }
}
