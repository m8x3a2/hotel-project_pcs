# PR: КР3 — Spring Boot backend и web-интерфейс гостиницы

Ветка `kr3-backend` → `master`. Подробное описание по пунктам задания — [KR3.md](KR3.md).

## Что сделано

Система бронирования из КР1/КР2 получила серверную часть: REST API на Spring Boot, PostgreSQL в Docker,
вход по JWT с тремя ролями и web-интерфейс для проверки. Этот же API будет использовать Android-приложение из КР4.
КР1 и КР2 (корневой `pom.xml`, `src/`) не изменялись.

| Что | Где |
|---|---|
| Отдельный Maven-проект backend (Java 21, Spring Boot 3.5.5) | `backend/pom.xml` |
| Пакеты controller / service / repository / domain / dto / security / filter / error / config / web | `backend/src/main/java/ru/mirea/hotel/backend/` |
| Основная сущность — бронирование (`/api/bookings`): CRUD, архив и восстановление, окончательное удаление только ADMIN, `version` | `domain/Booking.java`, `service/BookingService.java`, `controller/BookingController.java` |
| Справочник номеров (`/api/rooms`, включение/отключение без удаления) и гости (`/api/guests`) | `RoomService`, `GuestService`, их контроллеры |
| Вычисляемый показатель — риск отмены брони | `service/CancellationRiskCalculator.java` |
| Поиск по строке `q`, фильтры, сравнение суммы, сортировка по 11 полям, пагинация до 100 записей, без склейки SQL | `filter/*`, `web/PageableFactory.java` |
| Заметки к брони (автор, время, закрепление) | `NoteService`, `NoteController` |
| Массовые операции с результатом по каждой записи | `BookingService.bulk`, `POST /api/bookings/bulk` |
| Импорт CSV (проверка без записи → применение, режимы «только новые» и «обновление по коду») и экспорт с фильтром | `service/CsvBookingService.java` |
| Аналитика | `AnalyticsService`, `GET /api/analytics/summary` |
| Вход, access JWT на 15 минут, refresh-сессии с ротацией, выход, смена пароля, обязательная смена временного, BCrypt, блокировка после 5 ошибок | `security/*`, `AuthService`, `LoginAttemptService`, `AuthController` |
| Роли VIEWER / MANAGER / ADMIN | `@PreAuthorize` в контроллерах, `config/SecurityConfig.java` |
| Управление пользователями: временный пароль генерирует сервер | `UserAdminService`, `/api/admin/users` |
| Журнал аудита с correlation ID: поиск, сортировка, пагинация | `AuditService`, `web/CorrelationIdFilter.java`, `/api/admin/audit` |
| Единый JSON ошибок (400/401/403/404/409), без stack trace; HTML-страницы ошибок для браузера | `error/*`, `static/errors/` |
| Миграции Flyway: V1 — схема КР1 с UUID, V2 — пользователи и аудит, V3 — новые поля, индексы и запрет пересечения броней, V4 — демо-данные | `backend/src/main/resources/db/migration/` |
| Docker Compose: PostgreSQL 17 + backend, healthcheck у обоих, порты только на 127.0.0.1, данные в томе | `backend/docker-compose.yml`, `backend/Dockerfile` |
| Скрипты запуска и остановки, генерация `.env` со случайными секретами | `backend/start-compose.ps1`, `stop-compose.ps1`, `init-env.ps1`, `.env.example` |
| Swagger UI (только ADMIN) | `/swagger-ui.html`, `config/OpenApiConfig.java` |
| Web-интерфейс без фреймворков | `backend/src/main/resources/static/` |
| Тесты на H2, 38 штук: контекст, бизнес-правило, фильтр, политика паролей, сквозной сценарий безопасности | `backend/src/test/` |
| Примеры для защиты: CSV-файлы, `.http`-запросы, сквозная проверка API (70 проверок) | `backend/examples/` |
| Документация | `KR3.md`, раздел КР3 в `README.md`, части про КР3 в `team_docs/TEAM_GUIDE.md` и `team_docs/JAVA_GUIDE.md` |

## Как проверено

- `cd backend && mvn test`: 38 тестов, BUILD SUCCESS.
- `start-compose.ps1`: оба контейнера в статусе *healthy*, Flyway применил V1–V4 на PostgreSQL 17, Hibernate подтвердил схему (`ddl-auto: validate`).
- `python backend/examples/check_api.py .env backend/examples`: 70 из 70 проверок на работающем стеке.
- В браузере вручную проверены вход, аналитика, таблица броней, форма изменения, заметки, массовая операция, пользователи, аудит, Swagger и страница 404.

## Исправлено по ходу проверки

- После создания брони с тегами Hibernate отдельным запросом увеличивал `version` в базе, но не в объекте. Клиент получал устаревшую версию, и следующее изменение падало с 409. Исправлено (`@OptimisticLock(excluded = true)` на тегах и `touch()`), добавлен регрессионный тест.

## Запуск

```bash
powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
```

Откройте http://localhost:8080/ и войдите как `admin@hotel.local` с паролем `BOOTSTRAP_ADMIN_PASSWORD` из `.env` (после входа его нужно сменить).
Файл `.env` в git не попадает, в репозитории лежит только `.env.example`.
