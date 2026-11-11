# Разбор проекта для команды (КР1, КР2, КР3)

> Это внутренний файл команды для подготовки к защите.
> Про синтаксис Java, JavaFX и Spring — отдельный файл `JAVA_GUIDE.md` в этой же папке.
> Часть 1 (разделы 1–6) — КР1, консоль. Часть 2 (разделы 7–10) — КР2, JavaFX.
> Часть 3 (разделы 11–15) — КР3, Spring Boot backend + web-интерфейс (полное описание — `KR3.md` в корне).

На защите преподаватель может спросить **любого** про **любую** часть. Поэтому:
1. Сначала **все** читают разделы 1–3 (общая картина) — это минимум для каждого.
2. Потом каждый глубоко разбирает **свою** часть (раздел 4) и умеет показать её в коде.
3. В конце — прогоните друг другу вопросы из раздела 6 и сценарий защиты из раздела 5.

---

## 1. Общая картина (знают ВСЕ)

### Что делает программа
Консольная система для гостиницы: ведёт **гостей**, **номера** и **бронирования**.
Бронирование связывает гостя и номер на период дат. Данные лежат в PostgreSQL.

### Слои (многослойная архитектура)

```
 Пользователь
     │  вводит цифры/текст
     ▼
 ui/  (ConsoleInput, MainMenu, GuestMenu, RoomMenu, BookingMenu, TablePrinter)
     │  вызывает методы сервиса, НЕТ SQL, НЕТ бизнес-правил
     ▼
 service/  (GuestService, RoomService, BookingService)
     │  проверяет бизнес-правила, считает цену/статистику
     ▼
 repository/  (GuestRepository, RoomRepository, BookingRepository)
     │  ТОЛЬКО здесь SQL: INSERT / SELECT / UPDATE / DELETE через JDBC
     ▼
 PostgreSQL (таблицы guests, rooms, bookings)
```

Плюс вспомогательные пакеты:
- `model/` — классы данных (Guest, Room, Booking) и enum'ы (BookingStatus, RoomType). Их используют все слои.
- `exception/` — свои исключения BusinessException и EntityNotFoundException.
- `util/` — DatabaseManager (подключение) и ExcelExporter (экспорт).

**Зачем слои?** Каждый класс отвечает за одно. Если завтра вместо консоли будет JavaFX (это КР2!),
мы меняем только `ui/`, а service и repository остаются теми же.

### Путь одного действия: «Создать бронирование»
Это лучший пример, чтобы объяснить всю систему. Выучите его все.

1. `Main.main()` создал все объекты и запустил `mainMenu.run()`.
2. `BaseMenu.run()` печатает пункты, пользователь вводит `3` → `MainMenu.handle(3)` → `bookingMenu.run()`.
3. Пользователь вводит `1` → `BookingMenu.handle(1)` → `createBooking()`.
4. `createBooking()` через `ConsoleInput` спрашивает ID гостя, даты, показывает свободные номера
   (`roomService.findFreeRooms`), спрашивает ID номера и количество гостей.
5. Вызывает `bookingService.create(guestId, roomId, checkIn, checkOut, guestsCount)`.
6. `BookingService.create()` проверяет правила: гость есть? номер есть? дата не в прошлом?
   выезд позже заезда? гостей не больше вместимости? номер не занят (`bookingRepository.hasOverlap`)?
   Если что-то не так — `throw new BusinessException("...")`.
7. Считает цену: ночи × цена номера. Создаёт объект `Booking`, статус `CREATED`.
8. `bookingRepository.save(booking)` выполняет `INSERT ... RETURNING id` через `PreparedStatement`.
9. Результат возвращается наверх, меню печатает «Бронирование создано!».
10. Если на шаге 6 было исключение — его ловит `BaseMenu.execute()` и печатает «Ошибка: ...».
    Программа не падает, пользователь снова в меню.

### Таблицы и связи
```
guests (id PK, full_name, phone, email UNIQUE)
rooms  (id PK, room_number UNIQUE, room_type, capacity, price_per_night)
bookings (id PK, guest_id FK -> guests.id, room_id FK -> rooms.id,
          check_in, check_out, guests_count, status, total_price)
```
Один гость — много бронирований. Один номер — много бронирований (связи «один ко многим»).

### Бизнес-правила (знают ВСЕ, хотя бы 5 штук)
Все в `service/`, основные пронумерованы в комментарии в начале `BookingService`.
1. Нельзя указать несуществующего гостя/номер → `EntityNotFoundException`.
2. Выезд позже заезда, не больше 30 ночей.
3. Нельзя бронировать на прошедшую дату.
4. Гостей от 1 до вместимости номера.
5. Номер нельзя занять на пересекающиеся даты.
6. Статусы — только разрешённые переходы (`BookingStatus.canChangeTo`).
7. Нельзя «завершить» бронь, которая ещё не началась.
8. Менять бронь можно только в статусе Создано/Подтверждено.
9. Цена считается автоматически.
10. Email гостя и номер комнаты уникальны.
11. Нельзя удалить гостя/номер, у которых есть брони.
12. Нельзя сохранить пустое ФИО, неверный телефон/email, вместимость вне 1..6, цену ≤ 0.

### Ключевые понятия из задания — где они в коде

| Понятие | Где показать |
|---|---|
| Классы и объекты | `model/Guest.java`, `Room.java`, `Booking.java`; объекты создаются в `Main.java` |
| Инкапсуляция, private-поля | все поля в `model/*` — `private`, доступ через get/set |
| Конструкторы | в каждом классе модели по два конструктора |
| enum | `BookingStatus`, `RoomType` |
| Интерфейс | `CrudRepository<T>`, `MenuAction` |
| Полиморфизм | 3 класса реализуют `CrudRepository`; 4 меню наследуют `BaseMenu` и переопределяют `getTitle/getItems/handle`; `toString()` |
| Наследование | `GuestMenu extends BaseMenu`, `BusinessException extends RuntimeException` |
| Коллекции | `List`, `ArrayList`, `Map`, `LinkedHashMap`, `Optional` — в repository и service |
| Свои исключения | `exception/BusinessException`, `EntityNotFoundException` |
| JDBC | `repository/*`, `util/DatabaseManager` |
| try-with-resources | все методы репозиториев, `ExcelExporter.export` |
| Stream API | поиск/фильтр/сортировка/статистика в `BookingService` |
| Экспорт | `util/ExcelExporter` |

---

## 2. Как запустить у себя (все)

1. Создать базу `hotel_db` и выполнить `sql/init.sql` (подробно — README, раздел «Инструкция по запуску»).
2. Проверить пароль в `src/main/resources/database.properties` (у нас `password`).
3. Открыть папку в IntelliJ IDEA → запустить `Main.java`.

Перед защитой **обязательно** ещё раз выполните `sql/init.sql` — база вернётся к исходным тестовым данным.

---

## 3. Файлы проекта одной таблицей

| Файл | Зачем нужен | Кто отвечает |
|---|---|---|
| `sql/init.sql` | создаёт таблицы и тестовые данные | Участник 1 |
| `docs/er-diagram.*` | ER-диаграмма | Участник 1 |
| `resources/database.properties` | адрес БД, логин, пароль | Участник 1 |
| `util/DatabaseManager.java` | выдаёт Connection | Участник 1 |
| `repository/*Repository.java` | SQL-запросы через JDBC | Участник 1 |
| `repository/CrudRepository.java` | интерфейс CRUD | Участник 2 |
| `model/*` | сущности и enum'ы | Участник 2 |
| `exception/*` | свои исключения | Участник 2 |
| `ui/BaseMenu.java` | абстрактный класс меню (наследование, полиморфизм) | Участник 2 + 4 |
| `service/*` | бизнес-правила, поиск, фильтры, сортировка, статистика | Участник 3 |
| `Main.java` | запуск, сборка слоёв | Участник 4 |
| `ui/*` | меню, ввод, вывод, обработка ошибок | Участник 4 |
| `util/ExcelExporter.java` | экспорт в Excel | Участник 4 |
| `pom.xml` | Maven, зависимости | Участник 4 |

---

## 4. Распределение по участникам

### Участник 1 — База данных и JDBC

**Файлы:** `sql/init.sql`, `docs/er-diagram.svg`, `src/main/resources/database.properties`,
`util/DatabaseManager.java`, `repository/GuestRepository.java`, `RoomRepository.java`, `BookingRepository.java`.

**Что нужно понимать:**

1. **Таблицы и ограничения** (`init.sql`):
   - `SERIAL PRIMARY KEY` — id, который база увеличивает сама (1, 2, 3...). Первичный ключ однозначно определяет строку.
   - `REFERENCES guests(id)` — внешний ключ (FOREIGN KEY). Нельзя вставить бронь с `guest_id = 999`, если такого гостя нет,
     и нельзя удалить гостя, на которого ссылаются брони (база выдаст ошибку).
   - `NOT NULL` — поле обязательно. `UNIQUE` — значение не повторяется (email, room_number).
   - `CHECK (...)` — ограничение значений: статус только из 4 вариантов, `check_out > check_in`, вместимость 1..6.
   - `DROP TABLE IF EXISTS` в начале — чтобы скрипт можно было запускать повторно.
     Удаляем сначала `bookings`, потому что она ссылается на `guests` и `rooms`.

