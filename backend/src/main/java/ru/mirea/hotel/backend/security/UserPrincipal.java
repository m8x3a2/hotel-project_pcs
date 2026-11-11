package ru.mirea.hotel.backend.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.mirea.hotel.backend.domain.Role;
import ru.mirea.hotel.backend.domain.UserAccount;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Текущий пользователь в SecurityContext (без хеша пароля). */
public record UserPrincipal(
        UUID id,
        String email,
        String displayName,
        boolean passwordChangeRequired,
        Set<Role> roles) {

    public static UserPrincipal from(UserAccount user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(),
                user.isPasswordChangeRequired(), Set.copyOf(user.getRoles()));
    }

    /** Роли в формате Spring Security: ROLE_ADMIN, ROLE_MANAGER, ROLE_VIEWER. */
    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
}
