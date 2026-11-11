# КР3: backend и frontend системы бронирования гостиницы

Тема та же, что в КР1/КР2: **гостиница, основная сущность — бронирование номера**.
В КР3 сделан Spring Boot REST API (PostgreSQL, Flyway, Spring Security + JWT, Swagger, Docker Compose)
и web-интерфейс для проверки всех функций. Этим же API будет пользоваться Android-приложение из КР4.

Всё новое лежит в папке [`backend/`](backend) — это отдельный Maven-проект. КР1 и КР2 (корневой `pom.xml`, `src/`) не изменялись.

---

## 1. Быстрый запуск

Нужен только запущенный **Docker Desktop** (JDK и Maven для запуска не нужны — сборка идёт внутри Docker).

Из корня проекта в PowerShell:

```bash
powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
```

Скрипт сам:
1. создаёт корневой `.env` со случайными секретами, если его ещё нет (`backend/init-env.ps1`), и печатает временный пароль администратора;
2. собирает образ backend (`backend/Dockerfile`, в первый раз несколько минут — скачиваются зависимости Maven);
3. запускает PostgreSQL и backend и ждёт, пока оба healthcheck станут *healthy*.

То же самое без скрипта (из корня проекта, `.env` уже должен быть):

```bash
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

Остановить (данные базы сохраняются в томе `hotel-booking_hotel_pgdata`):

```bash
powershell -ExecutionPolicy Bypass -File .\backend\stop-compose.ps1
```

Начать с чистой базы (том удаляется, Flyway заново создаст схему и демо-данные): добавьте `-ResetData`.

| Назначение | Адрес |
|---|---|
| Web-интерфейс | http://localhost:8080/ |
| Swagger UI (только ADMIN) | http://localhost:8080/swagger-ui.html |
| REST API | http://localhost:8080/api |
| OpenAPI JSON (только ADMIN) | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Для Android-эмулятора (КР4) | http://10.0.2.2:8080/api |
| PostgreSQL | `jdbc:postgresql://localhost:55432/hotel_booking`, пользователь `hotel_app`, пароль — `POSTGRES_PASSWORD` в `.env` |

**Первый вход:** `admin@hotel.local`, пароль — `BOOTSTRAP_ADMIN_PASSWORD` из `.env`.
Пароль временный: сервер сразу попросит задать постоянный (12+ символов, заглавная и строчная буква, цифра, спецсимвол, без слова `admin`).

> `.env` с настоящими секретами в git не попадает (`.gitignore`). В репозитории лежит [`.env.example`](.env.example) без секретов.
> Если `.env` пересоздать при уже существующей базе, пароль PostgreSQL в томе останется старым — тогда запускайте `stop-compose.ps1 -ResetData`.

Тесты (нужны JDK 21+ и Maven; база не нужна — используется H2 в памяти):

```bash
cd backend
mvn test
```

---

## 2. Как Student Hub перенесён на гостиницу

| В архиве | У нас | Таблица |
|---|---|---|
| `Student` | `Booking` — бронирование | `bookings` |
| `StudyGroup` | `Room` — номер гостиницы (справочник) | `rooms` |
| — | `Guest` — гость (связанная сущность из КР1) | `guests` |
| `StudentStatus` | `BookingStatus`: CREATED → CONFIRMED → COMPLETED, отмена CANCELLED | |
| `RiskLevel` | `CancellationRisk` — риск отмены брони | |
| `StudentNote` | `BookingNote` — заметка к брони | `booking_notes` |
| `student_tag` | теги брони | `booking_tags` |
| `UserAccount` | `UserAccount` | `app_users`, `app_user_roles` |
| `RefreshSession` | `RefreshSession` | `refresh_sessions` |
| `AuditEvent` | `AuditEvent` | `audit_events` |
| `AnalyticsResponse` | `AnalyticsResponse` | |
| роли VIEWER / TEACHER / ADMIN | **VIEWER** (просмотр) / **MANAGER** (менеджер ресепшена — редактирование) / **ADMIN** | |

