package ru.mirea.hotel.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Role;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.hotel.backend.dto.UserDtos.TemporaryPasswordResponse;
import ru.mirea.hotel.backend.dto.UserDtos.UpdateUserRequest;
import ru.mirea.hotel.backend.dto.UserDtos.UserResponse;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.error.NotFoundException;
import ru.mirea.hotel.backend.repository.RefreshSessionRepository;
import ru.mirea.hotel.backend.repository.UserAccountRepository;
import ru.mirea.hotel.backend.security.CurrentUserService;
import ru.mirea.hotel.backend.security.TemporaryPasswordGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Администрирование учётных записей (только ADMIN).
 * Временный пароль генерирует сервер и возвращает один раз; в базе - только его BCrypt-хеш.
 */
@Service
public class UserAdminService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public UserAdminService(UserAccountRepository users, RefreshSessionRepository sessions, PasswordEncoder encoder,
                            TemporaryPasswordGenerator passwordGenerator, CurrentUserService currentUser,
                            AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.passwordGenerator = passwordGenerator;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String query, Pageable pageable) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<UserAccount> spec = (root, cq, cb) -> text.isEmpty() ? cb.conjunction() : cb.or(
                cb.like(cb.lower(root.get("email")), like, '\\'),
                cb.like(cb.lower(root.get("displayName")), like, '\\'));
        return users.findAll(spec, pageable).map(UserAdminService::toResponse);
    }

    @Transactional
    public TemporaryPasswordResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("Пользователь с email " + email + " уже есть");
        String temporaryPassword = passwordGenerator.generate();
        UserAccount user = new UserAccount(UUID.randomUUID(), email, request.displayName().trim(),
                encoder.encode(temporaryPassword), Set.copyOf(request.roles()));
        user.setPasswordChangeRequired(true);
        users.saveAndFlush(user);
        audit.record("USER_CREATED", "USER", user.getId(), "Создан пользователь " + email,
                Map.of("roles", user.getRoles()));
        return new TemporaryPasswordResponse(toResponse(user), temporaryPassword);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        UserAccount user = require(id);
        if (request.version() != user.getVersion()) throw new ConflictException("Пользователь уже изменён. Обновите список");
        String email = normalizeEmail(request.email());
        users.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Пользователь с email " + email + " уже есть");
        });
        Set<Role> roles = Set.copyOf(request.roles());
        UserAccount actor = currentUser.requireEntity();
        if (actor.getId().equals(id) && (!request.enabled() || !roles.contains(Role.ADMIN))) {
            throw new BadRequestException("Нельзя отключить себя или снять с себя роль ADMIN");
        }
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setEnabled(request.enabled());
        user.setRoles(roles);
        users.flush();
        // отключённый пользователь теряет все refresh-сессии; access JWT перестанет работать сразу,
        // потому что JwtAuthenticationFilter проверяет enabled по базе на каждом запросе
        if (!request.enabled()) sessions.revokeAllForUser(id, Instant.now());
        audit.record("USER_UPDATED", "USER", id, "Изменён пользователь " + email,
                Map.of("roles", roles, "enabled", request.enabled()));
        return toResponse(user);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID id) {
        UserAccount user = require(id);
        String temporaryPassword = passwordGenerator.generate();
        user.setPasswordHash(encoder.encode(temporaryPassword));
        user.setPasswordChangeRequired(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        users.flush();
        sessions.revokeAllForUser(id, Instant.now());
        audit.record("USER_PASSWORD_RESET", "USER", id, "Сброшен пароль пользователя " + user.getEmail(), Map.of());
        return new TemporaryPasswordResponse(toResponse(user), temporaryPassword);
    }

    private UserAccount require(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEnabled(),
                user.isPasswordChangeRequired(), Set.copyOf(user.getRoles()), user.getLockedUntil(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getVersion());
    }
}
