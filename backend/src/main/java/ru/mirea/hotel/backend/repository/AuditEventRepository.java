package ru.mirea.hotel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.mirea.hotel.backend.domain.AuditEvent;

import java.util.UUID;

/** Журнал аудита (только добавление и чтение). */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
