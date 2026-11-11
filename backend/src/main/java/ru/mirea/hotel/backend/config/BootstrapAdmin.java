package ru.mirea.hotel.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Role;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.repository.UserAccountRepository;
import ru.mirea.hotel.backend.security.PasswordPolicy;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * При первом запуске (таблица пользователей пуста) создаёт администратора из переменных
 * BOOTSTRAP_ADMIN_*. Пароль временный: после первого входа его нужно сменить.
 */
@Component
public class BootstrapAdmin implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final UserAccountRepository users;
    private final AppProperties properties;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;

    public BootstrapAdmin(UserAccountRepository users, AppProperties properties,
                          PasswordEncoder encoder, PasswordPolicy policy) {
        this.users = users;
        this.properties = properties;
        this.encoder = encoder;
        this.policy = policy;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        String email = properties.bootstrap().email();
        String password = properties.bootstrap().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("База пуста, но BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD не заданы - администратор не создан");
            return;
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        policy.validate(password, email);
        String name = properties.bootstrap().name();
        UserAccount admin = new UserAccount(UUID.randomUUID(), email,
                name == null || name.isBlank() ? "System Administrator" : name.trim(),
                encoder.encode(password), Set.of(Role.ADMIN, Role.MANAGER, Role.VIEWER));
        admin.setPasswordChangeRequired(true);
        users.save(admin);
        log.info("Создан первый администратор {} (пароль в журнал не пишется)", email);
    }
}
