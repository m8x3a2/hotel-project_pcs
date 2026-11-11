package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mirea.hotel.backend.domain.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** DTO администрирования пользователей. Хеш пароля наружу не уходит никогда. */
public final class UserDtos {
    private UserDtos() {
    }

    /** Временный пароль сервер генерирует сам и возвращает один раз в ответе. */
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName,
            @NotEmpty Set<@NotNull Role> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName,
            boolean enabled,
            @NotEmpty Set<@NotNull Role> roles,
            @NotNull Long version) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            boolean enabled,
            boolean passwordChangeRequired,
            Set<Role> roles,
            Instant lockedUntil,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    /** Ответ на создание пользователя и сброс пароля: временный пароль показывается один раз. */
    public record TemporaryPasswordResponse(UserResponse user, String temporaryPassword) {
    }
}
