package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.mirea.hotel.backend.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** DTO входа, обновления токенов и смены пароля. */
public final class AuthDtos {
    private AuthDtos() {
    }

    /**
     * Тип клиента. WEB - refresh-токен уходит в HttpOnly cookie и не виден JavaScript.
     * NATIVE (JavaFX, Android) - refresh-токен возвращается в теле ответа.
     */
    public enum ClientType { WEB, NATIVE }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            ClientType clientType) {
        public ClientType effectiveClientType() {
            return clientType == null ? ClientType.NATIVE : clientType;
        }
    }

    public record RefreshRequest(@Size(max = 512) String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    /** Первая замена временного пароля: текущий пароль повторно не спрашивается. */
    public record InitialPasswordChangeRequest(@NotBlank @Size(max = 128) String newPassword) {
    }

    public record CurrentUser(
            UUID id,
            String email,
            String displayName,
            Set<Role> roles,
            boolean passwordChangeRequired) {
    }

    public record AuthResponse(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            CurrentUser user) {
    }
}
