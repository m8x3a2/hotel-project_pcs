# Как работает программа: правила и где они в коде

Краткий справочник: какие проверки есть и в каком файле/строке они написаны.
Ссылки кликабельны в VS Code. Номера строк актуальны на момент написания: если код
поменяется, ищите по названию метода.

---

## 1. Общая схема

```
Main (меню, ввод)  →  Service (правила)  →  Repository (SQL)  →  PostgreSQL
                          ↑
                     Validator (форматы полей)
```

| Что | Файл |
|---|---|
| Точка входа, меню | [Main.java](src/main/java/hotel/Main.java) |
| Все проверки форматов (телефон, email и т.д.) | [Validator.java](src/main/java/hotel/util/Validator.java) |
| Правила по гостям | [UserService.java](src/main/java/hotel/service/UserService.java) |
| Правила по бронированиям | [BookingService.java](src/main/java/hotel/service/BookingService.java) |
| SQL-запросы | [UserRepository.java](src/main/java/hotel/repository/UserRepository.java), [BookingRepository.java](src/main/java/hotel/repository/BookingRepository.java) |
| Подключение к БД | [DatabaseManager.java:15-17](src/main/java/hotel/util/DatabaseManager.java#L15-L17) |
| Таблицы и тестовые данные | [sql/init.sql](sql/init.sql) |

**Проверка идёт в три слоя:**
1. **Меню** — каждое поле проверяется сразу при вводе, при ошибке переспрашивается только оно
   ([Main.java:387-422](src/main/java/hotel/Main.java#L387-L422): `readValidString`, `readValidInt`, `readValidDate`).
2. **Сервис** — те же проверки ещё раз, обойти их нельзя.
3. **База данных** — `UNIQUE`, `CHECK`, `FOREIGN KEY` в [init.sql:15-36](sql/init.sql#L15-L36).

---

## 2. Гость: правила для полей

Все проверки в [Validator.java](src/main/java/hotel/util/Validator.java). Вызываются из
[Main.java:129-132](src/main/java/hotel/Main.java#L129-L132) (создание),
[Main.java:142-145](src/main/java/hotel/Main.java#L142-L145) (изменение),
[UserService.java:31-34](src/main/java/hotel/service/UserService.java#L31-L34) и
[UserService.java:59-62](src/main/java/hotel/service/UserService.java#L59-L62).

### ФИО — [Validator.java:50-60](src/main/java/hotel/util/Validator.java#L50-L60), шаблон [строка 30](src/main/java/hotel/util/Validator.java#L30)
- не пустое;
- от 2 до 4 слов через пробел (минимум фамилия и имя);
- только буквы (русские или латинские), внутри слова можно `-` и `'`;
- не длиннее 150 символов ([строка 19](src/main/java/hotel/util/Validator.java#L19));
- лишние пробелы убираются ([строка 171](src/main/java/hotel/util/Validator.java#L171)).

| ✅ Верно | ❌ Неверно |
|---|---|
| `Иванов Иван Иванович` | `Иванов` (одно слово) |
| `Салтыков-Щедрин Михаил` | `Иванов 123` (цифры) |
| `O'Neil John` | `Иванов Иван!` (спецсимвол) |

### Телефон — [Validator.java:66-79](src/main/java/hotel/util/Validator.java#L66-L79), шаблон [строка 34](src/main/java/hotel/util/Validator.java#L34)
- разрешены только цифры, пробелы, скобки, дефисы и `+`;
- после удаления пробелов/скобок/дефисов: `+7` или `8` и ровно 10 цифр (российский номер);
- **сохраняется в едином виде** `+7 XXX XXX-XX-XX` ([строка 78](src/main/java/hotel/util/Validator.java#L78)).

| Ввод | Сохранится |
|---|---|
| `8 (916) 111-22-33` | `+7 916 111-22-33` |
| `+79161112233` | `+7 916 111-22-33` |
| `12345`, `+19161112233`, `+7916abc2233` | ❌ ошибка |

### Email — [Validator.java:82-94](src/main/java/hotel/util/Validator.java#L82-L94), шаблон [строка 37](src/main/java/hotel/util/Validator.java#L37)
- формат `имя@домен.зона`, только латиница, цифры и `. _ % + -`;
- зона (после последней точки) — минимум 2 буквы;
- нельзя: две точки подряд, точка в начале, точка перед `@`;
- не длиннее 150 символов;
- **приводится к нижнему регистру**: `Petrov@Yandex.RU` → `petrov@yandex.ru`.

❌ `petrov.mail.ru`, `petrov@`, `a@.ru`, `pe..trov@mail.ru`, `p@mail.r`, `петров@почта.рф`

### Паспорт — [Validator.java:100-110](src/main/java/hotel/util/Validator.java#L100-L110), шаблон [строка 41](src/main/java/hotel/util/Validator.java#L41)
- ровно 10 цифр: серия (4) + номер (6), пробелы при вводе допустимы;
- серия не `0000`, номер не `000000`;
- **сохраняется без пробелов**: `4601 777888` → `4601777888`.

### Уникальность — [UserService.java:97-109](src/main/java/hotel/service/UserService.java#L97-L109)
- email не должен совпадать с email другого гостя (без учёта регистра);
- паспорт не должен совпадать с паспортом другого гостя;
- при изменении гостя его собственные email/паспорт не считаются дубликатом;
- запасная защита в базе: `UNIQUE` в [init.sql:19-20](sql/init.sql#L19-L20), ошибка `23505`
  переводится в понятный текст в [UserService.java:112-116](src/main/java/hotel/service/UserService.java#L112-L116).

### Удаление гостя — [UserService.java:79-91](src/main/java/hotel/service/UserService.java#L79-L91)
- гостя с бронированиями удалить нельзя (внешний ключ, код `23503`, [строка 84](src/main/java/hotel/service/UserService.java#L84)).

---

## 3. Бронирование: правила

Создание — [BookingService.java:45-70](src/main/java/hotel/service/BookingService.java#L45-L70),
ввод в меню — [Main.java:184-195](src/main/java/hotel/Main.java#L184-L195).

| Правило | Где |
|---|---|
| ID гостя > 0 | [Validator.java:114-118](src/main/java/hotel/util/Validator.java#L114-L118) |
| Гость с таким ID существует | [BookingService.java:51-53](src/main/java/hotel/service/BookingService.java#L51-L53) |
| Номер комнаты 101–999, не 100/200/…/900 (этаж + номер на этаже) | [Validator.java:124-130](src/main/java/hotel/util/Validator.java#L124-L130), границы [строки 22-23](src/main/java/hotel/util/Validator.java#L22-L23) |
| Тип номера указан | [BookingService.java:233-237](src/main/java/hotel/service/BookingService.java#L233-L237) |
| Выезд позже заезда | [Validator.java:137-139](src/main/java/hotel/util/Validator.java#L137-L139) |
| Не больше 30 ночей | [Validator.java:140-144](src/main/java/hotel/util/Validator.java#L140-L144), константа [строка 25](src/main/java/hotel/util/Validator.java#L25) |
| Заезд не в прошлом | [Validator.java:152-154](src/main/java/hotel/util/Validator.java#L152-L154) |
| Заезд не дальше 365 дней вперёд | [Validator.java:155-158](src/main/java/hotel/util/Validator.java#L155-L158), константа [строка 26](src/main/java/hotel/util/Validator.java#L26) |
| Номер свободен на эти даты (отменённые брони не мешают; день выезда одного = день заезда другого — можно) | [BookingService.java:244-256](src/main/java/hotel/service/BookingService.java#L244-L256) |
| Стоимость = ночи × цена типа | [BookingService.java:258-261](src/main/java/hotel/service/BookingService.java#L258-L261) |
| Новая бронь всегда в статусе `CREATED` | [BookingService.java:66-67](src/main/java/hotel/service/BookingService.java#L66-L67) |

**Цены за ночь** — [RoomType.java:8-11](src/main/java/hotel/model/RoomType.java#L8-L11):

| Тип | Цена, руб/ночь |
|---|---|
| STANDARD | 2000 |
| DELUXE | 3500 |
| SUITE | 5000 |
| FAMILY | 4500 |

### Изменение брони — [BookingService.java:83-103](src/main/java/hotel/service/BookingService.java#L83-L103)
- менять можно только брони в статусах `CREATED` и `CONFIRMED`
  ([BookingService.java:109-117](src/main/java/hotel/service/BookingService.java#L109-L117));
  меню проверяет это сразу после ввода ID ([Main.java:200](src/main/java/hotel/Main.java#L200));
- все проверки как при создании;
- «не в прошлом» проверяется, только если дату заезда перенесли ([строки 91-93](src/main/java/hotel/service/BookingService.java#L91-L93));
- при проверке занятости сама редактируемая бронь не учитывается;
- стоимость пересчитывается.

### Смена статуса — [BookingService.java:119-133](src/main/java/hotel/service/BookingService.java#L119-L133), таблица переходов [строки 30-36](src/main/java/hotel/service/BookingService.java#L30-L36)

```
CREATED ──► CONFIRMED ──► CHECKED_IN ──► CHECKED_OUT
   │            │
   └──► CANCELLED ◄──┘
```
Из `CHECKED_OUT` и `CANCELLED` перейти никуда нельзя.

---

## 4. Остальные функции меню

Главное меню — [Main.java:69-104](src/main/java/hotel/Main.java#L69-L104).

| Пункт | Что делает | Где логика |
|---|---|---|
| 3. Поиск | по части имени гостя (без учёта регистра) или по номеру комнаты | [BookingService.java:143-161](src/main/java/hotel/service/BookingService.java#L143-L161) |
| 4. Фильтрация | по статусу или по типу номера | [BookingService.java:165-175](src/main/java/hotel/service/BookingService.java#L165-L175) |
| 2 → 7. Сортировка | по дате заезда (ранние первыми) или по цене (дорогие первыми) | [BookingService.java:179-189](src/main/java/hotel/service/BookingService.java#L179-L189) |
| 5. Статистика | 7 показателей: гости, брони, активные, завершённые, отменённые, выручка без отменённых, средняя цена | [BookingService.java:193-220](src/main/java/hotel/service/BookingService.java#L193-L220) |
| 6. Экспорт | все брони в `bookings_export.xlsx` (в папке, откуда запущена программа) | [Main.java:277-300](src/main/java/hotel/Main.java#L277-L300), [ExcelExporter.java](src/main/java/hotel/util/ExcelExporter.java) |
| 7. Таблицы БД | вывод всех гостей и броней | [Main.java:304-310](src/main/java/hotel/Main.java#L304-L310) |

---

## 5. Обработка ошибок

- Ошибки правил (`BusinessException`, `EntityNotFoundException`) и ошибки базы ловятся в
  главном меню ([Main.java:96-101](src/main/java/hotel/Main.java#L96-L101)): выводится сообщение,
  программа продолжает работу.
- Нечисловой ввод там, где нужно число, и неверный формат даты переспрашиваются
  ([Main.java:345](src/main/java/hotel/Main.java#L345) `readInt`, [Main.java:370](src/main/java/hotel/Main.java#L370) `readDate`).
  Дата вводится как `гггг-мм-дд`; несуществующая дата (`2026-02-30`) не примется.
- Закрытие ввода (Ctrl+Z) — корректный выход ([Main.java:58-63](src/main/java/hotel/Main.java#L58-L63)).
- Русский ввод читается в кодировке консоли ([Main.java:40](src/main/java/hotel/Main.java#L40), [333](src/main/java/hotel/Main.java#L333)).

---

## 6. Как поменять правило

| Хочу | Где править |
|---|---|
| Другой максимум ночей / дней вперёд | [Validator.java:25-26](src/main/java/hotel/util/Validator.java#L25-L26) |
| Другой диапазон номеров комнат | [Validator.java:22-23](src/main/java/hotel/util/Validator.java#L22-L23) и условие в [строке 125](src/main/java/hotel/util/Validator.java#L125) |
| Другие цены | [RoomType.java:8-11](src/main/java/hotel/model/RoomType.java#L8-L11) |
| Другие переходы статусов | [BookingService.java:30-36](src/main/java/hotel/service/BookingService.java#L30-L36) |
| Пароль/адрес базы | [DatabaseManager.java:15-17](src/main/java/hotel/util/DatabaseManager.java#L15-L17) или при запуске `-Ddb.password=...` |