2. **Подключение** (`DatabaseManager`): читает `database.properties` (через `getResourceAsStream`)
   и вызывает `DriverManager.getConnection(url, user, password)`. Пароль вынесен из кода в файл настроек — так требует КР2.

3. **Цепочка JDBC** (на примере `GuestRepository.findById`):
   ```java
   String sql = "SELECT id, full_name, phone, email FROM guests WHERE id = ?";
   try (Connection connection = DatabaseManager.getConnection();          // 1. соединение
        PreparedStatement statement = connection.prepareStatement(sql)) { // 2. подготовленный запрос
       statement.setInt(1, id);                                           // 3. подставили значение вместо ?
       try (ResultSet resultSet = statement.executeQuery()) {             // 4. выполнили, получили результат
           if (resultSet.next()) {                                        // 5. есть строка?
               return Optional.of(mapRow(resultSet));                     // 6. превратили строку в объект
           }
           return Optional.empty();
       }
   }                                                                      // 7. всё закрылось автоматически
   ```
   - `executeQuery()` — для SELECT (возвращает ResultSet). `executeUpdate()` — для INSERT/UPDATE/DELETE (возвращает число строк).
   - `INSERT ... RETURNING id` — PostgreSQL сразу возвращает новый id, поэтому для INSERT тоже используем `executeQuery()`.
   - **try-with-resources** (`try (...)`) — Connection, PreparedStatement, ResultSet закрываются сами, даже при ошибке.
     Если их не закрывать, база «кончится» по количеству соединений.

4. **Statement vs PreparedStatement** — 100% спросят:
   - `Statement` выполняет готовую строку: `"SELECT * FROM guests WHERE email = '" + email + "'"`.
     Если пользователь введёт `' OR '1'='1`, запрос превратится в `... WHERE email = '' OR '1'='1'` и вернёт всех — это **SQL-инъекция**.
   - `PreparedStatement` с `?`: SQL и данные передаются в базу **отдельно**, данные никогда не становятся частью команды.
     Плюс база может переиспользовать план запроса. Мы везде используем только `PreparedStatement`.

5. **JOIN** в `BookingRepository.SELECT_WITH_JOIN`: к каждой брони «приклеиваем» строку гостя и строку номера,
   чтобы сразу получить `full_name`, `room_number`, `room_type` для вывода.

6. **Проверка занятости** `BookingRepository.hasOverlap()`:
   ```sql
   SELECT COUNT(*) FROM bookings
   WHERE room_id = ? AND status <> 'CANCELLED' AND id <> ?
     AND check_in < ? AND check_out > ?   -- (старый заезд < новый выезд) И (старый выезд > новый заезд)
   ```
   Пример: бронь 15–20 ноября. Новая 18–22 → 15 < 22 и 20 > 18 → пересекаются. Новая 20–22 → 20 > 20 ложно → не пересекаются
   (выезд и заезд в один день — можно). `id <> ?` нужен при изменении брони, чтобы она не «мешала сама себе».

7. **Перевод типов**: `Date.valueOf(localDate)` (Java → SQL), `rs.getDate(...).toLocalDate()` (SQL → Java),
   enum в базе хранится строкой: `status.name()` → `"CREATED"`, обратно `BookingStatus.valueOf("CREATED")`.

**Что показать на защите:** `init.sql` (ограничения), ER-диаграмму, любой метод репозитория, `hasOverlap`, пункт меню 9.

**Вопросы и ответы:**
- *Зачем внешний ключ, если мы и так проверяем гостя в Java?* — База — последняя линия защиты: данные могут попасть
  не только через нашу программу (pgAdmin, другое приложение в КР2/КР3). FK гарантирует целостность всегда.
- *Что будет, если удалить гостя с бронированиями?* — Наш сервис не даст (правило 11). А если удалить в pgAdmin —
  база выдаст ошибку нарушения внешнего ключа.
- *Почему каждый метод открывает новое соединение?* — Так проще и надёжнее для учебного проекта. В больших системах
  используют пул соединений (в КР3 Spring сделает это сам).
- *Что такое ResultSet?* — Курсор по строкам результата. `next()` переходит на следующую строку и возвращает false, когда строки кончились.
- *Где хранится пароль?* — В `src/main/resources/database.properties`, не в Java-коде.

---

### Участник 2 — Модель предметной области и ООП

**Файлы:** `model/Guest.java`, `Room.java`, `Booking.java`, `BookingStatus.java`, `RoomType.java`,
`exception/BusinessException.java`, `EntityNotFoundException.java`, `repository/CrudRepository.java`,
`ui/BaseMenu.java` (как пример наследования), `ui/MenuAction.java`.

**Что нужно понимать:**

1. **Класс и объект.** Класс `Guest` — шаблон («у гостя есть id, ФИО, телефон, email»).
   Объект — конкретный гость: `new Guest("Иванов Иван", "+7 900...", "ivanov@mail.ru")`.

2. **Инкапсуляция.** Все поля `private` — снаружи к ним нельзя обратиться напрямую (`guest.email = ...` не скомпилируется).
   Доступ только через методы `getEmail()` / `setEmail(...)`. Так класс сам контролирует свои данные.

3. **Конструкторы.** В каждом классе два:
   - без `id` — для нового объекта (id выдаст база): `new Guest(fullName, phone, email)`;
   - с `id` — для объекта, прочитанного из базы.
   Первый вызывает второй через `this(0, fullName, phone, email)` — чтобы не дублировать код.

4. **enum** — фиксированный набор значений. `BookingStatus` может быть только CREATED, CONFIRMED, COMPLETED, CANCELLED.
   У нашего enum есть:
   - поле `title` и конструктор (русское название для вывода);
   - метод `canChangeTo(next)` — **бизнес-правило переходов** прямо в enum:
     ```java
     return switch (this) {
         case CREATED -> next == CONFIRMED || next == CANCELLED;
         case CONFIRMED -> next == COMPLETED || next == CANCELLED;
         case COMPLETED, CANCELLED -> false;
     };
     ```
   - переопределённый `toString()` → печатается «Создано», а в базу пишется `name()` → `"CREATED"`.

5. **Интерфейс** `CrudRepository<T>` — список методов без реализации (save, findAll, findById, update, deleteById).
   Его реализуют (`implements`) три класса. `<T>` — generic: подставляется Guest, Room или Booking.
   Интерфейс — это «договор»: любой репозиторий гарантированно умеет эти 5 операций.

6. **Наследование и абстрактный класс** — `BaseMenu`:
   - `abstract class` — нельзя создать `new BaseMenu()`, можно только наследоваться.
   - В нём готовый метод `run()` (цикл меню) и **абстрактные** методы `getTitle()`, `getItems()`, `handle()`.
   - `GuestMenu extends BaseMenu` — наследует `run()` и обязан реализовать абстрактные методы (`@Override`).
   - `super(input)` в конструкторе наследника вызывает конструктор родителя.

7. **Полиморфизм** — «один вызов, разное поведение»:
   - `BaseMenu.run()` вызывает `getTitle()`. Для `GuestMenu` это «ГОСТИ», для `RoomMenu` — «НОМЕРА».
     Код `run()` один, а работает по-разному в зависимости от реального объекта.
   - `findAll()` есть у всех трёх репозиториев, но каждый читает свою таблицу.
   - `toString()` переопределён в каждом классе модели — `System.out.println(obj)` печатает по-своему.
   - Перегрузка методов (статический полиморфизм): `readInt(prompt)`, `readInt(prompt, current)`, `readInt(prompt, min, max)` в `ConsoleInput`.

8. **Свои исключения.** `BusinessException` — нарушено правило; `EntityNotFoundException` — нет записи с таким ID.
   Наследуются от `RuntimeException` (unchecked), поэтому их не нужно указывать в `throws` у каждого метода.
   Разные классы нужны, чтобы по-разному их обработать (в `BaseMenu.execute` разные сообщения).

9. **Поля Booking для вывода** (`guestName`, `roomNumber`, `roomType`) — их нет в таблице bookings,
   их заполняет репозиторий из JOIN. В базе хранятся только `guest_id` и `room_id` (внешние ключи).

**Что показать на защите:** класс `Booking` (private-поля, конструкторы, `getNights()`), `BookingStatus.canChangeTo`,
`CrudRepository` + `implements`, `BaseMenu` + `GuestMenu`, исключения.

**Вопросы и ответы:**
- *Что такое инкапсуляция?* — Скрытие данных объекта (`private`) и доступ к ним только через методы класса.
- *Чем интерфейс отличается от абстрактного класса?* — Интерфейс — только «что уметь» (методы без тела, нет полей-состояния),
  класс может реализовать много интерфейсов. Абстрактный класс может содержать готовый код и поля
  (у `BaseMenu` есть поле `input` и готовый `run()`), но наследоваться можно только от одного класса.
