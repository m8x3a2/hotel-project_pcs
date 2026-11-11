-- =====================================================================
-- V4. Демонстрационные данные.
-- Первые 13 бронирований, 6 гостей и 8 номеров - те же, что в sql/init.sql КР1.
-- Дополнительно 7 бронирований, чтобы было что листать, фильтровать и считать.
-- Пользователей здесь нет: первый администратор создаётся при старте из .env.
-- =====================================================================

INSERT INTO guests (id, full_name, phone, email) VALUES
(gen_random_uuid(), 'Иванов Иван Иванович',      '+7 900 111-11-11', 'ivanov@mail.ru'),
(gen_random_uuid(), 'Петрова Мария Сергеевна',   '+7 900 222-22-22', 'petrova@mail.ru'),
(gen_random_uuid(), 'Сидоров Алексей Петрович',  '+7 900 333-33-33', 'sidorov@mail.ru'),
(gen_random_uuid(), 'Кузнецова Анна Дмитриевна', '+7 900 444-44-44', 'kuznetsova@mail.ru'),
(gen_random_uuid(), 'Смирнов Дмитрий Олегович',  '+7 900 555-55-55', 'smirnov@mail.ru'),
(gen_random_uuid(), 'Волкова Елена Андреевна',   '+7 900 666-66-66', 'volkova@mail.ru');

INSERT INTO rooms (id, room_number, room_type, capacity, price_per_night) VALUES
(gen_random_uuid(), 101, 'STANDARD', 2, 2500),
(gen_random_uuid(), 102, 'STANDARD', 2, 2500),
(gen_random_uuid(), 103, 'STANDARD', 1, 2000),
(gen_random_uuid(), 104, 'STANDARD', 3, 3000),
(gen_random_uuid(), 201, 'DELUXE',   2, 4000),
(gen_random_uuid(), 202, 'DELUXE',   3, 4500),
(gen_random_uuid(), 301, 'SUITE',    4, 7000),
(gen_random_uuid(), 302, 'SUITE',    2, 6500);

-- Временная таблица с исходными строками: гость по email, номер по room_number.
-- total_price = количество ночей * цена номера (как в КР1).
CREATE TEMPORARY TABLE seed_bookings (
    n            INTEGER,
    email        VARCHAR(100),
    room_number  INTEGER,
    check_in     DATE,
    check_out    DATE,
    guests_count INTEGER,
    status       VARCHAR(20),
    prepaid      BOOLEAN,
    requests     VARCHAR(1000),
    tags         TEXT
);

