package ru.mirea.hotel.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.config.AppProperties;
import ru.mirea.hotel.backend.domain.RefreshSession;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.dto.AuthDtos.AuthResponse;
import ru.mirea.hotel.backend.dto.AuthDtos.ChangePasswordRequest;
import ru.mirea.hotel.backend.dto.AuthDtos.CurrentUser;
import ru.mirea.hotel.backend.dto.AuthDtos.InitialPasswordChangeRequest;
import ru.mirea.hotel.backend.dto.AuthDtos.LoginRequest;
import ru.mirea.hotel.backend.error.AuthenticationFailedException;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.repository.RefreshSessionRepository;
import ru.mirea.hotel.backend.repository.UserAccountRepository;
import ru.mirea.hotel.backend.security.CurrentUserService;
import ru.mirea.hotel.backend.security.JwtService;
import ru.mirea.hotel.backend.security.PasswordPolicy;
import ru.mirea.hotel.backend.web.RequestMetadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Вход, обновление токенов, выход и смена пароля.
 *
 * <p>Схема токенов: access JWT живёт 15 минут и не хранится на сервере; refresh-токен -
 * случайная строка, на сервере лежит её SHA-256 в таблице refresh_sessions. При каждом
 * обновлении старый refresh-токен отзывается и выдаётся новый (ротация). Повторное
 * предъявление уже использованного токена означает кражу - отзываются все сессии пользователя.</p>
 */
@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService attempts;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    private final AppProperties properties;
    private final RequestMetadata metadata;
    private final AuditService audit;
    private final String dummyHash;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       LoginAttemptService attempts, JwtService jwtService,
                       CurrentUserService currentUser, AppProperties properties,
                       RequestMetadata metadata, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.attempts = attempts;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.properties = properties;
        this.metadata = metadata;
        this.audit = audit;
        // для несуществующего email тоже выполняем BCrypt-проверку - время ответа не выдаёт, есть ли такой пользователь
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-" + UUID.randomUUID());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByEmailIgnoreCase(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !user.isEnabled() || attempts.isLocked(user) || !passwordMatches) {
            String reason = user == null ? "unknown_user" : !user.isEnabled() ? "disabled"
                    : attempts.isLocked(user) ? "locked" : "bad_password";
            if (user != null && user.isEnabled() && !attempts.isLocked(user)) attempts.recordFailure(user.getId());
            audit.recordSeparately(user == null ? null : user.getId(), email, "LOGIN_FAILED",
                    "Неудачная попытка входа", Map.of("reason", reason));
            throw new AuthenticationFailedException();
        }
        attempts.recordSuccess(user.getId());
        AuthResponse response = issueSession(user);
        audit.recordSeparately(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "Успешный вход",
                Map.of("clientType", request.effectiveClientType().name()));
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) throw new AuthenticationFailedException();
        RefreshSession old = sessions.findByTokenHash(hash(rawToken)).orElseThrow(AuthenticationFailedException::new);
        Instant now = Instant.now();
        if (old.getRevokedAt() != null) {
            if (old.getReplacedBy() != null) {
                // токен уже обменивали - его кто-то перехватил; закрываем все сессии пользователя
                sessions.revokeAllForUser(old.getUser().getId(), now);
                audit.recordSeparately(old.getUser().getId(), old.getUser().getEmail(), "REFRESH_REUSE_DETECTED",
                        "Повторное использование refresh-токена, все сессии отозваны", Map.of());
            }
            throw new AuthenticationFailedException();
        }
        if (!old.getExpiresAt().isAfter(now) || !old.getUser().isEnabled()) {
            old.setRevokedAt(now);
            throw new AuthenticationFailedException();
        }
        String nextRaw = randomToken();
        Instant nextExpiry = now.plus(properties.jwt().refreshDays(), ChronoUnit.DAYS);
        RefreshSession next = new RefreshSession(UUID.randomUUID(), old.getUser(), hash(nextRaw), nextExpiry,
                metadata.userAgent(), metadata.ipAddress());
        sessions.save(next);
        old.setRevokedAt(now);
        old.setLastUsedAt(now);
        old.setReplacedBy(next.getId());
        JwtService.IssuedAccessToken access = jwtService.issue(old.getUser());
        return new AuthResponse(access.token(), access.expiresAt(), nextRaw, nextExpiry, toCurrentUser(old.getUser()));
    }

    /** Выход: refresh-сессия отзывается. Повторный выход ошибкой не считается. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) return;
        sessions.findByTokenHash(hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                audit.recordSeparately(session.getUser().getId(), session.getUser().getEmail(), "LOGOUT", "Выход", Map.of());
            }
        });
    }

    @Transactional(readOnly = true)
    public CurrentUser me() {
        return toCurrentUser(currentUser.requireEntity());
    }

    /** Обычная смена пароля: нужен текущий пароль, после смены все refresh-сессии отзываются. */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAccount user = currentUser.requireEntity();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Текущий пароль указан неверно");
        }
        applyNewPassword(user, request.newPassword());
        users.saveAndFlush(user);
        sessions.revokeAllForUser(user.getId(), Instant.now());
        audit.record("PASSWORD_CHANGED", "USER", user.getId(), "Пользователь сменил пароль", Map.of());
    }

    /** Первая смена временного пароля (пользователь уже вошёл с ним, текущая сессия сохраняется). */
    @Transactional
    public void changeInitialPassword(InitialPasswordChangeRequest request) {
        UserAccount user = currentUser.requireEntity();
        if (!user.isPasswordChangeRequired()) {
            throw new BadRequestException("Временный пароль уже заменён. Используйте обычную смену пароля");
        }
        applyNewPassword(user, request.newPassword());
        users.saveAndFlush(user);
        audit.record("INITIAL_PASSWORD_CHANGED", "USER", user.getId(), "Пользователь заменил временный пароль", Map.of());
    }

    private void applyNewPassword(UserAccount user, String newPassword) {
        passwordPolicy.validate(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("Новый пароль должен отличаться от текущего");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    private AuthResponse issueSession(UserAccount user) {
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        String rawRefresh = randomToken();
        Instant refreshExpiry = Instant.now().plus(properties.jwt().refreshDays(), ChronoUnit.DAYS);
        sessions.save(new RefreshSession(UUID.randomUUID(), user, hash(rawRefresh), refreshExpiry,
                metadata.userAgent(), metadata.ipAddress()));
        return new AuthResponse(access.token(), access.expiresAt(), rawRefresh, refreshExpiry, toCurrentUser(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 недоступен", ex);
        }
    }

    public static CurrentUser toCurrentUser(UserAccount user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), Set.copyOf(user.getRoles()),
                user.isPasswordChangeRequired());
    }
}