Основная сущность `Booking` по пунктам задания:

| Требование | Поле |
|---|---|
| UUID | `id` |
| Отдельный код / внешний идентификатор | `code` — `BK-000001`… (выдаёт сервер из sequence или задаётся вручную); по нему работает CSV-импорт |
| Основные текстовые поля | `specialRequests` (пожелания гостя) + ФИО/email/телефон гостя через связь |
| Связь со справочником | `room` → `rooms` (и `guest` → `guests`) |
| Статус | `status` |
| Числовой показатель | `totalPrice` (ночи × цена номера, считает сервер), `guestsCount` |
| Логический признак | `prepaid` — внесена ли предоплата |
| Теги | `tags` (таблица `booking_tags`) |
| Вычисляемый показатель | `riskLevel` — риск отмены (см. ниже) |
| Даты создания и изменения | `createdAt`, `updatedAt` |
| version | `version` (`@Version`, optimistic locking) |
| Признак архива | `archived`, `archivedAt`, `archivedBy` |

**Риск отмены** (`CancellationRiskCalculator`): завершённая или отменённая бронь — `NOT_APPLICABLE`; иначе баллы:
нет предоплаты +2, бронь ещё не подтверждена +1, 7 и больше ночей +1, сумма от 30 000 ₽ +1.
0 — LOW, 1–2 — MEDIUM, 3 — HIGH, 4+ — CRITICAL.

**Бизнес-правила** (в `BookingService`, часть — из КР1):
гость и номер существуют, отключённый номер нельзя выбрать для новой брони; выезд позже заезда, не больше 60 ночей;
гостей не больше вместимости номера; номер не занят другой активной бронью на эти даты (проверяет сервис, а ещё
PostgreSQL ограничением `EXCLUDE USING gist`); новая бронь — только CREATED/CONFIRMED и не в прошлом;
статус меняется только по разрешённым переходам; нельзя завершить бронь до даты заезда; у завершённой/отменённой брони
нельзя менять гостя, номер, даты, число гостей; архивную бронь нельзя изменять; цена фиксируется при бронировании
(смена цены номера в справочнике не меняет старые брони).

---

## 3. Выполнение требований задания

### Обязательная структура backend — `backend/src/main/java/ru/mirea/hotel/backend/`

| Пакет | Что в нём |
|---|---|
| `controller` | `AuthController`, `BookingController`, `NoteController`, `RoomController`, `GuestController`, `AnalyticsController`, `AdminUserController`, `AuditController` |
| `service` | бизнес-логика и транзакции: `BookingService`, `CsvBookingService`, `NoteService`, `RoomService`, `GuestService`, `AnalyticsService`, `AuthService`, `LoginAttemptService`, `UserAdminService`, `AuditService`, `CancellationRiskCalculator`, `BookingMapper` |
| `repository` | Spring Data JPA: `BookingRepository` (+ JPQL для аналитики и проверки занятости), `GuestRepository`, `RoomRepository`, … |
| `domain` | JPA-сущности и enum |
| `dto` | входные и выходные модели (`record`), сущности наружу не отдаются |
| `security` | `JwtService`, `JwtAuthenticationFilter`, `PasswordChangeRequiredFilter`, `PasswordPolicy`, `TemporaryPasswordGenerator`, `CurrentUserService`, `UserPrincipal` |
| `filter` | `BookingFilterParser` (умная строка поиска), `BookingFilter`, `BookingSpecifications` (Criteria API) |
| `error` | `GlobalExceptionHandler`, `AppErrorController`, свои исключения |
| `config` | `SecurityConfig`, `OpenApiConfig`, `AppProperties`, `BootstrapAdmin`, `TimeConfig` |
| `web` | `CorrelationIdFilter`, `PageableFactory` (белый список сортировки), `RequestMetadata` |
| `resources/db/migration` | миграции Flyway V1–V4 |

