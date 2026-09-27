-- =====================================================================
-- SQL-скрипт создания базы данных "Гостиница: Бронирование номеров"
-- СУБД: PostgreSQL
-- =====================================================================

-- 1. Создаём базу данных (выполнить отдельно, если база ещё не создана)
-- CREATE DATABASE hotel_db;

-- Дальше выполняется уже внутри базы hotel_db

DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS users;

-- Таблица "Гости" (пользователи системы)
CREATE TABLE users (
    id               SERIAL PRIMARY KEY,
    full_name        VARCHAR(150) NOT NULL,
    phone            VARCHAR(20)  NOT NULL,
    email            VARCHAR(150) NOT NULL UNIQUE,
    passport_number  VARCHAR(20)  NOT NULL UNIQUE
);

-- Таблица "Бронирования" (основная сущность)
-- room_type и status хранятся как текст (соответствуют значениям enum в Java)
CREATE TABLE bookings (
    id              SERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id),
    room_number     INTEGER NOT NULL CHECK (room_number > 0),
    room_type       VARCHAR(20) NOT NULL,
    check_in_date   DATE NOT NULL,
    check_out_date  DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,
    total_price     NUMERIC(10, 2) NOT NULL CHECK (total_price >= 0),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (check_out_date > check_in_date)
);

-- =====================================================================
-- Начальные тестовые данные
-- =====================================================================

-- Минимум 5 гостей
INSERT INTO users (full_name, phone, email, passport_number) VALUES
('Иванов Иван Иванович',    '+7 900 111-11-11', 'ivanov@mail.ru',    '4500111111'),
('Петрова Мария Сергеевна', '+7 900 222-22-22', 'petrova@mail.ru',   '4500222222'),
('Сидоров Алексей Петрович','+7 900 333-33-33', 'sidorov@mail.ru',   '4500333333'),
('Кузнецова Анна Дмитриевна','+7 900 444-44-44','kuznecova@mail.ru', '4500444444'),
('Смирнов Дмитрий Олегович', '+7 900 555-55-55','smirnov@mail.ru',   '4500555555');

-- Минимум 10 бронирований, минимум 3 разных статуса, разные типы номеров
INSERT INTO bookings (user_id, room_number, room_type, check_in_date, check_out_date, status, total_price) VALUES
(1, 101, 'STANDARD', '2026-10-01', '2026-10-04', 'CONFIRMED',  6000.00),
(1, 205, 'DELUXE',   '2026-11-10', '2026-11-12', 'CREATED',    7000.00),
(2, 102, 'STANDARD', '2026-09-25', '2026-09-27', 'CHECKED_OUT',4000.00),
(2, 310, 'SUITE',    '2026-12-01', '2026-12-05', 'CONFIRMED', 20000.00),
(3, 103, 'STANDARD', '2026-10-15', '2026-10-16', 'CANCELLED',  2000.00),
(3, 401, 'FAMILY',   '2026-10-20', '2026-10-25', 'CHECKED_IN',22500.00),
(4, 206, 'DELUXE',   '2026-09-18', '2026-09-20', 'CHECKED_OUT', 7000.00),
(4, 104, 'STANDARD', '2026-11-01', '2026-11-03', 'CREATED',    4000.00),
(5, 311, 'SUITE',    '2026-12-20', '2026-12-22', 'CONFIRMED', 10000.00),
(5, 402, 'FAMILY',   '2026-10-05', '2026-10-07', 'CREATED',    9000.00),
(2, 105, 'STANDARD', '2026-10-10', '2026-10-11', 'CANCELLED',  2000.00),
(1, 207, 'DELUXE',   '2026-11-20', '2026-11-23', 'CONFIRMED', 10500.00);
