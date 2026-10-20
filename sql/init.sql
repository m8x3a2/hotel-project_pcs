-- =====================================================================
-- Гостиница: бронирование номеров. Скрипт создания базы данных (PostgreSQL)
--
-- 1) Сначала один раз создайте базу:   CREATE DATABASE hotel_db;
-- 2) Потом выполните этот файл внутри базы hotel_db
--    (pgAdmin -> Query Tool, или: psql -U postgres -d hotel_db -f sql/init.sql)
--
-- ВНИМАНИЕ: скрипт удаляет старые таблицы и создаёт их заново с тестовыми данными.
-- =====================================================================

-- Файл сохранён в UTF-8 (нужно для psql в Windows, иначе русские буквы испортятся)
SET client_encoding = 'UTF8';

-- Удаляем таблицы, если они уже есть (сначала bookings, т.к. она ссылается на остальные)
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS guests;
DROP TABLE IF EXISTS users; -- таблица из старой версии проекта

-- ---------------------------------------------------------------------
-- Гости (пользователи предметной области)
-- ---------------------------------------------------------------------
CREATE TABLE guests (
    id        SERIAL       PRIMARY KEY,          -- SERIAL = число, которое база увеличивает сама
    full_name VARCHAR(100) NOT NULL,
    phone     VARCHAR(20)  NOT NULL,
    email     VARCHAR(100) NOT NULL UNIQUE       -- двух гостей с одним email быть не может
);

-- ---------------------------------------------------------------------
-- Номера гостиницы
-- ---------------------------------------------------------------------
CREATE TABLE rooms (
    id              SERIAL        PRIMARY KEY,
    room_number     INTEGER       NOT NULL UNIQUE CHECK (room_number > 0),
    room_type       VARCHAR(20)   NOT NULL CHECK (room_type IN ('STANDARD', 'DELUXE', 'SUITE')),
    capacity        INTEGER       NOT NULL CHECK (capacity BETWEEN 1 AND 6),   -- сколько человек вмещает
    price_per_night NUMERIC(10,2) NOT NULL CHECK (price_per_night > 0)
);

-- ---------------------------------------------------------------------
-- Бронирования (основная сущность). Связана с guests и rooms внешними ключами.
-- ---------------------------------------------------------------------
CREATE TABLE bookings (
    id           SERIAL        PRIMARY KEY,
    guest_id     INTEGER       NOT NULL REFERENCES guests(id),   -- FOREIGN KEY -> guests.id
    room_id      INTEGER       NOT NULL REFERENCES rooms(id),    -- FOREIGN KEY -> rooms.id
    check_in     DATE          NOT NULL,                         -- дата заезда
    check_out    DATE          NOT NULL,                         -- дата выезда
    guests_count INTEGER       NOT NULL CHECK (guests_count > 0),
    status       VARCHAR(20)   NOT NULL CHECK (status IN ('CREATED', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    total_price  NUMERIC(10,2) NOT NULL CHECK (total_price >= 0),
    CHECK (check_out > check_in)                                 -- выезд позже заезда
);

-- =====================================================================
-- Тестовые данные
-- =====================================================================

-- 6 гостей (у последнего нет бронирований - его можно удалить на защите)
INSERT INTO guests (full_name, phone, email) VALUES
('Иванов Иван Иванович',       '+7 900 111-11-11', 'ivanov@mail.ru'),
('Петрова Мария Сергеевна',    '+7 900 222-22-22', 'petrova@mail.ru'),
('Сидоров Алексей Петрович',   '+7 900 333-33-33', 'sidorov@mail.ru'),
('Кузнецова Анна Дмитриевна',  '+7 900 444-44-44', 'kuznetsova@mail.ru'),
('Смирнов Дмитрий Олегович',   '+7 900 555-55-55', 'smirnov@mail.ru'),
('Волкова Елена Андреевна',    '+7 900 666-66-66', 'volkova@mail.ru');

-- 8 номеров трёх типов
INSERT INTO rooms (room_number, room_type, capacity, price_per_night) VALUES
(101, 'STANDARD', 2, 2500),   -- id 1
(102, 'STANDARD', 2, 2500),   -- id 2
(103, 'STANDARD', 1, 2000),   -- id 3
(104, 'STANDARD', 3, 3000),   -- id 4
(201, 'DELUXE',   2, 4000),   -- id 5
(202, 'DELUXE',   3, 4500),   -- id 6
(301, 'SUITE',    4, 7000),   -- id 7
(302, 'SUITE',    2, 6500);   -- id 8

-- 13 бронирований, все 4 статуса. total_price = количество ночей * цена номера
INSERT INTO bookings (guest_id, room_id, check_in, check_out, guests_count, status, total_price) VALUES
(1, 1, '2026-09-10', '2026-09-13', 2, 'COMPLETED',  7500),
(2, 5, '2026-09-15', '2026-09-20', 2, 'COMPLETED', 20000),
(3, 7, '2026-10-01', '2026-10-04', 4, 'COMPLETED', 21000),
(4, 2, '2026-10-05', '2026-10-07', 1, 'CANCELLED',  5000),
(5, 6, '2026-10-18', '2026-10-23', 3, 'CONFIRMED', 22500),
(1, 8, '2026-10-25', '2026-10-28', 2, 'CONFIRMED', 19500),
(2, 1, '2026-11-02', '2026-11-05', 2, 'CREATED',    7500),
(3, 3, '2026-11-10', '2026-11-12', 1, 'CREATED',    4000),
(4, 7, '2026-11-15', '2026-11-20', 3, 'CONFIRMED', 35000),
(5, 4, '2026-11-20', '2026-11-22', 3, 'CREATED',    6000),
(1, 5, '2026-12-28', '2027-01-03', 2, 'CREATED',   24000),
(2, 2, '2026-10-12', '2026-10-14', 2, 'CANCELLED',  5000),
(3, 6, '2026-09-01', '2026-09-04', 2, 'COMPLETED', 13500);