### 1. Авторизация и учётные записи

| Требование | Реализация |
|---|---|
| Вход по логину и паролю | `POST /api/auth/login` |
| Три уровня доступа | VIEWER / MANAGER / ADMIN, `@PreAuthorize` на каждом методе контроллеров |
| Короткоживущий access JWT | 15 минут (`ACCESS_TOKEN_MINUTES`), HMAC-подпись, секрет из `.env` |
| Refresh-токен с серверной сессией | таблица `refresh_sessions`, хранится только SHA-256 токена |
| Обновление access JWT | `POST /api/auth/refresh`; при каждом обновлении выдаётся новый refresh, старый отзывается; повторное использование старого = кража, отзываются все сессии |
| Выход с отзывом сессии | `POST /api/auth/logout` |
| Текущий пользователь | `GET /api/auth/me` |
| Смена пароля | `POST /api/auth/change-password` (после неё все refresh-сессии отзываются) |
| Обязательная смена временного пароля | `POST /api/auth/change-initial-password`; до смены остальные `/api/**` отвечают 403 `PASSWORD_CHANGE_REQUIRED` |
| BCrypt | `BCryptPasswordEncoder(12)` |
| Защита от перебора | 5 неудачных попыток подряд → блокировка на 5 минут, дальше дольше (до 40 мин); BCrypt-проверка выполняется и для несуществующего email |
| Пароли не хранятся и не возвращаются | в БД только хеш; временный пароль показывается один раз в ответе на создание/сброс |

Web-клиент получает refresh-токен только в HttpOnly-cookie (JavaScript его не видит), Android/JavaFX (`clientType: NATIVE`) — в JSON.

### 2–3. Основная сущность, CRUD и архив — `/api/bookings`

| Требование | Запрос | Роли |
|---|---|---|
| Список | `GET /api/bookings` | все |
| Карточка по UUID | `GET /api/bookings/{id}` | все |
| Создание | `POST /api/bookings` | MANAGER, ADMIN |
| Изменение с проверкой version | `PUT /api/bookings/{id}` (устаревшая `version` → 409) | MANAGER, ADMIN |
| Архивирование вместо удаления | `DELETE /api/bookings/{id}` | MANAGER, ADMIN |
| Восстановление | `POST /api/bookings/{id}/restore` (проверяет, не заняли ли номер) | MANAGER, ADMIN |
| Окончательное удаление | `DELETE /api/bookings/{id}/permanent` + заголовок `X-Confirm-Delete: <код брони>`, только для брони из архива | ADMIN |

### 4. Поиск, фильтрация, сортировка, пагинация

`GET /api/bookings?q=...&page=0&size=20&sort=checkIn,desc`. Строка `q` разбирается на сервере:

| Требование | Пример `q` |
|---|---|
| Свободный текстовый поиск | `Иванов`, `"Петрова Мария"` — код, пожелания, ФИО/email/телефон гостя, номер комнаты |
| Фильтр по справочнику | `номер:301`, `тип:люкс` |
| Фильтр по статусу | `статус:подтверждено` (или `status:confirmed`) |
| Фильтр по логическому признаку | `предоплата:нет` |
| Фильтр по вычисляемому показателю | `риск:высокий` |
| Фильтр по тегу | `тег:vip` |
| Архивные / активные | по умолчанию активные; `архив:да`, `архив:все` |
| Числовой показатель со сравнением | `сумма>=20000`, `сумма<5000` (`< <= = >= >`) |
| Дополнительно | `заезд>=2026-11-01` |
| Сортировка (11 полей) | `sort=` `code`, `checkIn`, `checkOut`, `price`, `guests`, `status`, `risk`, `guest`, `room`, `created`, `updated` + `,asc`/`,desc` |
| Серверная пагинация | `page`, `size`; ответ `PageResponse` (`content`, `totalElements`, `totalPages`, …) |
| Ограничение размера страницы | `size` от 1 до 100, иначе 400 |
| Без конкатенации SQL | парсер знает только фиксированные команды, значения идут в Criteria API параметрами; поле сортировки — только из белого списка; `%` и `_` в LIKE экранируются |

