package ru.mirea.hotel.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.error.AuthenticationFailedException;
import ru.mirea.hotel.backend.repository.UserAccountRepository;

/** Даёт сервисам текущего пользователя (автора изменений). */
@Component
public class CurrentUserService {
    private final UserAccountRepository users;

    public CurrentUserService(UserAccountRepository users) {
        this.users = users;
    }

    /** Актуальная сущность пользователя из БД (не из токена). */
    public UserAccount requireEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationFailedException();
        }
        return users.findById(principal.id()).filter(UserAccount::isEnabled)
                .orElseThrow(AuthenticationFailedException::new);
    }
}
