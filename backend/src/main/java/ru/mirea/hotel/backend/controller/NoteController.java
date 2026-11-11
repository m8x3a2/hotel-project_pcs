package ru.mirea.hotel.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.dto.NoteDtos.NoteRequest;
import ru.mirea.hotel.backend.dto.NoteDtos.NoteResponse;
import ru.mirea.hotel.backend.service.NoteService;

import java.util.List;
import java.util.UUID;

/** Заметки к бронированию: читают все роли, пишут MANAGER и ADMIN. */
@RestController
@RequestMapping("/api/bookings/{bookingId}/notes")
@SecurityRequirement(name = "bearerAuth")
public class NoteController {
    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    public List<NoteResponse> list(@PathVariable UUID bookingId) {
        return notes.list(bookingId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<NoteResponse> create(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(201).body(notes.create(bookingId, request));
    }

    @PutMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public NoteResponse update(@PathVariable UUID bookingId, @PathVariable UUID noteId,
                               @Valid @RequestBody NoteRequest request) {
        return notes.update(bookingId, noteId, request);
    }

    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID bookingId, @PathVariable UUID noteId) {
        notes.delete(bookingId, noteId);
        return ResponseEntity.noContent().build();
    }
}