### 5. Справочная сущность — номера `/api/rooms`

Список (`GET`, все роли; `?activeOnly=true`), создание (`POST`), изменение (`PUT`), включение/отключение без удаления
(`PATCH /api/rooms/{id}/active?active=false&version=N`) — изменения только ADMIN.
Дополнительно справочник гостей `/api/guests` (поиск и пагинация, создание и изменение — MANAGER/ADMIN).

### 6. Заметки — `/api/bookings/{id}/notes`

История (`GET`, сначала закреплённые), создание (`POST`), изменение (`PUT /{noteId}` с version), удаление (`DELETE /{noteId}`).
Хранятся автор и время, признак `pinned`. Изменять и удалять может только автор или ADMIN.

### 7. Массовые операции — `POST /api/bookings/bulk`

`{"ids": [...], "action": "ARCHIVE|RESTORE|SET_STATUS|ADD_TAG|REMOVE_TAG", "status": ..., "tag": ...}` — до 500 UUID.
Каждая бронь обрабатывается отдельно с теми же правилами; ответ: `requested`, `succeeded` и список `failures` (UUID, код, причина).

### 8. Импорт и экспорт CSV

| Требование | Реализация |
|---|---|
| Экспорт в CSV с учётом фильтра | `GET /api/bookings/export?q=...` — UTF-8 с BOM (открывается в Excel), защита от CSV-инъекции формул |
| Предварительная проверка | `POST /api/bookings/import/preview` — ничего не пишет в БД, проверяет формат, Bean Validation и все бизнес-правила |
| Количество корректных и ошибочных строк | `totalRows`, `validRows`, `invalidRows`, `toCreate`, `toUpdate`, `issues` (строка, поле, причина) |
| Отдельное применение | `POST /api/bookings/import/apply` — записывает только корректные строки |
| Два режима | `mode=CREATE_ONLY` (только новые) и `mode=UPSERT_BY_CODE` (обновление по коду брони) |

Колонки: `code, guestEmail, roomNumber, checkIn, checkOut, guestsCount, status` + необязательные `prepaid, specialRequests, tags` (теги через `|`).
Файл экспорта можно сразу импортировать обратно. Примеры: [`backend/examples/bookings-import.csv`](backend/examples/bookings-import.csv)
(3 корректные строки и 4 с разными ошибками), [`bookings-update.csv`](backend/examples/bookings-update.csv) (для режима обновления).

### 9. Аналитика — `GET /api/analytics/summary`

Всего, активные, архивные, с предоплатой / без, средняя сумма брони, выручка по завершённым,
группировки по номеру (справочник), по типу номера, по статусу, по риску отмены. Всё считает PostgreSQL (`count/avg/sum/group by`).

### 10. Администрирование пользователей — `/api/admin/users` (только ADMIN)

Список с поиском и пагинацией; создание (сервер генерирует временный пароль и возвращает его один раз);
изменение имени, email, ролей, активности (`PUT`); сброс пароля `POST /{id}/reset-password` (новый временный пароль, все сессии отзываются).
Отключённый пользователь не может войти, а его уже выданный токен перестаёт работать сразу — роли и активность читаются из БД на каждом запросе.
Администратор не может отключить себя или снять с себя ADMIN.

### 11. Журнал аудита — `/api/admin/audit` (только ADMIN)

Пишутся входы (успешные и неудачные), выход, смены паролей, все изменения броней, гостей, номеров, заметок, пользователей, импорт/экспорт.
В записи: пользователь, тип события, тип сущности, UUID, время, описание, IP, **correlation ID**.
Correlation ID есть у каждого HTTP-запроса (заголовок `X-Correlation-ID`, `CorrelationIdFilter`), он же — в JSON ошибок.
Поиск (`q`), сортировка (`occurred`, `actor`, `event`, `entity`), пагинация.

