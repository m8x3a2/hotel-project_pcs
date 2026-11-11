package ru.mirea.hotel.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Guest;
import ru.mirea.hotel.backend.dto.GuestDtos.GuestRequest;
import ru.mirea.hotel.backend.dto.GuestDtos.GuestResponse;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.error.NotFoundException;
import ru.mirea.hotel.backend.repository.GuestRepository;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Гости: список с поиском, создание и изменение. */
@Service
public class GuestService {
    private final GuestRepository guests;
    private final AuditService audit;

    public GuestService(GuestRepository guests, AuditService audit) {
        this.guests = guests;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<GuestResponse> search(String query, Pageable pageable) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > 100) throw new BadRequestException("Строка поиска длиннее 100 символов");
        String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<Guest> spec = (root, cq, cb) -> text.isEmpty() ? cb.conjunction() : cb.or(
                cb.like(cb.lower(root.get("fullName")), like, '\\'),
                cb.like(cb.lower(root.get("email")), like, '\\'),
                cb.like(root.get("phone"), like, '\\'));
        return guests.findAll(spec, pageable).map(GuestService::toResponse);
    }

    @Transactional(readOnly = true)
    public GuestResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public GuestResponse create(GuestRequest request) {
        String email = normalizeEmail(request.email());
        if (guests.existsByEmailIgnoreCase(email)) throw new ConflictException("Гость с email " + email + " уже есть");
        Guest guest = new Guest(UUID.randomUUID(), normalizeName(request.fullName()), request.phone().trim(), email);
        guests.saveAndFlush(guest);
        audit.record("GUEST_CREATED", "GUEST", guest.getId(), "Добавлен гость " + guest.getFullName(), Map.of());
        return toResponse(guest);
    }

    @Transactional
    public GuestResponse update(UUID id, GuestRequest request) {
        Guest guest = require(id);
        if (request.version() == null) throw new BadRequestException("Для изменения нужно поле version");
        if (request.version() != guest.getVersion()) throw new ConflictException("Данные гостя уже изменены. Обновите список");
        String email = normalizeEmail(request.email());
        if (guests.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new ConflictException("Гость с email " + email + " уже есть");
        guest.setFullName(normalizeName(request.fullName()));
        guest.setPhone(request.phone().trim());
        guest.setEmail(email);
        guests.flush(); // в ответе - уже новая version
        audit.record("GUEST_UPDATED", "GUEST", id, "Изменены данные гостя " + guest.getFullName(), Map.of());
        return toResponse(guest);
    }

    private Guest require(UUID id) {
        return guests.findById(id).orElseThrow(() -> new NotFoundException("Гость не найден"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    static GuestResponse toResponse(Guest guest) {
        return new GuestResponse(guest.getId(), guest.getFullName(), guest.getPhone(), guest.getEmail(),
                guest.getCreatedAt(), guest.getUpdatedAt(), guest.getVersion());
    }
}
