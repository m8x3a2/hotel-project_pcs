package ru.mirea.hotel.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.dto.GuestDtos.GuestRequest;
import ru.mirea.hotel.backend.dto.GuestDtos.GuestResponse;
import ru.mirea.hotel.backend.dto.PageResponse;
import ru.mirea.hotel.backend.service.GuestService;
import ru.mirea.hotel.backend.web.PageableFactory;

import java.util.UUID;

/** Гости: читают все роли, добавляют и изменяют MANAGER и ADMIN. */
@RestController
@RequestMapping("/api/guests")
@SecurityRequirement(name = "bearerAuth")
public class GuestController {
    private final GuestService guests;
    private final PageableFactory pageableFactory;

    public GuestController(GuestService guests, PageableFactory pageableFactory) {
        this.guests = guests;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    public PageResponse<GuestResponse> search(@RequestParam(defaultValue = "") String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size,
                                              @RequestParam(defaultValue = "name,asc") String sort) {
        return PageResponse.from(guests.search(q, pageableFactory.guests(page, size, sort)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    public GuestResponse get(@PathVariable UUID id) {
        return guests.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody GuestRequest request) {
        return ResponseEntity.status(201).body(guests.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public GuestResponse update(@PathVariable UUID id, @Valid @RequestBody GuestRequest request) {
        return guests.update(id, request);
    }
}
