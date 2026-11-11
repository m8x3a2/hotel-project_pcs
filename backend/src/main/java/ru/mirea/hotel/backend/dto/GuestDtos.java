package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTO гостей. */
public final class GuestDtos {
    private GuestDtos() {
    }

    public record GuestRequest(
            @NotBlank @Size(min = 3, max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "телефон: 7-20 символов, цифры, пробел, скобки, дефис") String phone,
            @NotBlank @Email @Size(max = 100) String email,
            Long version) {
    }

    public record GuestResponse(
            UUID id,
            String fullName,
            String phone,
            String email,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
