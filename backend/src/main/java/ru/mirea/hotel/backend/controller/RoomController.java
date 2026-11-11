package ru.mirea.hotel.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.dto.RoomDtos.RoomRequest;
import ru.mirea.hotel.backend.dto.RoomDtos.RoomResponse;
import ru.mirea.hotel.backend.service.RoomService;

import java.util.List;
import java.util.UUID;

/** Справочник номеров («references» из задания). Читают все, изменяет только ADMIN. */
@RestController
@RequestMapping("/api/rooms")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {
    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    public List<RoomResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return rooms.list(activeOnly);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(201).body(rooms.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(@PathVariable UUID id, @Valid @RequestBody RoomRequest request) {
        return rooms.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Включение/отключение номера без удаления")
    public RoomResponse setActive(@PathVariable UUID id, @RequestParam boolean active, @RequestParam long version) {
        return rooms.setActive(id, active, version);
    }
}
