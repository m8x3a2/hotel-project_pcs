package ru.mirea.hotel.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTO заметок к бронированию. */
public final class NoteDtos {
    private NoteDtos() {
    }

    /** {@code version} обязателен только при изменении заметки. */
    public record NoteRequest(
            @NotBlank @Size(max = 4000) String body,
            boolean pinned,
            Long version) {
    }

    public record NoteResponse(
            UUID id,
            UUID bookingId,
            UUID authorId,
            String authorName,
            String body,
            boolean pinned,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