- *Где полиморфизм?* — См. пункт 7 (лучший пример — `BaseMenu.run()` и 4 меню).
- *Зачем enum, а не String?* — Нельзя ошибиться в значении: компилятор не даст написать `BookingStatus.DONEE`.
  Плюс у enum могут быть свои методы (`canChangeTo`).
- *Почему исключения unchecked?* — Это ошибки бизнес-логики, которые ловятся в одном месте (меню);
  не нужно писать `throws` в каждом методе.
- *Что делает @Override?* — Говорит компилятору «я переопределяю метод родителя»; если ошибиться в названии — будет ошибка компиляции.

---

### Участник 3 — Бизнес-логика (сервисы), поиск, фильтры, сортировка, статистика

**Файлы:** `service/GuestService.java`, `RoomService.java`, `BookingService.java`.

**Что нужно понимать:**

1. **Зачем слой service.** Здесь «мозги»: проверки и правила. Меню только спрашивает данные,
   репозиторий только сохраняет. Задание требует: правила должны быть **в Java-коде**, а не только в интерфейсе.
   В КР2 (JavaFX) эти же сервисы будут вызываться из кнопок — правила не придётся переписывать.

2. **Как устроено правило** — проверка + исключение:
   ```java
   if (guestsCount > room.getCapacity()) {
       throw new BusinessException("Номер " + room.getRoomNumber() + " вмещает только " + room.getCapacity() + " чел.");
   }
   ```
   `throw` прерывает метод, до сохранения в базу дело не доходит.

3. **Optional и orElseThrow** — репозиторий возвращает `Optional<Guest>` («может быть пусто»):
   ```java
   return guestRepository.findById(id)
           .orElseThrow(() -> new EntityNotFoundException("Гость с ID " + id + " не найден."));
   ```

4. **Валидация строк регулярными выражениями** (`GuestService.validate`):
   `fullName.matches("[А-Яа-яЁёA-Za-z\\- ]+")` — только буквы, пробел, дефис. Аналогично телефон и email.

5. **Поиск, фильтрация, сортировка — Stream API.** Берём все брони из базы и обрабатываем цепочкой:
   ```java
   return bookingRepository.findAll().stream()                       // поток элементов списка
           .filter(b -> b.getGuestName().toLowerCase().contains(query)) // оставить подходящие
           .toList();                                                // собрать обратно в список
   ```
   - поиск: `searchByGuestName`, `searchByRoomNumber`;
   - фильтры: `filterByStatus`, `filterByRoomType`, `filterByCheckInPeriod`;
   - сортировка: `.sorted(Comparator.comparing(Booking::getCheckIn))`, `.reversed()` — обратный порядок,
     `.thenComparing(...)` — вторичный ключ сортировки.

6. **Статистика** `BookingService.getStatistics()`:
   - `Collectors.groupingBy(Booking::getStatus, Collectors.counting())` — сколько броней в каждом статусе (Map статус → число);
   - `mapToDouble(Booking::getTotalPrice).sum()` / `.average()` — сумма и среднее;
   - `.map(Booking::getRoomId).distinct().count()` — сколько разных номеров занято сегодня;
   - результат кладём в `LinkedHashMap` — он помнит порядок добавления, и показатели выводятся в нужном порядке
     (`HashMap` перемешал бы их).

7. **Расчёт цены**: `ChronoUnit.DAYS.between(checkIn, checkOut) * room.getPricePerNight()`.

8. **Почему некоторые операции в Java (stream), а не в SQL?** Задание прямо рекомендует Collections и Stream API.
   Для небольшой базы это нормально. Проверку занятости делаем SQL-запросом — так точнее и быстрее.

**Что показать на защите:** комментарий со списком правил в начале `BookingService`, методы `create` и `changeStatus`,
`checkBookingData`, любой фильтр/сортировку, `getStatistics`.

**Вопросы и ответы:**
- *Назовите 5 бизнес-правил и покажите в коде.* — См. раздел 1, всё в `BookingService`, `GuestService`, `RoomService`.
- *Где проверяется, что номер свободен?* — `BookingService.checkBookingData` → `bookingRepository.hasOverlap(...)`.
- *Как реализована сортировка?* — `stream().sorted(Comparator.comparing(...))`; Comparator определяет, как сравнить две брони.
- *Что такое лямбда `b -> b.getStatus() == status`?* — Короткая запись функции: на входе бронь `b`, на выходе true/false.
- *Что такое `Booking::getCheckIn`?* — Ссылка на метод, то же самое, что `b -> b.getCheckIn()`.
- *Почему LinkedHashMap?* — Сохраняет порядок вставки.
- *Как запрещён переход Создано → Завершено?* — `booking.getStatus().canChangeTo(newStatus)` вернёт false → `BusinessException`.

---

### Участник 4 — Консольный интерфейс, обработка ошибок, экспорт, запуск

**Файлы:** `Main.java`, `ui/BaseMenu.java`, `MainMenu.java`, `GuestMenu.java`, `RoomMenu.java`, `BookingMenu.java`,
`ConsoleInput.java`, `MenuAction.java`, `TablePrinter.java`, `util/ExcelExporter.java`, `pom.xml`.

**Что нужно понимать:**

1. **Запуск** (`Main.main`):
   - проверяет подключение к БД; если нет — понятное сообщение (по коду ошибки `SQLState`: неверный пароль `28P01`,
     нет базы `3D000`) и выход без падения;
   - создаёт репозитории → передаёт их в конструкторы сервисов → сервисы в `MainMenu`.
     Это называется **внедрение зависимостей через конструктор** (в КР3 это будет делать Spring).

2. **Цикл меню** (`BaseMenu.run`): `while (true)` → напечатать пункты → `readInt(..., 0, N)` → `0` = выход из цикла,
   иначе `execute(() -> handle(choice))`. После действия снова печатается меню — программа не завершается после одного действия.

3. **Обработка ошибок в одном месте** (`BaseMenu.execute`):
   ```java
   try {
       action.run();
   } catch (EntityNotFoundException e) { ... "Не найдено: ..." }
     catch (BusinessException e)       { ... "Ошибка: ..." }
     catch (SQLException e)            { ... код 08xxx -> "Ошибка подключения", иначе "Ошибка SQL-запроса" }
     catch (IOException e)             { ... "Ошибка работы с файлом" }
   ```
   `MenuAction` — функциональный интерфейс, поэтому действие можно передать лямбдой `() -> handle(choice)`.