### 12. Валидация и ошибки

Bean Validation на всех входных DTO (`@NotNull`, `@Size`, `@Min/@Max`, `@Email`, `@Pattern`, `@DecimalMin`…);
контроллеры возвращают только DTO; единый JSON `ApiError {timestamp, status, code, message, path, correlationId, validationErrors}`;
stack trace клиенту не отдаётся (только в лог сервера). Коды: 400 — неверные данные/правило, 401 — нет входа,
403 — нет прав, 404 — нет записи, 409 — конфликт версий, дубликат кода, занятый номер. Для браузера — HTML-страницы ошибок.

### PostgreSQL и Flyway

`ddl-auto: validate` — схему создаёт только Flyway, Hibernate её проверяет при старте.

| Миграция | Что делает |
|---|---|
| `V1__kr1_hotel_schema.sql` | таблицы `guests`, `rooms`, `bookings` из КР1 (те же столбцы и CHECK), ключи UUID |
| `V2__users_sessions_audit.sql` | пользователи, роли, refresh-сессии, аудит |
| `V3__booking_backend_fields.sql` | новые поля (код, предоплата, пожелания, риск, архив, авторы, created/updated/version), теги, заметки, индексы для поиска (в т.ч. триграммные `pg_trgm`), ограничение `EXCLUDE` против пересечения броней одного номера |
| `V4__demo_data.sql` | 6 гостей, 8 номеров и 13 броней из КР1 + 7 новых броней, теги, риск |

Внешние ключи, `UNIQUE`, `CHECK` и `EXCLUDE` заданы на уровне PostgreSQL.

### Docker Compose — `backend/docker-compose.yml`

PostgreSQL 17 и backend — отдельные сервисы; параметры из корневого `.env`; порты опубликованы только на `127.0.0.1`
(8080 и 55432); healthcheck у обоих (`pg_isready` и `GET /actuator/health`); данные в именованном томе — обычный
`docker compose down` их не удаляет. Backend собирается двухэтапным `Dockerfile` (Maven → лёгкий JRE, запуск не от root).

### Swagger и тесты

Swagger UI `/swagger-ui.html` доступен только ADMIN: после входа в web-интерфейс администратору ставится HttpOnly-cookie,
поэтому Swagger открывается без ручного ввода токена. Для «Try it out» — кнопка «Скопировать JWT» в web-интерфейсе → Authorize.

Тесты (`backend/src/test`, H2, 38 тестов):

| Требование | Тест |
|---|---|
| Smoke-тест Spring Context | `ContextSmokeTest` |
| Бизнес-правило / вычисляемый показатель | `CancellationRiskCalculatorTest` (риск и переходы статусов) |
| Фильтр и поиск | `BookingFilterParserTest` (разбор строки), `BookingSearchIntegrationTest` (запросы к БД) |
| Политика паролей / безопасность | `PasswordPolicyTest` (+ генератор временных паролей), `SecurityFlowTest` (401 → вход → 403 до смены пароля → роли → одноразовый refresh → аудит → version после создания) |

Кроме unit-тестов есть сквозная проверка на живом стеке: [`backend/examples/check_api.py`](backend/examples/check_api.py)
(70 проверок: вход, роли, CRUD, version, фильтры, пагинация, заметки, архив, удаление, bulk, CSV, справочник, аналитика, аудит,
Swagger, блокировка после 5 ошибок). Запуск (Python 3, без доп. библиотек; **меняет данные**, поэтому потом `stop-compose.ps1 -ResetData`):

```bash
python backend/examples/check_api.py .env backend/examples
```

Скрипт меняет временный пароль администратора на `Hotel-Defense-2026!` и создаёт `manager@hotel.local` / `viewer@hotel.local`.

### Web-интерфейс (frontend)

