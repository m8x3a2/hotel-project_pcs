package ru.mirea.hotel.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.AuditEvent;
import ru.mirea.hotel.backend.dto.AuditDtos.AuditResponse;
import ru.mirea.hotel.backend.repository.AuditEventRepository;
import ru.mirea.hotel.backend.security.UserPrincipal;
import ru.mirea.hotel.backend.web.RequestMetadata;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Запись и просмотр журнала аудита. */
@Service
public class AuditService {
    private final AuditEventRepository events;
    private final ObjectMapper objectMapper;
    private final RequestMetadata metadata;

    public AuditService(AuditEventRepository events, ObjectMapper objectMapper, RequestMetadata metadata) {
        this.events = events;
        this.objectMapper = objectMapper;
        this.metadata = metadata;
    }

    /**
     * Событие от имени текущего пользователя. Пишется в той же транзакции, что и изменение данных:
     * если изменение откатится, не останется и записи о нём.
     */
    @Transactional
    public void record(String eventType, String entityType, UUID entityId, String summary, Map<String, ?> details) {
        UserPrincipal principal = currentPrincipal();
        persist(principal == null ? null : principal.id(), principal == null ? null : principal.email(),
                eventType, entityType, entityId, summary, details);
    }

    /**
     * Событие входа с явно указанным пользователем (его ещё нет в SecurityContext).
     *
     * <p>REQUIRES_NEW - отдельная транзакция: запись о неудачном входе сохранится,
     * хотя сам запрос входа завершится ошибкой 401 и его транзакция откатится.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSeparately(UUID actorId, String actorEmail, String eventType, String summary,
                                 Map<String, ?> details) {
        persist(actorId, actorEmail, eventType, "USER", actorId, summary, details);
    }

    private void persist(UUID actorId, String actorEmail, String eventType, String entityType, UUID entityId,
                         String summary, Map<String, ?> details) {
        String json = null;
        if (details != null && !details.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException ignored) {
                json = "{\"serialization\":\"failed\"}";
            }
        }
        events.save(new AuditEvent(UUID.randomUUID(), actorId, truncate(actorEmail, 254), eventType, entityType,
                entityId, truncate(summary, 1000), metadata.correlationId(), metadata.ipAddress(),
                metadata.userAgent(), json));
    }

    /** Поиск по автору, типу события, сущности, описанию или correlation ID. */
    @Transactional(readOnly = true)
    public Page<AuditResponse> search(String query, Pageable pageable) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 200) normalized = normalized.substring(0, 200);
        String like = "%" + normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        boolean empty = normalized.isBlank();
        Specification<AuditEvent> spec = (root, cq, cb) -> empty ? cb.conjunction() : cb.or(
                cb.like(cb.lower(root.get("actorEmail")), like, '\\'),
                cb.like(cb.lower(root.get("eventType")), like, '\\'),
                cb.like(cb.lower(root.get("entityType")), like, '\\'),
                cb.like(cb.lower(root.get("summary")), like, '\\'),
                cb.like(cb.lower(root.get("correlationId")), like, '\\'));
        return events.findAll(spec, pageable).map(AuditService::toResponse);
    }

    private static AuditResponse toResponse(AuditEvent event) {
        return new AuditResponse(event.getId(), event.getActorId(), event.getActorEmail(),
                event.getEventType(), event.getEntityType(), event.getEntityId(), event.getSummary(),
                event.getCorrelationId(), event.getIpAddress(), event.getOccurredAt(), event.getDetailsJson());
    }

    private static UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