4. **Ввод текста вместо числа** (`ConsoleInput.readInt`):
   ```java
   while (true) {
       String line = readLine(prompt);
       try {
           return Integer.parseInt(line);              // "abc" -> NumberFormatException
       } catch (NumberFormatException e) {
           System.out.println("Ошибка: нужно ввести целое число.");  // и спрашиваем снова
       }
   }
   ```
   Так же сделаны `readDouble` (принимает и `2500,50`), `readDate` (`DateTimeParseException`).
   Версии с `current` — для изменения записи: пустой ввод (Enter) оставляет старое значение.
   `choose(title, options)` — generic-метод выбора из массива (используется для enum'ов).
   `confirm(...)` — подтверждение «да/нет» перед удалением.

5. **Кодировка консоли** (`ConsoleInput.detectConsoleCharset`): в Windows консоль работает в cp866,
   без правильной кодировки русские буквы при вводе портятся. Можно задать вручную `-Dconsole.encoding=UTF-8`.

6. **Вывод таблиц** (`TablePrinter`): `System.out.printf("%-4s %-30s ...%n", ...)` — `%-30s` значит «строка шириной 30, выровнять влево».

7. **Экспорт в Excel** (`ExcelExporter`), библиотека Apache POI:
   `Workbook` (файл) → `createSheet` (лист) → `createRow` (строка) → `createCell(i).setCellValue(...)` (ячейка) →
   `workbook.write(out)`. Workbook и FileOutputStream открыты в try-with-resources.
   Если файл открыт в Excel — Windows не даст его перезаписать → `IOException` → сообщение «Ошибка работы с файлом».

8. **Maven, pom.xml**: `dependencies` — библиотеки (postgresql, poi-ooxml, log4j-core), Maven скачивает их сам.
   `exec-maven-plugin` позволяет запустить `mvn compile exec:java`. `maven.compiler.release=21` — версия Java.

**Что показать на защите:** запуск, ввод `abc` в меню, `BaseMenu.execute`, `ConsoleInput.readInt`, экспорт и открытие xlsx, `pom.xml`.

**Вопросы и ответы:**
- *Почему программа не падает при ошибке?* — Все действия меню выполняются через `execute()`, который ловит исключения.
- *Где SQL в меню?* — Нигде. Меню вызывает только методы сервисов.
- *Как работает экспорт?* — Меню берёт списки из сервисов и передаёт их в `ExcelExporter.export`, тот создаёт 3 листа через Apache POI.
- *Зачем pom.xml?* — Описание проекта для Maven: зависимости, версия Java, плагины. Без него пришлось бы вручную скачивать .jar-файлы.
- *Что такое лямбда в `execute(() -> handle(choice))`?* — Объект-действие, реализующий интерфейс `MenuAction`; `execute` вызывает его внутри try.

---

## 5. Сценарий демонстрации на защите

Перед защитой: выполнить `sql/init.sql`, запустить программу.
⚠ Тестовые брони рассчитаны на даты около 20.10.2026. Если защита позже — при создании брони берите **будущие** даты
относительно дня защиты (иначе сработает правило «нельзя бронировать задним числом» — это тоже можно показать).

| Шаг | Ввод | Что показываем |
|---|---|---|
| 1 | `9` | все таблицы БД |
| 2 | `abc` | «Ошибка: нужно ввести целое число.» — программа не упала |
| 3 | `1` → `1` → `Новиков Пётр Ильич`, `+7 901 777-77-77`, `novikov@mail.ru` | добавление гостя (получит ID 7) |
| 4 | `1` → тот же email `ivanov@mail.ru` | правило уникальности email |
| 5 | `3` → `999` | «Не найдено: Гость с ID 999 не найден.» |
| 6 | `5` → `1` → `да` | нельзя удалить гостя с бронями |
| 7 | `0`, `2` → `1` → `105`, `2` (Делюкс), `2`, `3500` | создание номера (ID 9) |
| 8 | `6` → `2026-11-15`, `2026-11-20` | свободные номера — 301 (ID 7) занят, его нет в списке |
| 9 | `0`, `3` → `1` → гость `7`, `2026-11-15`, `2026-11-18`, номер `7`, гостей `2` | «Номер 301 уже занят» |
| 10 | `1` → то же, номер `9`, гостей `3` | «Номер 105 вмещает только 2 чел.» |
| 11 | `1` → то же, номер `9`, гостей `2` | бронь создана, сумма 3 × 3500 = 10500 |
| 12 | `5` → ID новой брони (14) → `3` (Завершено) | запрещённый переход статуса |
| 13 | `5` → `14` → `2` (Подтверждено) | разрешённый переход |
| 14 | `4` → `1` | нельзя изменить завершённую бронь |
| 15 | `4` → `14` → Enter, Enter, `2026-11-19`, Enter | изменение, цена пересчитана (4 ночи) |
| 16 | `0`, `4` → `1` → `иван`; `4` → `2` → `101` | два способа поиска |
| 17 | `5` → `1` → `2`; `5` → `2` → `3`; `5` → `3` → период | три фильтра |
| 18 | `6` → `2` | сортировка по стоимости |
| 19 | `7` | статистика |
| 20 | `8` → Enter | экспорт, открыть `hotel_export.xlsx` |
| 21 | `0` | выход |

---

## 6. Вопросы из задания «На защите необходимо уметь объяснить» — короткие ответы

| Вопрос | Короткий ответ | Где в коде |
|---|---|---|
| Назначение основных классов | Model — данные, Repository — SQL, Service — правила, UI — меню | раздел 1 и 3 |
| Применение инкапсуляции | private-поля + get/set | `model/*` |
| Назначение интерфейсов | договор о методах; `CrudRepository` для всех репозиториев, `MenuAction` для лямбд | `repository/CrudRepository`, `ui/MenuAction` |
| Применение enum | статусы и типы номеров, правило переходов в enum | `model/BookingStatus`, `RoomType` |
| Применение полиморфизма | `BaseMenu.run()` + 4 меню; 3 реализации `CrudRepository`; `toString()` | `ui/BaseMenu` |
| Использование коллекций | `List/ArrayList` (результаты запросов), `Map/LinkedHashMap` (статистика), `Optional` | repository, `BookingService` |
| Обработка исключений | свои исключения в service, ловим в `BaseMenu.execute`; ввод — в `ConsoleInput` | `ui/BaseMenu`, `ui/ConsoleInput` |
| Работа JDBC | DriverManager → Connection → PreparedStatement → execute → ResultSet → закрыть | `repository/*` |
| Statement vs PreparedStatement | PreparedStatement с `?` защищает от SQL-инъекций | раздел 4, Участник 1 |
| Связи между таблицами | bookings.guest_id → guests.id, bookings.room_id → rooms.id, один-ко-многим | `init.sql`, ER |
| Основные бизнес-правила | см. список из 12 правил | `BookingService` (комментарий сверху) |
| Создание, изменение, удаление | меню → service (проверки) → repository (INSERT/UPDATE/DELETE) | `BookingMenu`, `BookingService`, `BookingRepository` |
| Поиск, фильтрация, сортировка | Stream API: filter / sorted + Comparator | `BookingService` |
| Экспорт данных | Apache POI → .xlsx с 3 листами | `util/ExcelExporter` |

---

# ЧАСТЬ 2. КР2 — JavaFX-приложение

## 7. Общая картина КР2 (знают ВСЕ)

КР2 = та же система, но с графическим интерфейсом. **База, модель, репозитории и сервисы — из КР1, без копирования.**
Новое — только пакет `fx` (контроллеры) и FXML/CSS в `src/main/resources/ru/mirea/hotel/fx/`.

```
 Пользователь кликает кнопку
     ▼
 FXML (View)            bookings-tab.fxml: <Button onAction="#onDelete"/>, <TableView fx:id="table">
     ▼  FXMLLoader связывает разметку с контроллером
 Controller             BookingsController.onDelete(): подтверждение -> bookingService.delete(id) -> refresh()
     ▼
 Service (из КР1)       BookingService: бизнес-правила
     ▼
 Repository / DAO (КР1) BookingRepository: SQL через JDBC
     ▼
 PostgreSQL hotel_db
```

Почему в сервисах КР1 нет ни одного `System.out.println`: они только возвращают данные или бросают исключения,
поэтому одинаково работают и с консолью, и с JavaFX. Это и есть смысл многослойной архитектуры.

### Путь действия «Добавить бронирование» в JavaFX (выучить всем)
1. Кнопка «Добавить» в `bookings-tab.fxml` имеет `onAction="#onAdd"` → вызывается `BookingsController.onAdd()`.
2. `FxDialogs.openForm(..., "booking-dialog.fxml", ...)` загружает FXML формы, `FXMLLoader` создаёт `BookingDialogController`.
3. В `initialize()` формы списки гостей и номеров загружаются из сервисов в `ComboBox`.
4. Пользователь выбирает гостя/номер/даты, внизу пересчитывается стоимость (`bookingService.calculatePrice`).
5. «Сохранить» → `FormController.save()` → `doSave()` → `bookingService.create(...)` (те же правила, что в КР1).
6. Ошибка → красный текст под формой, окно **не закрывается** (в `openForm` событие кнопки «съедается» `event.consume()`).
7. Успех → окно закрывается, `refresh()` перечитывает данные из базы, `ObservableList` обновляется → таблица перерисовывается.

### Файлы КР2

| Файл | Зачем | Кто |
|---|---|---|
| `pom.xml` (javafx-controls, javafx-fxml, javafx-maven-plugin) | зависимости и запуск `mvn javafx:run` | Участник 2 |
| `fx/HotelFxApp.java`, `HotelFxLauncher.java` | запуск JavaFX, проверка БД | Участник 2 |
| `fx/AppContext.java` | создание сервисов для контроллеров | Участник 1 |
| `main.fxml` + `MainController` | главное окно, вкладки (fx:include) | Участник 2 |
| `app.css` | оформление | Участник 2 |
| `bookings-tab.fxml` + `BookingsController` | таблица, поиск, фильтры, сортировка, экспорт | Участник 3 |
| `statistics-tab.fxml` + `StatisticsController` | статистика и диаграмма | Участник 3 |
| `fx/util/FxCells.java` | формат ячеек (даты, деньги), цвет строк | Участник 3 |
| `*-dialog.fxml` + `*DialogController`, `FormController` | формы, валидация | Участник 4 |
| `fx/util/FxDialogs.java`, `FxAction.java` | диалоги, обработка ошибок | Участник 4 |
| `guests-tab.fxml`, `rooms-tab.fxml` + контроллеры | связанные данные | Участник 4 |
| архив `student-hub-suite.zip`, Docker | развёртывание примера | Участник 1 |

---

## 8. Распределение по участникам (КР2)

### Участник 1 — Docker, пример из архива, подключение к базе

**Что нужно уметь:** развернуть пример из архива, объяснить, как работает Docker-часть и как наше приложение подключается к БД.

1. **Docker** — программа, которая запускает приложения в изолированных «контейнерах» из готовых «образов».
   - `docker compose` поднимает сразу несколько контейнеров по описанию в `backend/docker-compose.yml`:
     контейнер PostgreSQL (`postgres:17-alpine`) и контейнер backend (Spring Boot, собирается Maven-ом прямо в контейнере).
   - Параметры (имя БД, пароль, порты) берутся из файла `backend/.env`.
   - Порт: `127.0.0.1:5433:5433` — с компьютера база примера доступна на порту 5433, поэтому не конфликтует с нашей на 5432.
   - **Volume** (том) — место, где Docker хранит данные БД. `docker compose down` его не удаляет, `down -v` — удаляет.
2. **Порядок запуска примера:** Docker Desktop (Running) → `powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1`
   → проверка `http://localhost:8080/` и `http://localhost:8080/actuator/health` → только потом `javafx-client` (`mvn javafx:run`).
3. **Как проверить состояние:** `docker compose -f backend/docker-compose.yml ps` — у PostgreSQL статус `healthy`
   (прошёл healthcheck), backend запущен; `actuator/health` отвечает `{"status":"UP"}`.
4. **Чем пример отличается от нашей КР2:** их JavaFX-клиент ходит в **REST API** (HTTP → Spring → PostgreSQL),
   а наш по заданию подключается **напрямую через JDBC** к базе КР1.
5. **Наше подключение:** `database.properties` (адрес, логин, пароль — не в коде!) → `DatabaseManager.getConnection()` (КР1).
   `AppContext` один раз создаёт репозитории и сервисы — как `Main.java` в КР1; контроллеры берут сервисы оттуда,
   потому что контроллеры создаёт сам `FXMLLoader` (через конструктор без параметров).
6. **Проверка БД при старте:** `HotelFxApp.start()` открывает соединение; при ошибке показывает окно с понятной причиной
   (по коду SQLState: неверный пароль / нет базы / сервер не запущен) и закрывает приложение.

**Вопросы:**
- *Как запускается готовая система?* — Docker → `start-compose.ps1` (или `docker compose --env-file ... up -d --wait`) → проверка health → JavaFX-клиент.
- *Где пароль БД в нашем приложении?* — `src/main/resources/database.properties`, в Java-коде паролей нет.
- *Почему JavaFX не в Docker?* — Это настольное приложение с окнами, оно запускается на компьютере; в Docker — только сервер и база.
- *Если база в Docker, как подключиться по JDBC?* — К опубликованному порту: `jdbc:postgresql://localhost:5433/...`.

---

### Участник 2 — запуск JavaFX, FXML, контроллеры, CSS

**Файлы:** `HotelFxApp.java`, `HotelFxLauncher.java`, `main.fxml`, `MainController.java`, `app.css`, `pom.xml`.

1. **Stage, Scene, Node** (100% спросят):
   - `Stage` — окно (рамка, заголовок, кнопки свернуть/закрыть). Главный Stage JavaFX передаёт в `start(Stage stage)`.
   - `Scene` — содержимое окна, «сцена»: `new Scene(root, 1280, 780)`. Одна сцена на окно в каждый момент.
   - `Node` — любой элемент внутри сцены: кнопка, таблица, панель. Узлы образуют дерево (scene graph): BorderPane → TabPane → ...
2. **Запуск:** `Application.launch()` → JavaFX создаёт поток интерфейса (JavaFX Application Thread) и вызывает `start()`.
   `HotelFxLauncher` нужен, чтобы запускать из IntelliJ без ошибки «JavaFX runtime components are missing».
   `mvn javafx:run` использует `javafx-maven-plugin` из `pom.xml`.
3. **FXML** — XML-файл с описанием интерфейса (какие элементы, где, с какими надписями). Зачем: разметка отдельно от логики,
   её легче читать и менять, Java-код не превращается в кашу из `new Button(...)`.
4. **Связь FXML и Controller:**
   - `fx:controller="ru.mirea.hotel.fx.controller.BookingsController"` — какой класс управляет разметкой;
   - `fx:id="table"` в FXML ↔ `@FXML private TableView<Booking> table;` в контроллере — FXMLLoader сам кладёт объект в поле;
   - `onAction="#onAdd"` ↔ `@FXML private void onAdd()` — обработчик события (нажатие кнопки);
   - `@FXML private void initialize()` — FXMLLoader вызывает после того, как все `@FXML`-поля заполнены.
5. **fx:include** в `main.fxml` — каждая вкладка в своём FXML со своим контроллером. Контроллер вложенного файла
   попадает в поле `<fx:id>Controller`: `fx:id="bookingsView"` → `@FXML BookingsController bookingsViewController`.
   `MainController` при переключении вкладки вызывает её `refresh()`.
6. **CSS** (`app.css`): подключён в FXML `stylesheets="@app.css"`. `styleClass="primary-button"` в FXML = `.primary-button {}` в CSS.
   Свойства начинаются с `-fx-`: `-fx-background-color`, `-fx-text-fill`. Оформлены кнопки, таблицы, поля, шапка, ошибки
   (`.error-label`, `.field-error`), строки таблицы по статусу (`.status-cancelled`).
   `FlowPane` в панели фильтров переносит элементы на новую строку, если окно узкое.

**Вопросы:**
- *Что такое Stage, Scene, Node?* — см. п.1.
- *Зачем FXML?* — Разделить внешний вид и логику; см. п.3.
- *Как FXML связан с контроллером?* — fx:controller, fx:id + @FXML, onAction="#метод"; см. п.4.
- *Где обработчики событий?* — Методы `onXxx()` контроллеров; лямбды-слушатели `valueProperty().addListener(...)` для полей фильтра.

---

### Участник 3 — таблицы, поиск, фильтры, сортировка, статистика, экспорт

**Файлы:** `bookings-tab.fxml`, `BookingsController.java`, `statistics-tab.fxml`, `StatisticsController.java`, `FxCells.java`.

1. **TableView и столбцы:** в FXML `<TableColumn text="Гость"><cellValueFactory><PropertyValueFactory property="guestName"/>` —
   значение ячейки берётся из метода `getGuestName()` объекта Booking. Технические поля (guest_id, room_id) не показываем.
2. **ObservableList** — список, который сообщает об изменениях. Таблица «подписана» на него:
   `bookings.setAll(новыйСписок)` → таблица сама перерисовалась. Обычный `ArrayList` так не умеет.
3. **Как обновляются данные:** после добавления/изменения/удаления вызывается `refresh()` → `bookingService.search(...)`
   читает из базы → `bookings.setAll(...)`. Также кнопка «Обновить» и переключение вкладок.
4. **Поиск и фильтры:** поля в FXML; в `initialize()` на каждое повешен слушатель (`textProperty().addListener`,
   `valueProperty().addListener`) → `refresh()`. Сама фильтрация — в сервисе `BookingService.search(text, status, type, from, to)`:
   цепочка `stream().filter(...)`; `null` = фильтр не задан. Текст ищется в ФИО гостя и номере комнаты.
5. **Сортировка:** `SortedList` поверх `bookings`, его компаратор привязан к таблице:
   `sorted.comparatorProperty().bind(table.comparatorProperty())`. Клик по заголовку столбца меняет порядок;
   выпадающий список «Сортировка» делает то же программно (`table.getSortOrder().setAll(column)`).
   Сортировка по датам и деньгам правильная, т.к. в ячейке хранится `LocalDate`/`Double`, а формат «20.10.2026» и
   «22 500,00 руб.» задаёт только `CellFactory` (`FxCells.dateColumn/moneyColumn`).
6. **Статистика:** `StatisticsController.refresh()` берёт `bookingService.getStatistics()` (те же 10+ показателей, что в КР1)
   и раскладывает в `GridPane`; `countByStatus()` → `PieChart` (круговая диаграмма с легендой).
7. **Экспорт:** `FileChooser.showSaveDialog()` — пользователь сам выбирает путь (абсолютных путей в коде нет);
   выгружается то, что сейчас в таблице (`table.getItems()` — с учётом фильтров), через `ExcelExporter` из КР1.

**Вопросы:**
- *Как обновляются данные в TableView?* — Меняем ObservableList → таблица перерисовывается сама; см. п.2–3.
- *Зачем ObservableList?* — Чтобы интерфейс автоматически реагировал на изменение данных.
- *Как реализован поиск и фильтрация?* — Слушатели полей → `refresh()` → `BookingService.search` (Stream API).
- *Как работает встроенная сортировка TableView?* — По клику столбец задаёт компаратор таблицы; SortedList сортирует по нему.
- *Как рассчитывается статистика?* — В `BookingService.getStatistics()` (Stream API, groupingBy) — контроллер только показывает.

---

### Участник 4 — формы, валидация, ошибки, связанные сущности

**Файлы:** `booking-dialog.fxml`, `guest-dialog.fxml`, `room-dialog.fxml`, `FormController.java`, `*DialogController.java`,
`FxDialogs.java`, `FxAction.java`, `guests-tab.fxml`, `rooms-tab.fxml`, `GuestsController.java`, `RoomsController.java`.

1. **Формы:** корень FXML формы — `DialogPane`. `FxDialogs.openForm()` загружает его, добавляет кнопки «Сохранить»/«Отмена»
   и показывает в `Dialog`. Для изменения в контроллер передаётся запись (`setBooking(booking)`) → поля заполняются текущими значениями.
2. **FormController** — абстрактный родитель всех форм: `save()` общий (очистить ошибку → `doSave()` → поймать исключение →
   показать текст в `errorLabel`), а `doSave()` у каждой формы свой. Тот же приём, что `BaseMenu` в КР1 (шаблонный метод + полиморфизм).
3. **Окно не закрывается при ошибке:** на кнопку «Сохранить» повешен `addEventFilter(ActionEvent.ACTION, ...)`:
   если `save()` вернул false → `event.consume()` → диалог не получает нажатие и остаётся открытым.
4. **Валидация (что проверяется):**
   - пустые поля и выбор из списка: «Поле «Гость»: выберите гостя из списка»;
   - числа: `readInt/readDouble` — не число → поле красное (`field-error`) + «Поле «Количество гостей»: введите целое число»;
   - диапазоны, длины строк, формат телефона/email, уникальность email и номера комнаты, даты (выезд позже заезда, не в прошлом),
     вместимость, занятость номера — это бизнес-правила из КР1 в сервисах; сообщение называет поле и что исправить.
   - даты выбираются только из календаря (`DatePicker editable="false"`), поэтому неверный текст даты ввести нельзя.
5. **Связанные сущности:** в форме бронирования гость и номер — `ComboBox<Guest>` и `ComboBox<Room>`
   (ID вручную не вводится). `StringConverter` задаёт, как объект выглядит в списке: «101 · Стандарт · мест: 2 · 2 500,00 руб.».
   На вкладках «Гости» и «Номера» при выборе строки снизу показываются её бронирования; на вкладке «Бронирования» — данные гостя и номера.
6. **Диалоги** (`FxDialogs`): `error` (ошибка), `info` (успешный экспорт), `confirm` (Да/Нет перед удалением),
   `choose` (`ChoiceDialog` — выбор нового статуса, предлагаются только разрешённые `canChangeTo`).
7. **Обработка исключений:** все действия контроллеров обёрнуты в `FxDialogs.run(owner, () -> ...)`:
   ловит `SQLException`, `IOException`, `RuntimeException` (наши BusinessException/EntityNotFoundException) и показывает
   `toMessage(e)` в окне. Пустых `catch` нет, stack trace пользователю не показывается.

**Вопросы:**
- *Как проверяются пользовательские данные?* — Сначала форма (числа, выбор из списков), потом сервис (все бизнес-правила).
- *Как обрабатываются исключения?* — `FxDialogs.run` и `FormController.save`; см. п.7.
- *Почему гостя выбирают из списка, а не вводят ID?* — Требование КР2 и защита от ошибок: несуществующего гостя выбрать нельзя.
- *Где бизнес-логика?* — В сервисах (КР1). Контроллеры её не дублируют.

---

## 9. Сценарий демонстрации КР2

Перед защитой: выполнить `sql/init.sql`, запустить `HotelFxLauncher` (или `mvn javafx:run`).
⚠ Даты тестовых данных рассчитаны на 20.10.2026 — для новых броней берите будущие даты относительно дня защиты.

| Шаг | Действие | Что показываем |
|---|---|---|
| 1 | Окно открылось, вкладка «Бронирования» | TableView, цвет строк по статусу |
| 2 | Ввести в поиск `иван` | поиск по ФИО; потом `101` — по номеру |
| 3 | Статус «Подтверждено», тип «Люкс», период заезда | фильтры (сочетаются); «Сбросить фильтры» |
| 4 | Клик по заголовку «Стоимость»; список «Сортировка» | два способа сортировки |
| 5 | Выбрать строку | внизу данные гостя и номера (связанные данные) |
| 6 | «Добавить»: ничего не выбрать → «Сохранить» | сообщение «выберите гостя» |
| 7 | Гость, номер 301, 16.11–18.11, гостей `abc` | поле подсвечено красным |
| 8 | Гостей `2` | «Номер 301 уже занят» — окно не закрылось |
| 9 | Номер 302, гостей 2 → «Сохранить» | бронь появилась в таблице, стоимость посчитана |
| 10 | Выбрать новую бронь → «Сменить статус» | предлагаются только разрешённые статусы |
| 11 | Выбрать завершённую бронь → «Изменить» | «Нельзя изменить бронирование в статусе «Завершено»» |
| 12 | Новую бронь → «Удалить» → «Нет», потом «Да» | подтверждение удаления |
| 13 | Вкладка «Гости»: выбрать гостя; «Добавить гостя» с email `ivanov@mail.ru` | связанные брони; уникальность email |
| 14 | «Удалить» гостя с бронями | понятная ошибка |
| 15 | Вкладка «Номера»: даты → «Показать свободные» | поиск свободных номеров |
| 16 | Вкладка «Статистика» | показатели + диаграмма |
| 17 | «Экспорт в Excel» → выбрать папку | FileChooser, открыть файл |
| 18 | (по желанию) остановить PostgreSQL и нажать «Обновить» | понятная ошибка подключения, программа не упала |

---

## 10. Вопросы из задания КР2 — короткие ответы

| Вопрос | Ответ | Где |
|---|---|---|
| Как запускается система из архива через Docker, как проверить PostgreSQL и backend | Docker Desktop → `start-compose.ps1` → `docker compose ps` (healthy) → `/actuator/health` = UP | раздел 8, Участник 1 |
| Как запускается JavaFX-приложение | `HotelFxLauncher` в IDEA или `mvn javafx:run` → `HotelFxApp.start()` | `fx/HotelFxApp.java` |
| Stage, Scene, Node | окно / содержимое окна / любой элемент | Участник 2, п.1 |
| Зачем FXML | интерфейс отдельно от логики | `*.fxml` |
| Как FXML связан с Controller | `fx:controller`, `fx:id` + `@FXML`, `onAction="#..."`, `initialize()` | Участник 2, п.4 |
| Как обновляются данные в TableView | меняем ObservableList (`setAll`) → таблица перерисовывается | `BookingsController.refresh()` |
| Зачем ObservableList | таблица автоматически видит изменения списка | Участник 3, п.2 |
| Где бизнес-логика | в сервисах из КР1 | `service/*` |
| Где работа с данными | в репозиториях (DAO) из КР1, SQL через JDBC | `repository/*` |
| Как обрабатываются исключения | `FxDialogs.run`, `FormController.save`, окно Alert | `fx/util/FxDialogs.java` |
| Как проверяются данные | форма (числа, выбор) + сервис (правила) | Участник 4, п.4 |
| Как реализован поиск и фильтрация | слушатели полей → `BookingService.search` | `BookingsController.initialize()` |
| Как рассчитывается статистика | `BookingService.getStatistics()`, `countByStatus()` | `StatisticsController` |
| Как выполняется экспорт | FileChooser → `ExcelExporter.export` текущих строк таблицы | `BookingsController.onExport()` |

### Чего по заданию КР2 делать нельзя — и как это соблюдено
| Запрет | У нас |
|---|---|
| весь код в одном классе/контроллере | 8 FXML, 8 контроллеров + общий `FormController`, помощники `FxDialogs`, `FxCells` |
| SQL в обработчике кнопки | SQL только в `repository` |
| дублировать код в контроллерах | диалоги и обработка ошибок — `FxDialogs`, формат ячеек — `FxCells`, общая логика форм — `FormController` |
| пустые обработчики / неработающие кнопки | все кнопки работают; без выбора строки кнопки «Изменить/Удалить» неактивны |
| пустой catch, stack trace пользователю | ошибки показываются текстом в окне |
| пароль БД в коде | `database.properties` |
| абсолютные пути | `FileChooser` |
| данные без проверки | валидация в формах и сервисах |
| консоль как основной интерфейс | весь интерфейс в окнах |

---

# Часть 3. КР3 — Spring Boot backend

## 11. Общая картина КР3 (знают ВСЕ)

КР3 — та же гостиница, но теперь это **сервер**: программа без окон, которая слушает HTTP-запросы на порту 8080
и отвечает JSON. Клиенты — наш web-интерфейс в браузере, Swagger, а в КР4 — Android-приложение.
Код — в папке `backend/` (отдельный Maven-проект, пакет `ru.mirea.hotel.backend`). КР1/КР2 не тронуты.

```
 Браузер / Android / Swagger
     │  HTTP-запрос: GET /api/bookings?q=номер:301   Authorization: Bearer <JWT>
     ▼
 Фильтры Spring Security     CorrelationIdFilter -> JwtAuthenticationFilter -> PasswordChangeRequiredFilter
     │  кто пользователь? какие роли? не временный ли пароль?
     ▼
 controller/                 BookingController.search(): @PreAuthorize проверяет роль, принимает DTO
     ▼
 service/                    BookingService: бизнес-правила, @Transactional
     ▼
 repository/                 BookingRepository (Spring Data JPA) — SQL пишет Hibernate
     ▼
 PostgreSQL в Docker         схема создана миграциями Flyway V1..V4
```

Отличия от КР1/КР2, которые надо уметь назвать:

| КР1/КР2 | КР3 |
|---|---|
| SQL руками через JDBC (`PreparedStatement`) | Spring Data JPA + Hibernate: сущность `@Entity`, запросы генерируются |
| таблицы создаёт `sql/init.sql` | таблицы создаёт **Flyway** по миграциям `V1__...sql`; Hibernate только проверяет (`ddl-auto: validate`) |
| объекты создаём сами в `Main`/`AppContext` | объекты (бины) создаёт **Spring** и сам передаёт в конструкторы (внедрение зависимостей) |
| ID — `SERIAL` | ID — `UUID` + человекочитаемый код `BK-000014` |
| нет пользователей | вход, JWT, роли VIEWER / MANAGER / ADMIN, аудит |
| удаление строки | архив (`archived = true`) + окончательное удаление только ADMIN |
| PostgreSQL ставится руками | PostgreSQL и backend в Docker одной командой |

### Путь запроса «Создать бронь» (выучить всем)
1. Web-интерфейс (`app.js`, функция `bindBookingForm`) отправляет `POST /api/bookings` с JSON и заголовком `Authorization: Bearer <access JWT>`.
2. `JwtAuthenticationFilter` проверяет подпись токена (`JwtService.parse`), находит пользователя в БД и кладёт его в SecurityContext.
3. `BookingController.create` — `@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")`; у VIEWER здесь будет 403.
4. `@Valid` проверяет `BookingRequest` (Bean Validation: обязательные поля, 1–6 гостей). Ошибка → 400 со списком полей.
5. `BookingService.create` → `check(...)`: гость и номер есть, номер активен, даты, вместимость, номер не занят (`hasOverlap`) → иначе 400/409.
6. Считается цена (ночи × цена номера) и риск отмены (`CancellationRiskCalculator`), код `BK-...` берётся из sequence.
7. `bookings.saveAndFlush` → Hibernate делает `INSERT`; `audit.record(...)` пишет запись в журнал аудита в той же транзакции.
8. Сущность превращается в `BookingResponse` (DTO) → Spring превращает его в JSON → ответ **201 Created**.
9. Любое исключение ловит `GlobalExceptionHandler` и отдаёт единый JSON `ApiError` с нужным кодом (400/401/403/404/409).

### Вход и токены (выучить всем)
- `POST /api/auth/login` → сервер проверяет BCrypt-хеш пароля → выдаёт **access JWT** (15 минут, подписан секретом из `.env`)
  и **refresh-токен** (случайная строка, в БД лежит только её SHA-256, живёт 14 дней).
- Каждый запрос несёт access JWT. Истёк → клиент вызывает `POST /api/auth/refresh` → получает новую пару, старый refresh больше не работает.
- `logout` отзывает refresh-сессию. 5 неверных паролей подряд — блокировка на 5+ минут.
- Новый пользователь получает **временный пароль** — пока не сменит, все `/api/**`, кроме `/api/auth/*`, отвечают 403.

### Файлы КР3

| Файл / папка | Зачем | Кто |
|---|---|---|
| `backend/docker-compose.yml`, `Dockerfile`, `*.ps1`, `.env.example` | запуск в Docker, секреты | Участник 1 |
| `db/migration/V1..V4`, `domain/*`, `repository/*` | схема БД, сущности, Spring Data | Участник 1 |
| `security/*`, `config/SecurityConfig`, `AuthController`, `AuthService`, `LoginAttemptService` | вход, JWT, роли, пароли | Участник 2 |
| `AdminUserController`, `UserAdminService`, `AuditService`, `AuditController`, `web/CorrelationIdFilter` | пользователи, аудит | Участник 2 |
| `BookingService`, `CancellationRiskCalculator`, `filter/*`, `web/PageableFactory`, `AnalyticsService` | правила, поиск, фильтры, сортировка, аналитика | Участник 3 |
| `BookingController`, `NoteService`, `CsvBookingService`, `RoomService`, `GuestService`, `dto/*`, `error/*` | REST API, CSV, заметки, справочники, ошибки | Участник 4 |
| `static/index.html`, `app.js`, `styles.css` | web-интерфейс | Участник 4 (+ все показывают) |
| `src/test/*` | тесты | каждый — тест своей части |

---

## 12. Распределение по участникам (КР3)

### Участник 1 — Docker, PostgreSQL, Flyway, сущности

1. **Docker Compose** (`backend/docker-compose.yml`): два сервиса — `db` (образ `postgres:17-alpine`) и `backend` (собирается из `Dockerfile`).
   - `depends_on: condition: service_healthy` — backend стартует, только когда PostgreSQL готов (`pg_isready`).
   - healthcheck backend — запрос `GET /actuator/health`, ответ должен содержать `UP`.
   - `ports: "127.0.0.1:55432:5432"` — порт открыт только на этом компьютере.
   - `volumes: hotel_pgdata` — данные базы переживают `docker compose down` (удаляет только `down -v`).
   - Переменные `${POSTGRES_PASSWORD}` берутся из корневого `.env` (`--env-file .env`). Секретов в коде нет.
2. **Dockerfile** двухэтапный: образ с Maven собирает jar, в итоговый лёгкий образ с JRE копируется только jar; запуск не от root.
3. **Flyway**: при старте смотрит таблицу `flyway_schema_history` и выполняет новые файлы `V<номер>__<описание>.sql` по порядку.
   Уже выполненную миграцию менять нельзя — только добавлять новую. V1 = таблицы КР1, V2 = пользователи/аудит,
   V3 = новые поля и индексы (`ALTER TABLE ... ADD COLUMN`), V4 = демо-данные.
4. **Ограничения в БД**: `PRIMARY KEY`, `REFERENCES` (внешние ключи), `UNIQUE (code)`, `CHECK (check_out > check_in)`,
   `EXCLUDE USING gist (room_id WITH =, daterange(...) WITH &&)` — PostgreSQL сам не даст двум активным броням занять номер на пересекающиеся даты.
5. **JPA-сущности** (`domain/`): `@Entity @Table(name="bookings")`, `@Id UUID`, `@ManyToOne` (бронь → гость, номер),
   `@Enumerated(EnumType.STRING)` (статус хранится текстом), `@ElementCollection` (теги в `booking_tags`),
   `@Version` (optimistic locking), общий родитель `AuditedVersionedEntity` (`createdAt`, `updatedAt`, `version`, `@PrePersist`/`@PreUpdate`).
6. **Spring Data**: `interface BookingRepository extends JpaRepository<Booking, UUID>` — методы `findById`, `save`, `findAll(spec, pageable)`
   уже готовы; `countByArchivedFalse()` — запрос по имени метода; `@Query` — свой JPQL (аналитика, `hasOverlap`).

Показать: `docker compose ps` (healthy), `\dt` в psql, таблицу `flyway_schema_history`, файл V3.

### Участник 2 — безопасность: вход, JWT, роли, пароли, пользователи, аудит

1. **SecurityConfig**: `STATELESS` (сервер не хранит сессию — всё в токене), открыты только статика, `/api/auth/login|refresh|logout`, health;
   `/api/**` — только с токеном; Swagger — только ADMIN. `@EnableMethodSecurity` включает `@PreAuthorize` на методах контроллеров.
2. **JwtService**: `issue` — создаёт JWT (subject = id пользователя, роли, срок 15 мин, подпись HMAC секретом из `.env`); `parse` — проверяет подпись и срок.
3. **JwtAuthenticationFilter**: читает `Authorization: Bearer ...`, проверяет токен, **роли и активность берёт из БД** (отключённый пользователь сразу теряет доступ).
4. **Refresh** (`AuthService.refresh`): ротация — старый токен отзывается, выдаётся новый; повторное предъявление старого = кража → отзыв всех сессий.
5. **Пароли**: `BCryptPasswordEncoder(12)` (медленный хеш с солью), `PasswordPolicy` (12+ символов, разные типы, без логина),
   `LoginAttemptService` (5 ошибок → блокировка), `PasswordChangeRequiredFilter` (временный пароль → 403 везде, кроме смены пароля).
6. **Пользователи** (`UserAdminService`): создание с серверным временным паролем (`TemporaryPasswordGenerator`, `SecureRandom`), изменение ролей,
   отключение, сброс пароля; нельзя отключить себя.
7. **Аудит**: `AuditService.record(...)` вызывается из сервисов; входы пишутся в отдельной транзакции (`REQUIRES_NEW`), чтобы неудачный вход
   сохранился, хотя запрос закончился ошибкой. **Correlation ID** — метка запроса из `CorrelationIdFilter`, есть в заголовке ответа, в аудите и в ошибках.

Показать: вход под 3 ролями, 403 у VIEWER, смену временного пароля, блокировку после 5 ошибок, аудит с поиском `LOGIN_FAILED`.

### Участник 3 — бизнес-логика, поиск, фильтры, сортировка, пагинация, аналитика

1. **BookingService** — все правила (список в Javadoc класса): даты, вместимость, занятость номера, переходы статусов,
   запрет менять завершённую бронь, архив. Метод `check(...)` **только проверяет**, `apply(...)` **только меняет** —
   поэтому ошибка в одной записи массовой операции или строке CSV не оставляет «полуизменённую» бронь.
2. **Optimistic locking**: клиент присылает `version`, которую видел; если в БД уже другая — 409 «изменил другой пользователь».
   Плюс `@Version` в Hibernate: `UPDATE ... WHERE id=? AND version=?`.
3. **CancellationRiskCalculator** — вычисляемый показатель (баллы → LOW/MEDIUM/HIGH/CRITICAL), тест `CancellationRiskCalculatorTest`.
4. **Поиск и фильтры**: `BookingFilterParser` разбирает строку `q` (`номер:301 статус:подтверждено сумма>=20000 ...`) в объект `BookingFilter`;
   `BookingSpecifications` строит из него запрос через **Criteria API** (`cb.equal`, `cb.like`, `cb.greaterThanOrEqualTo`).
   **SQL-инъекция невозможна**: строка пользователя никогда не склеивается с SQL, значения уходят параметрами.
5. **Сортировка и пагинация**: `PageableFactory` — короткое имя поля (`price`) → поле сущности (`totalPrice`) только из белого списка;
   `size` 1–100. Spring Data сам добавляет `ORDER BY ... LIMIT ... OFFSET` и считает `totalElements`.
6. **Аналитика** (`AnalyticsService`): `count`, `avg`, `sum`, `group by` выполняет PostgreSQL (JPQL в `BookingRepository`).

Показать: фильтры из KR3.md, сортировку «Сумма ↓», «На странице: 5», 409 при старой version, аналитику.

### Участник 4 — REST API, DTO, ошибки, CSV, заметки, справочники, web-интерфейс

1. **REST-контроллеры**: `@RestController`, `@RequestMapping("/api/bookings")`, `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping`,
   `@PathVariable UUID id`, `@RequestParam`, `@RequestBody`. Статусы: 201 при создании, 204 без тела.
   `DELETE /api/bookings/{id}` = архив, `DELETE .../permanent` = удаление (ADMIN + заголовок с кодом брони).
2. **DTO** (`dto/*`, Java `record`): `BookingRequest` (что принимаем) и `BookingResponse` (что отдаём). Сущность наружу не отдаём —
   иначе утекут служебные поля и ленивые связи. Валидация аннотациями `@NotNull`, `@Size`, `@Min`, `@Pattern`.
3. **Ошибки** (`GlobalExceptionHandler`, `@RestControllerAdvice`): исключение → `ApiError` JSON; stack trace только в лог.
4. **Массовые операции** (`POST /bulk`): цикл по UUID, ошибки собираются в `failures`, операция не прерывается.
5. **CSV** (`CsvBookingService`, Apache Commons CSV): `preview` — проверка без записи (`@Transactional(readOnly = true)`),
   `apply` — запись корректных строк, режимы CREATE_ONLY / UPSERT_BY_CODE; экспорт по текущему фильтру, защита от формул Excel (`'=`).
6. **Заметки** (`NoteService`): автор и время, закрепление, менять может автор или ADMIN.
7. **Справочники**: номера (`RoomService`, вкл/выкл без удаления, только ADMIN), гости (`GuestService`).
8. **Web-интерфейс** (`static/app.js`): `fetch` к API, access-токен в памяти, refresh в HttpOnly-cookie, при 401 — автоматический refresh.

Показать: Swagger, CSV preview → apply с файлом `backend/examples/bookings-import.csv`, массовую операцию с ошибками, заметки.

---

## 13. Сценарий демонстрации КР3

Подготовка: Docker Desktop запущен; `powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1`;
для чистых данных перед защитой — `stop-compose.ps1 -ResetData` и снова `start-compose.ps1`.

1. Docker Desktop → `hotel-booking`: `db` и `backend` *healthy*. Открыть http://localhost:8080/actuator/health. (Участник 1)
2. Вход `admin@hotel.local` с паролем из `.env` → смена временного пароля. (Участник 2)
3. «Пользователи» → создать менеджера и наблюдателя, показать временные пароли; войти под наблюдателем в окне инкогнито — нет кнопок, 403. (Участник 2)
4. «Бронирования» → «+ Бронь» → сумма и риск посчитаны сервером; «Изменить» → подтвердить, предоплата → риск LOW. (Участник 4/3)
5. Тот же номер на те же даты → 409; гостей больше мест → 400. (Участник 3)
6. Поиск: `номер:301`, `статус:подтверждено предоплата:нет`, `сумма>=20000`, `тег:vip`; сортировка и размер страницы. (Участник 3)
7. «В архив» → `архив:да` → «Восстановить»; ADMIN удаляет архивную бронь с вводом кода. (Участник 4)
8. Массовая операция по нескольким броням с ошибками. Заметки: добавить, закрепить, изменить. (Участник 4)
9. «Импорт CSV» → проверить (3 + 4 ошибки) → применить; «Экспорт CSV». (Участник 4)
10. «Аналитика», «Журнал аудита» (поиск `LOGIN_FAILED`, correlation ID). (Участник 3 / 2)
11. Swagger UI. Миграции V1–V4 и `\dt` в psql. (Участник 4 / 1)
12. `cd backend; mvn test` — 38 тестов зелёные. (любой)

---

## 14. Вопросы КР3 — короткие ответы

| Вопрос | Ответ | Где |
|---|---|---|
| Что такое REST | ресурсы по URL + HTTP-методы (GET читать, POST создать, PUT изменить, DELETE удалить), ответ JSON, сервер не хранит состояние клиента | `controller/*` |
| Что делает Spring Boot | запускает встроенный Tomcat, сам создаёт объекты (бины) и связывает их через конструкторы, настройки в `application.yml` | `HotelBackendApplication` |
| Зачем слои controller/service/repository | контроллер — HTTP, сервис — правила и транзакции, репозиторий — БД; каждый меняется отдельно | раздел 11 |
| Что такое JPA / Hibernate | ORM: класс `@Entity` ↔ таблица, Hibernate сам пишет SQL | `domain/*` |
| Зачем Flyway и `ddl-auto: validate` | схема меняется только версионными SQL-миграциями, одинаково у всех; Hibernate лишь проверяет, что сущности совпадают с таблицами | `db/migration` |
| Что такое JWT | подписанная строка с id и ролями пользователя; сервер проверяет подпись и срок, в БД не ходит за сессией | `JwtService` |
| Зачем refresh-токен | access живёт 15 минут (украли — ненадолго), refresh позволяет получить новый без пароля и может быть отозван на сервере | `AuthService.refresh` |
| Как хранятся пароли | только BCrypt-хеш с солью; временный пароль показывается один раз | `UserAccount.passwordHash` |
| Как защищены от перебора | 5 ошибок → блокировка на 5+ минут; одинаковый ответ для неверного email и пароля | `LoginAttemptService` |
| Чем отличаются роли | VIEWER только читает; MANAGER + создаёт/меняет/архивирует, CSV, заметки; ADMIN + справочник номеров, пользователи, аудит, удаление, Swagger | `@PreAuthorize` |
| Что такое optimistic locking | поле `version`; при изменении сравнивается с присланным; не совпало → 409, чужие изменения не затираются | `verifyVersion`, `@Version` |
| Почему архив вместо удаления | данные и история сохраняются, бронь можно восстановить | `Booking.archive()` |
| Как сделан фильтр без SQL-инъекций | парсер → типизированный объект → Criteria API с параметрами; сортировка из белого списка | `filter/*`, `PageableFactory` |
| Как работает пагинация | `Pageable(page, size, sort)` → `LIMIT/OFFSET` + `count`; ответ `PageResponse` | `PageResponse` |
| Как работает импорт CSV | preview проверяет всё без записи и считает корректные/ошибочные строки; apply записывает корректные | `CsvBookingService` |
| Что такое correlation ID | метка HTTP-запроса; по ней ошибку из ответа находят в аудите и логах | `CorrelationIdFilter` |
| Почему контроллер не отдаёт @Entity | утечка полей (хеши, связи), ленивые связи вне транзакции, жёсткая связь API со схемой БД | `dto/*`, `BookingMapper` |
| Какие HTTP-коды и когда | 200/201/204 — успех; 400 — данные/правило; 401 — нет входа; 403 — нет прав; 404 — нет записи; 409 — конфликт версии/занятый номер | `GlobalExceptionHandler` |
| Как запустить и как проверить, что работает | `start-compose.ps1` → healthy → `/actuator/health` = UP | `KR3.md` |
| Какие тесты есть | контекст, риск/статусы, фильтр (парсер + запросы к БД), пароли, сквозной сценарий безопасности | `backend/src/test` |

## 15. Возможный лайвкодинг — куда смотреть

| Попросят | Что менять |
|---|---|
| новый фильтр, например `гостей>=3` | `BookingFilter` (поле), `BookingFilterParser` (регулярка как `PRICE`), `BookingSpecifications` (`compare(... root.get("guestsCount") ...)`), тест в `BookingFilterParserTest` |
| новое поле сортировки | одна строка в `PageableFactory.BOOKING_FIELDS` |
| новое бизнес-правило | `BookingService.check(...)` — `throw new BadRequestException("...")` |
| новое поле брони | миграция `V5__...sql` (`ALTER TABLE bookings ADD COLUMN ...`), поле в `Booking`, в `BookingRequest`/`BookingResponse`, `BookingMapper`, `apply(...)` |
| новый показатель аналитики | метод `@Query` в `BookingRepository` + поле в `AnalyticsResponse` + `AnalyticsService` |
| новый эндпоинт | метод в контроллере с `@GetMapping` и `@PreAuthorize`, логика — в сервисе |
| поменять правило риска | `CancellationRiskCalculator` + `CancellationRiskCalculatorTest` |

После изменения кода: `mvn test` в `backend/`, затем `start-compose.ps1` (пересоберёт образ).
