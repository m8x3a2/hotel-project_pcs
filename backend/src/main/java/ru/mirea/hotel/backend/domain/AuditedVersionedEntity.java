package ru.mirea.hotel.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Общие технические поля изменяемых сущностей: даты создания/изменения и версия.
 *
 * <p>{@link Version} включает optimistic locking: Hibernate добавляет в UPDATE условие
 * {@code where version = ?}. Если запись успели изменить, сервер вернёт 409, а не
 * перезапишет чужие изменения.</p>
 */
@MappedSuperclass
public abstract class AuditedVersionedEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * Long, а не long: у новой сущности version = null, и по этому Spring Data понимает,
     * что её надо вставить (persist), а не искать в базе (merge). Hibernate сам выставит 0.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Помечает сущность изменённой, когда менялись только коллекции (обычный UPDATE поднимет version). */
    protected void touch() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
