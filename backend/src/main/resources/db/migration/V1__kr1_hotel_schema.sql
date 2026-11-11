-- =====================================================================
-- V1. Базовая схема из КР1: гости, номера, бронирования.
--
-- Таблицы, столбцы и ограничения те же, что в sql/init.sql КР1.
-- Отличие одно: первичные ключи - UUID вместо SERIAL (требование КР3 к REST API).
-- Всё, что нужно только backend'у (архив, версии, теги, пользователи...),
-- добавляется следующими миграциями.
-- =====================================================================

-- Гости
CREATE TABLE guests (
    id        UUID         PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone     VARCHAR(20)  NOT NULL,
    email     VARCHAR(100) NOT NULL UNIQUE
);

-- Номера гостиницы (справочник)
CREATE TABLE rooms (
    id              UUID          PRIMARY KEY,
    room_number     INTEGER       NOT NULL UNIQUE CHECK (room_number > 0),
    room_type       VARCHAR(20)   NOT NULL CHECK (room_type IN ('STANDARD', 'DELUXE', 'SUITE')),
    capacity        INTEGER       NOT NULL CHECK (capacity BETWEEN 1 AND 6),
    price_per_night NUMERIC(10,2) NOT NULL CHECK (price_per_night > 0)
);

-- Бронирования (основная сущность)
CREATE TABLE bookings (
    id           UUID          PRIMARY KEY,
    guest_id     UUID          NOT NULL REFERENCES guests(id),
    room_id      UUID          NOT NULL REFERENCES rooms(id),
    check_in     DATE          NOT NULL,
    check_out    DATE          NOT NULL,
    guests_count INTEGER       NOT NULL CHECK (guests_count > 0),
    status       VARCHAR(20)   NOT NULL CHECK (status IN ('CREATED', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    total_price  NUMERIC(10,2) NOT NULL CHECK (total_price >= 0),
    CONSTRAINT chk_booking_dates CHECK (check_out > check_in)
);

CREATE INDEX idx_bookings_guest ON bookings(guest_id);
CREATE INDEX idx_bookings_room  ON bookings(room_id);