INSERT INTO seed_bookings VALUES
( 1, 'ivanov@mail.ru',     101, '2026-09-10', '2026-09-13', 2, 'COMPLETED', TRUE,  NULL,                              'regular'),
( 2, 'petrova@mail.ru',    201, '2026-09-15', '2026-09-20', 2, 'COMPLETED', TRUE,  'Номер с видом на парк',           'vip'),
( 3, 'sidorov@mail.ru',    301, '2026-10-01', '2026-10-04', 4, 'COMPLETED', TRUE,  'Детская кроватка',                'family'),
( 4, 'kuznetsova@mail.ru', 102, '2026-10-05', '2026-10-07', 1, 'CANCELLED', FALSE, NULL,                              NULL),
( 5, 'smirnov@mail.ru',    202, '2026-10-18', '2026-10-23', 3, 'CONFIRMED', TRUE,  'Поздний заезд после 23:00',       'family'),
( 6, 'ivanov@mail.ru',     302, '2026-10-25', '2026-10-28', 2, 'CONFIRMED', FALSE, NULL,                              'business'),
( 7, 'petrova@mail.ru',    101, '2026-11-02', '2026-11-05', 2, 'CREATED',   FALSE, NULL,                              NULL),
( 8, 'sidorov@mail.ru',    103, '2026-11-10', '2026-11-12', 1, 'CREATED',   FALSE, 'Тихий номер',                     'business'),
( 9, 'kuznetsova@mail.ru', 301, '2026-11-15', '2026-11-20', 3, 'CONFIRMED', TRUE,  'Трансфер из аэропорта',           'vip'),
(10, 'smirnov@mail.ru',    104, '2026-11-20', '2026-11-22', 3, 'CREATED',   TRUE,  NULL,                              'family'),
(11, 'ivanov@mail.ru',     201, '2026-12-28', '2027-01-03', 2, 'CREATED',   FALSE, 'Новогодний ужин',                 'new-year'),
(12, 'petrova@mail.ru',    102, '2026-10-12', '2026-10-14', 2, 'CANCELLED', FALSE, NULL,                              NULL),
(13, 'sidorov@mail.ru',    202, '2026-09-01', '2026-09-04', 2, 'COMPLETED', TRUE,  NULL,                              'regular'),
(14, 'volkova@mail.ru',    104, '2026-12-01', '2026-12-05', 2, 'CONFIRMED', TRUE,  'Дополнительное полотенце',        'family'),
(15, 'kuznetsova@mail.ru', 201, '2026-11-23', '2026-11-30', 2, 'CREATED',   FALSE, 'Командировка, нужен счёт для юрлица', 'business'),
(16, 'smirnov@mail.ru',    301, '2026-12-20', '2026-12-30', 4, 'CONFIRMED', FALSE, 'Праздничное оформление номера',   'vip|family'),
(17, 'petrova@mail.ru',    102, '2026-11-25', '2026-11-28', 2, 'CREATED',   TRUE,  'Поздний выезд до 15:00',          'late-checkout'),
(18, 'sidorov@mail.ru',    302, '2026-11-12', '2026-11-16', 1, 'CONFIRMED', TRUE,  NULL,                              'business|vip'),
(19, 'volkova@mail.ru',    202, '2026-11-08', '2026-11-10', 3, 'CREATED',   FALSE, NULL,                              'family'),
(20, 'ivanov@mail.ru',     103, '2026-12-05', '2026-12-07', 1, 'CANCELLED', FALSE, 'Отменил из-за смены планов',      NULL);

INSERT INTO bookings (id, code, guest_id, room_id, check_in, check_out, guests_count, status,
                      total_price, prepaid, special_requests)
SELECT gen_random_uuid(),
       'BK-' || lpad(nextval('booking_code_seq')::text, 6, '0'),
       g.id, r.id, s.check_in, s.check_out, s.guests_count, s.status,
       (s.check_out - s.check_in) * r.price_per_night,
       s.prepaid, s.requests
FROM seed_bookings s
JOIN guests g ON g.email = s.email
JOIN rooms  r ON r.room_number = s.room_number
ORDER BY s.n;

INSERT INTO booking_tags (booking_id, tag)
SELECT b.id, t.tag
FROM seed_bookings s
JOIN bookings b ON b.code = 'BK-' || lpad(s.n::text, 6, '0')
CROSS JOIN LATERAL unnest(string_to_array(s.tags, '|')) AS t(tag)
WHERE s.tags IS NOT NULL;

DROP TABLE seed_bookings;

-- Одна отменённая бронь сразу лежит в архиве - чтобы показать фильтр «архив:да».
UPDATE bookings SET archived = TRUE, archived_at = now() WHERE code = 'BK-000004';

-- Риск отмены считается по тем же правилам, что и CancellationRiskCalculator в Java:
--   завершённые и отменённые -> NOT_APPLICABLE;
--   иначе баллы: нет предоплаты +2, статус CREATED +1, 7+ ночей +1, сумма от 30 000 +1;
--   0 -> LOW, 1-2 -> MEDIUM, 3 -> HIGH, 4+ -> CRITICAL.
UPDATE bookings b
SET risk_level = CASE
        WHEN b.status IN ('COMPLETED', 'CANCELLED') THEN 'NOT_APPLICABLE'
        WHEN x.score = 0 THEN 'LOW'
        WHEN x.score <= 2 THEN 'MEDIUM'
        WHEN x.score = 3 THEN 'HIGH'
        ELSE 'CRITICAL'
    END
FROM (
    SELECT id,
           (CASE WHEN NOT prepaid THEN 2 ELSE 0 END)
         + (CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END)
         + (CASE WHEN check_out - check_in >= 7 THEN 1 ELSE 0 END)
         + (CASE WHEN total_price >= 30000 THEN 1 ELSE 0 END) AS score
    FROM bookings
) x
WHERE x.id = b.id;
