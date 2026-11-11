package ru.mirea.hotel.backend.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.error.BadRequestException;

import java.util.Map;

/**
 * Собирает Pageable (страница, размер, сортировка) из параметров запроса.
 *
 * <p>Поле сортировки берётся только из белого списка: клиент передаёт короткое имя
 * ({@code price}), а в запрос попадает поле сущности ({@code totalPrice}). Произвольная
 * строка пользователя в SQL не попадает. Размер страницы ограничен {@value #MAX_PAGE_SIZE}.</p>
 */
@Component
public class PageableFactory {
    public static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> BOOKING_FIELDS = Map.ofEntries(
            Map.entry("code", "code"),
            Map.entry("checkIn", "checkIn"),
            Map.entry("checkOut", "checkOut"),
            Map.entry("price", "totalPrice"),
            Map.entry("guests", "guestsCount"),
            Map.entry("status", "status"),
            Map.entry("risk", "riskLevel"),
            Map.entry("guest", "guest.fullName"),
            Map.entry("room", "room.roomNumber"),
            Map.entry("created", "createdAt"),
            Map.entry("updated", "updatedAt"));
    private static final Map<String, String> GUEST_FIELDS = Map.of(
            "name", "fullName", "email", "email", "created", "createdAt");
    private static final Map<String, String> USER_FIELDS = Map.of(
            "email", "email", "name", "displayName", "created", "createdAt", "updated", "updatedAt");
    private static final Map<String, String> AUDIT_FIELDS = Map.of(
            "occurred", "occurredAt", "actor", "actorEmail", "event", "eventType", "entity", "entityType");

    public Pageable bookings(int page, int size, String sort) { return create(page, size, sort, BOOKING_FIELDS, "checkIn,desc"); }
    public Pageable guests(int page, int size, String sort) { return create(page, size, sort, GUEST_FIELDS, "name,asc"); }
    public Pageable users(int page, int size, String sort) { return create(page, size, sort, USER_FIELDS, "email,asc"); }
    public Pageable audit(int page, int size, String sort) { return create(page, size, sort, AUDIT_FIELDS, "occurred,desc"); }

    private static Pageable create(int page, int size, String sort, Map<String, String> allowed, String defaultSort) {
        if (page < 0) throw new BadRequestException("page не может быть отрицательным");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("size должен быть от 1 до " + MAX_PAGE_SIZE);
        String[] parts = (sort == null || sort.isBlank() ? defaultSort : sort).split(",", 2);
        String field = allowed.get(parts[0].trim());
        if (field == null) {
            throw new BadRequestException("Недопустимое поле сортировки. Разрешены: " + String.join(", ", allowed.keySet()));
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        // вторичная сортировка по id делает порядок стабильным при равных значениях
        return PageRequest.of(page, size, Sort.by(direction, field).and(Sort.by("id")));
    }
}