Статический клиент без фреймворков (`backend/src/main/resources/static`: `index.html`, `app.js`, `styles.css`), его отдаёт тот же Spring Boot.
Вкладки: Аналитика, Бронирования (поиск с подсказкой, сортировка, размер страницы, карточка, форма, заметки, архив, удаление,
массовая операция, импорт/экспорт CSV), Гости, Номера, Пользователи и Журнал аудита (для ADMIN), ссылка на Swagger.
Кнопки скрываются по роли, но права всё равно проверяет сервер.

---

## 4. Что показать на защите (по пунктам задания)

1. **Docker:** `start-compose.ps1` → в Docker Desktop группа `hotel-booking` с контейнерами `db` и `backend`.
2. **Healthcheck:** статус *healthy* у обоих контейнеров; http://localhost:8080/actuator/health → `{"status":"UP"}`.
3. **Вход:** `admin@hotel.local` + пароль из `.env` → окно смены временного пароля.
4. **Роли:** «Пользователи» → создать `manager@hotel.local` (Менеджер) и `viewer@hotel.local` (Просмотр), показать временный пароль.
   Войти под viewer (другой браузер/окно инкогнито): нет кнопок изменения, нет вкладок пользователей и аудита; прямой запрос даёт 403.
5. **Создание и изменение:** «+ Бронь» → сумма и риск считаются сервером. Изменить статус и предоплату — риск меняется.
   Попробовать занять тот же номер на те же даты → 409; больше гостей, чем мест → 400.
6. **Поиск и фильтры:** `номер:301`, `статус:подтверждено предоплата:нет`, `риск:критический`, `тег:vip`, `сумма>=20000`, `иванов архив:все`.
7. **Пагинация и сортировка:** «На странице: 5», стрелки, сортировка «Сумма ↓».
8. **Архив:** «В архив» → бронь исчезла из списка → `архив:да` → «Восстановить». ADMIN: «Удалить» у архивной брони с вводом кода.
9. **Массовая операция:** отметить несколько броней (в т.ч. завершённую) → «Сменить статус: Подтверждено» → результат с ошибками по каждой.
10. **Заметки:** «Заметки» → добавить, закрепить, изменить, удалить.
11. **CSV:** «Экспорт CSV» с фильтром; «Импорт CSV» → `backend/examples/bookings-import.csv` → «Проверить» (3 корректные, 4 ошибки) → «Применить».
12. **Аналитика:** вкладка «Аналитика».
13. **Пользователи:** изменить роли, отключить, сбросить пароль.
14. **Аудит:** «Журнал аудита» → поиск `LOGIN_FAILED`, correlation ID.
15. **Swagger:** ссылка «Swagger API ↗» (под ADMIN).
16. **Flyway и PostgreSQL:** файлы `backend/src/main/resources/db/migration/V1…V4`; в базе таблица `flyway_schema_history`:

```bash
docker compose --env-file .env -f backend/docker-compose.yml exec db psql -U hotel_app -d hotel_booking -c "\dt"
```

Запросы ко всем эндпоинтам для IntelliJ IDEA: [`backend/examples/http-requests.http`](backend/examples/http-requests.http).

---

## 5. Найдено и исправлено при проверке

- Hibernate при создании брони с тегами отдельно поднимал `version` в БД, но не в объекте — клиент получал устаревшую версию
  и следующее изменение падало с 409. Теги исключены из автоматического optimistic lock (`@OptimisticLock(excluded = true)`),
  а их изменение помечает бронь изменённой обычным способом. Добавлен регрессионный тест в `SecurityFlowTest`.
- После изменения сервис делает `flush()` до формирования ответа, чтобы клиент сразу получал новую `version`
  (в примере Student Hub ответ содержал старую).
- В примере `version` был примитивом `long`, из-за чего новые сущности сохранялись через `merge`, и ответ на создание
  не содержал дат. Здесь `Long`: новые сущности сохраняются через `persist`.
