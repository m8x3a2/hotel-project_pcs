-- =====================================================================
-- V3. Поля и таблицы, которых не было в КР1, но которые нужны backend'у:
--   * технические поля (created_at, updated_at, version) у всех изменяемых таблиц;
--   * признак активности номера (справочник отключается, а не удаляется);
--   * у бронирования: код, предоплата, пожелания, риск отмены, архив, авторы изменений;
--   * теги и заметки бронирований;
--   * индексы для поиска и запрет пересечения броней одного номера на уровне БД.
-- =====================================================================

-- pg_trgm ускоряет поиск по подстроке (LIKE '%текст%'),
-- btree_gist нужен для EXCLUDE-ограничения по номеру и периоду дат.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Гости
-- ---------------------------------------------------------------------
ALTER TABLE guests
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

CREATE INDEX idx_guests_full_name_trgm ON guests USING gin (lower(full_name) gin_trgm_ops);

-- ---------------------------------------------------------------------
-- Номера (справочник)
-- ---------------------------------------------------------------------
ALTER TABLE rooms
    ADD COLUMN active     BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- Бронирования (основная сущность)
-- ---------------------------------------------------------------------
-- Последовательность для человекочитаемого кода брони: BK-000001, BK-000002, ...
CREATE SEQUENCE booking_code_seq START WITH 1;

ALTER TABLE bookings
    ADD COLUMN code             VARCHAR(20),
    ADD COLUMN prepaid          BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN special_requests VARCHAR(1000),
    ADD COLUMN risk_level       VARCHAR(20) NOT NULL DEFAULT 'LOW'
        CHECK (risk_level IN ('NOT_APPLICABLE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD COLUMN archived         BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN archived_at      TIMESTAMPTZ,
    ADD COLUMN archived_by      UUID REFERENCES app_users(id),
    ADD COLUMN created_by       UUID REFERENCES app_users(id),
    ADD COLUMN updated_by       UUID REFERENCES app_users(id),
    ADD COLUMN created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version          BIGINT      NOT NULL DEFAULT 0;

-- Если в таблице уже были брони из КР1, выдаём им коды, и только потом делаем поле обязательным.
UPDATE bookings SET code = 'BK-' || lpad(nextval('booking_code_seq')::text, 6, '0') WHERE code IS NULL;
ALTER TABLE bookings ALTER COLUMN code SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT uq_bookings_code UNIQUE (code);

-- Архивная запись обязана хранить время архивации.
ALTER TABLE bookings ADD CONSTRAINT chk_bookings_archived_at
    CHECK (archived = FALSE OR archived_at IS NOT NULL);

-- Один номер нельзя забронировать дважды на пересекающиеся даты.
-- Отменённые и архивные брони номер не занимают.
-- daterange(check_in, check_out, '[)') - день выезда свободен для следующего гостя.
ALTER TABLE bookings ADD CONSTRAINT excl_bookings_room_period
    EXCLUDE USING gist (room_id WITH =, daterange(check_in, check_out, '[)') WITH &&)
    WHERE (status <> 'CANCELLED' AND archived = FALSE);

CREATE INDEX idx_bookings_status   ON bookings(status);
CREATE INDEX idx_bookings_risk     ON bookings(risk_level);
CREATE INDEX idx_bookings_archived ON bookings(archived);
CREATE INDEX idx_bookings_check_in ON bookings(check_in);
CREATE INDEX idx_bookings_price    ON bookings(total_price);
CREATE INDEX idx_bookings_requests_trgm ON bookings USING gin (lower(special_requests) gin_trgm_ops);

-- ---------------------------------------------------------------------
-- Теги бронирования (vip, business, family, ...)
-- ---------------------------------------------------------------------
CREATE TABLE booking_tags (
    booking_id UUID        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL CHECK (tag = lower(tag) AND length(tag) > 0),
    PRIMARY KEY (booking_id, tag)
);

CREATE INDEX idx_booking_tags_tag ON booking_tags(tag);

-- ---------------------------------------------------------------------
-- Заметки к бронированию (история общения с гостем, договорённости)
-- ---------------------------------------------------------------------
CREATE TABLE booking_notes (
    id         UUID          PRIMARY KEY,
    booking_id UUID          NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES app_users(id),
    body       VARCHAR(4000) NOT NULL,
    pinned     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_booking_notes_booking ON booking_notes(booking_id, pinned DESC, created_at DESC);
