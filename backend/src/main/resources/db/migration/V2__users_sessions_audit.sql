-- =====================================================================
-- V2. Учётные записи, роли, refresh-сессии и журнал аудита.
-- =====================================================================

CREATE TABLE app_users (
    id                       UUID         PRIMARY KEY,
    email                    VARCHAR(254) NOT NULL UNIQUE,
    display_name             VARCHAR(160) NOT NULL,
    password_hash            VARCHAR(100) NOT NULL,           -- только BCrypt-хеш
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    password_change_required BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts    INTEGER      NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0
);

-- Роли пользователя: VIEWER (просмотр), MANAGER (редактирование), ADMIN (администрирование)
CREATE TABLE app_user_roles (
    user_id UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL CHECK (role IN ('VIEWER', 'MANAGER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

-- Серверные refresh-сессии. Хранится SHA-256 токена, а не сам токен.
CREATE TABLE refresh_sessions (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    user_agent   VARCHAR(500),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_sessions_user    ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_sessions_expires ON refresh_sessions(expires_at);

-- Журнал аудита: кто, что, с какой сущностью и когда сделал
CREATE TABLE audit_events (
    id             UUID          PRIMARY KEY,
    actor_id       UUID,
    actor_email    VARCHAR(254),
    event_type     VARCHAR(80)   NOT NULL,
    entity_type    VARCHAR(80),
    entity_id      UUID,
    summary        VARCHAR(1000) NOT NULL,
    correlation_id VARCHAR(100),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(500),
    occurred_at    TIMESTAMPTZ   NOT NULL,
    details_json   TEXT
);

CREATE INDEX idx_audit_occurred ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_actor    ON audit_events(actor_email);
CREATE INDEX idx_audit_event    ON audit_events(event_type);
CREATE INDEX idx_audit_entity   ON audit_events(entity_type, entity_id);
