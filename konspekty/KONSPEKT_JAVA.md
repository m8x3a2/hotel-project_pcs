# Конспект по Java на примере проекта «Гостиница: бронирование номеров»

Каждая тема: коротко теория → где это в нашем коде → что могут спросить на защите.

---

## Содержание

1. [Архитектура и пакеты](#1-архитектура-и-пакеты)
2. [Классы, объекты, конструкторы](#2-классы-объекты-конструкторы)
3. [Четыре принципа ООП](#3-четыре-принципа-ооп)
4. [equals, hashCode, toString](#4-equals-hashcode-tostring)
5. [Интерфейсы и обобщения (generics)](#5-интерфейсы-и-обобщения-generics)
6. [Перечисления (enum)](#6-перечисления-enum)
7. [Исключения](#7-исключения)
8. [Коллекции и Optional](#8-коллекции-и-optional)
9. [Лямбды, ссылки на методы, функциональные интерфейсы](#9-лямбды-ссылки-на-методы-функциональные-интерфейсы)
10. [Stream API](#10-stream-api)
11. [Даты: java.time](#11-даты-javatime)
12. [Строки и регулярные выражения (валидация)](#12-строки-и-регулярные-выражения-валидация)
13. [static, final, утилитные классы](#13-static-final-утилитные-классы)
14. [Современный синтаксис: switch-выражения, var](#14-современный-синтаксис-switch-выражения-var)
15. [Консольный ввод, Scanner, кодировки](#15-консольный-ввод-scanner-кодировки)
16. [JDBC — работа с PostgreSQL](#16-jdbc--работа-с-postgresql)
17. [База данных: ключи и ограничения](#17-база-данных-ключи-и-ограничения)
18. [Maven](#18-maven)
19. [Где проверять данные: многоуровневая валидация](#19-где-проверять-данные-многоуровневая-валидация)
20. [Вопросы на защите — короткие ответы](#20-вопросы-на-защите--короткие-ответы)

---

## 1. Архитектура и пакеты

Программа разделена на **слои**. Каждый слой отвечает за своё и обращается только к слою ниже:

```
Main (консольное меню, ввод/вывод)
   ↓
service (бизнес-правила: UserService, BookingService)
   ↓
repository (SQL-запросы: UserRepository, BookingRepository)
   ↓
DatabaseManager → JDBC → PostgreSQL
```

| Пакет | Что лежит | Пример |
|---|---|---|
| `hotel.model` | данные предметной области (сущности) | `User`, `Booking`, `RoomType`, `BookingStatus` |
| `hotel.repository` | доступ к БД, только SQL | `UserRepository.save()` |
| `hotel.service` | бизнес-логика и правила | «номер нельзя забронировать на занятые даты» |
| `hotel.exception` | собственные исключения | `BusinessException` |
| `hotel.util` | вспомогательные классы | `DatabaseManager`, `ExcelExporter`, `Validator` |

**Зачем так делать:**
- SQL есть только в репозиториях: если поменять БД, `Main` и сервисы менять не придётся;
- бизнес-правила есть только в сервисах: их нельзя обойти, даже если добавить второй интерфейс (например, веб);
- код проще читать и тестировать по частям.

**Пакет** (`package hotel.model;`) — это пространство имён и папка на диске. Классы из другого пакета подключаются через `import`.

---

## 2. Классы, объекты, конструкторы

**Класс** — шаблон (описание), **объект** — конкретный экземпляр, созданный через `new`.

```java
public class User {                      // класс
    private int id;                      // поля (состояние объекта)
    private String fullName;
    ...
}

User user = new User("Иванов Иван", "+7 900 111-11-11", "ivanov@mail.ru", "4500111111"); // объект
```

**Конструктор** — специальный метод, который вызывается при `new`. Имя совпадает с именем класса, возвращаемого типа нет.

**Перегрузка конструкторов (overloading)**: в `User` и `Booking` по **два** конструктора с разными параметрами:

```java
// новый гость — id ещё нет, его выдаст база данных (SERIAL)
public User(String fullName, String phone, String email, String passportNumber)

// гость, прочитанный из БД — id уже известен
public User(int id, String fullName, String phone, String email, String passportNumber)
```

Java выбирает нужный конструктор по количеству и типам аргументов.

**`this`** — ссылка на текущий объект. `this.fullName = fullName;` отличает поле объекта от параметра с тем же именем.

**Значения по умолчанию у полей:** `int` → `0`, `double` → `0.0`, `boolean` → `false`, ссылки → `null`. Поэтому у нового `User` до сохранения `id == 0`.

---

## 3. Четыре принципа ООП

### Инкапсуляция
Поля скрыты (`private`), доступ через методы (`getX`/`setX`). Так объект сам контролирует своё состояние.

```java
private String email;
public String getEmail() { return email; }
public void setEmail(String email) { this.email = email; }
```

Модификаторы доступа:

| Модификатор | Кому виден |
|---|---|
| `private` | только внутри класса |
| (без модификатора) | внутри пакета |
| `protected` | пакет + наследники |
| `public` | всем |

### Наследование
Класс получает поля и методы родителя через `extends`.

```java
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);      // вызов конструктора родителя
    }
}
```

`super(...)` — вызов конструктора родительского класса. В Java у класса **один** родитель, но реализовать можно **много** интерфейсов.

### Полиморфизм
Один интерфейс, разные реализации. `UserRepository` и `BookingRepository` оба реализуют `CrudRepository<T>`, у каждого своя версия `save`, `findAll` и т.д.

```java
CrudRepository<User> repo = new UserRepository();
repo.findAll();   // вызовется реализация из UserRepository
```

`@Override` — аннотация «этот метод переопределяет метод родителя/интерфейса». Если ошибиться в сигнатуре, компилятор выдаст ошибку.

Два вида полиморфизма:
- **перегрузка** (overloading) — одно имя, разные параметры, выбирается при компиляции (конструкторы `User`);
- **переопределение** (overriding) — тот же метод в наследнике, выбирается во время выполнения (`toString`, `save`).

### Абстракция
Показываем, **что** объект умеет, и прячем, **как**. Интерфейс `CrudRepository` описывает операции (`save`, `findById`...) без единой строки SQL.

---

## 4. equals, hashCode, toString

Все классы неявно наследуются от `Object`, и у них есть эти методы. Мы их переопределяем.

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;                // тот же самый объект
    if (!(o instanceof User)) return false;    // другой тип
    User user = (User) o;                      // приведение типа
    return id == user.id;                      // сравниваем по id
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

- `==` для объектов сравнивает **ссылки** (тот же ли это объект в памяти), `equals` — **содержимое** по нашему правилу.
- **Контракт:** если `a.equals(b)`, то обязательно `a.hashCode() == b.hashCode()`. Иначе `HashMap` и `HashSet` будут работать неправильно.
- Строки сравниваем через `equals`, а не через `==`! В `UserService` так и сделано: `existing.getEmail().equalsIgnoreCase(email)`.

`toString()` вызывается автоматически при `System.out.println(user)` и при склейке строк `"..." + user`.

---

## 5. Интерфейсы и обобщения (generics)

**Интерфейс** — набор методов без реализации (контракт). Класс обязуется реализовать все методы через `implements`.

```java
public interface CrudRepository<T> {
    T save(T entity) throws SQLException;
    List<T> findAll() throws SQLException;
    Optional<T> findById(int id) throws SQLException;
    void update(T entity) throws SQLException;
    void delete(int id) throws SQLException;
}

public class UserRepository implements CrudRepository<User> { ... }
public class BookingRepository implements CrudRepository<Booking> { ... }
```

**Generics** (`<T>`) — параметр-тип. Один интерфейс подходит для любой сущности, а компилятор проверяет типы: `UserRepository.findAll()` вернёт именно `List<User>`, приводить типы вручную не нужно.

- `List<String>` — список строк; в него нельзя положить `Integer` (ошибка компиляции).
- В generics нельзя использовать примитивы: `List<int>` ❌ → `List<Integer>` ✅ (обёртки, автоупаковка).

**Интерфейс vs абстрактный класс:** у интерфейса нет состояния (полей объекта), класс может реализовать несколько интерфейсов; абстрактный класс может хранить поля и общий код, но наследовать можно только один класс.

**CRUD** = Create, Read, Update, Delete — четыре базовые операции с данными.

---

## 6. Перечисления (enum)

`enum` — тип с фиксированным набором значений. Опечатка вроде `"CONFIRMD"` невозможна: компилятор не пропустит.

```java
public enum BookingStatus {
    CREATED, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED
}
```

У enum могут быть **поля, конструктор и методы**:

```java
public enum RoomType {
    STANDARD(2000.0), DELUXE(3500.0), SUITE(5000.0), FAMILY(4500.0);

    private final double pricePerNight;
    RoomType(double pricePerNight) { this.pricePerNight = pricePerNight; }  // конструктор всегда private
    public double getPricePerNight() { return pricePerNight; }
}
```

Полезные методы (все есть в проекте):

| Метод | Что делает | Где в коде |
|---|---|---|
| `RoomType.values()` | массив всех значений | меню выбора типа номера в `Main.readRoomType()` |
| `RoomType.valueOf("SUITE")` | строка → enum | чтение из БД в `BookingRepository.mapRow()` |
| `roomType.name()` | enum → строка | запись в БД |
| `==` | сравнение enum (можно, значения — единственные объекты) | `b.getStatus() == status` |

**`EnumSet`** — очень быстрый `Set` для enum. Им описаны разрешённые переходы статусов:

```java
private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
        BookingStatus.CREATED,   EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
        BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
        BookingStatus.CHECKED_IN, EnumSet.of(BookingStatus.CHECKED_OUT),
        BookingStatus.CHECKED_OUT, EnumSet.noneOf(BookingStatus.class),
        BookingStatus.CANCELLED,  EnumSet.noneOf(BookingStatus.class)
);
```

Это **конечный автомат**: правила записаны данными, а не цепочкой `if`. Проверка сводится к одной строке `allowed.contains(newStatus)`.

---

## 7. Исключения

**Исключение** — объект, описывающий ошибку. Его «бросают» (`throw`) и «ловят» (`catch`).

```
Throwable
├── Error                      — сбои JVM (OutOfMemoryError), не ловим
└── Exception
    ├── SQLException, IOException ...     — ПРОВЕРЯЕМЫЕ (checked)
    └── RuntimeException                  — НЕПРОВЕРЯЕМЫЕ (unchecked)
        ├── NumberFormatException, NullPointerException ...
        ├── BusinessException            ← наше
        └── EntityNotFoundException      ← наше
```

| | Checked | Unchecked |
|---|---|---|
| Родитель | `Exception` | `RuntimeException` |
| Компилятор требует обработать? | да: `try/catch` или `throws` в сигнатуре | нет |
| Пример в проекте | `SQLException` — отсюда `throws SQLException` у методов | `BusinessException` |

Наши исключения наследуются от `RuntimeException`, чтобы не писать `throws BusinessException` в каждом методе.

**Бросить:**
```java
throw new BusinessException("Дата выезда должна быть позже даты заезда.");
```

**Поймать несколько типов сразу (multi-catch):**
```java
} catch (EntityNotFoundException | BusinessException e) {
    System.out.println("Ошибка: " + e.getMessage());
} catch (SQLException e) {
    System.out.println("Ошибка базы данных: " + e.getMessage());
}
```
Благодаря этому в `Main.mainMenu()` программа **не падает** при ошибке, а показывает сообщение и возвращается в меню.

**try-with-resources** — ресурсы, открытые в скобках `try (...)`, закрываются автоматически (даже при исключении). Работает для всех классов, реализующих `AutoCloseable`:
```java
try (Connection connection = DatabaseManager.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {
    ...
}   // здесь statement и connection закрыты (в обратном порядке)
```
Без этого незакрытые соединения быстро исчерпали бы лимит подключений PostgreSQL.

**Перевод технической ошибки в понятную** (`UserService.deleteUser`): по коду `SQLState` определяем, что именно случилось.
```java
} catch (SQLException e) {
    if ("23503".equals(e.getSQLState())) {       // нарушение внешнего ключа
        throw new BusinessException("Нельзя удалить гостя: у него есть бронирования. ...");
    }
    throw e;                                      // остальные ошибки пробрасываем дальше
}
```
Коды PostgreSQL: `23503` — foreign key violation, `23505` — unique violation.

---

## 8. Коллекции и Optional

| Интерфейс | Реализация в проекте | Особенность |
|---|---|---|
| `List<E>` | `ArrayList` | упорядоченный, допускает дубликаты, доступ по индексу |
| `Set<E>` | `EnumSet` | без дубликатов |
| `Map<K,V>` | `LinkedHashMap`, `Map.of(...)` | пары ключ → значение |

- `new ArrayList<>()` — «ромб» `<>`: тип выводится из объявления.
- `LinkedHashMap` в `getStatistics()` **сохраняет порядок вставки**, поэтому статистика выводится в том порядке, в каком мы её добавили. У `HashMap` порядок не гарантирован.
- `Map.of(...)`, `List.of(...)` — **неизменяемые** коллекции: `put`/`add` бросят `UnsupportedOperationException`.
- Обход `Map`: `for (Map.Entry<String, String> entry : stats.entrySet())`.

**`Optional<T>`** — контейнер, в котором значение либо есть, либо нет. Избавляет от возврата `null` и `NullPointerException`.
```java
// репозиторий: гостя может не быть
public Optional<User> findById(int id) { ... return Optional.empty(); }

// сервис: если нет — бросаем понятное исключение
return userRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Гость с ID " + id + " не найден."));

// проверка существования
if (userRepository.findById(userId).isEmpty()) { ... }
```

---

## 9. Лямбды, ссылки на методы, функциональные интерфейсы

**Функциональный интерфейс** — интерфейс с **одним** абстрактным методом. Вместо класса-реализации можно передать **лямбду** или **ссылку на метод**.

```java
b -> b.getRoomNumber() == roomNumber          // лямбда: параметр -> выражение
() -> new EntityNotFoundException("...")      // лямбда без параметров
User::getId                                   // ссылка на метод = u -> u.getId()
System.out::println                           // ссылка на метод = x -> System.out.println(x)
Validator::validateEmail                      // = s -> Validator.validateEmail(s)
```

Стандартные функциональные интерфейсы (`java.util.function`):

| Интерфейс | Метод | Смысл | Где у нас |
|---|---|---|---|
| `Predicate<T>` | `boolean test(T)` | условие | внутри `filter(...)` |
| `Function<T,R>` | `R apply(T)` | преобразование | `Collectors.toMap(User::getId, u -> u)` |
| `UnaryOperator<T>` | `T apply(T)` | T → T | `Main.readValidString(prompt, Validator::validateEmail)` |
| `Consumer<T>` | `void accept(T)` | «сделать что-то» | `Main.readValidDate(prompt, date -> ...)`, `forEach` |
| `IntConsumer` | `void accept(int)` | то же для `int` | `Main.readValidInt(...)` |
| `Supplier<T>` | `T get()` | «дай значение» | `orElseThrow(() -> new ...)` |
| `Comparator<T>` | `int compare(T,T)` | сравнение | сортировка |

**Пример из проекта — один метод ввода для всех полей:**
```java
private static String readValidString(String prompt, UnaryOperator<String> validator) {
    while (true) {
        String input = readNonEmptyString(prompt);
        try {
            return validator.apply(input);          // проверка + нормализация
        } catch (BusinessException e) {
            System.out.println("Ошибка: " + e.getMessage());   // и снова спрашиваем
        }
    }
}

String email = readValidString("Введите email: ", Validator::validateEmail);
String phone = readValidString("Введите телефон: ", Validator::validatePhone);
```
Поведение (какую проверку делать) передаётся как **параметр**. Это функциональный стиль.

Лямбда может использовать внешние переменные, только если они **effectively final** (не меняются после присваивания). Поэтому в `createBooking` работает `date -> Validator.validateStayDates(checkIn, date)`: `checkIn` больше не меняется.

---

## 10. Stream API

**Stream** — конвейер обработки коллекции: источник → промежуточные операции → терминальная операция.
Поток **ленивый** (ничего не выполняется до терминальной операции) и **одноразовый**.

```java
return bookingRepository.findAll().stream()          // источник
        .filter(b -> b.getStatus() == status)        // промежуточная
        .collect(Collectors.toList());               // терминальная
```

Операции, которые используются в `BookingService`:

| Операция | Тип | Что делает | Метод проекта |
|---|---|---|---|
| `filter(pred)` | промежуточная | оставляет подходящие | поиск, фильтры |
| `sorted(comparator)` | промежуточная | сортирует | `sortByCheckInDate` |
| `mapToDouble(f)` | промежуточная | в `DoubleStream` | выручка |
| `collect(Collectors.toList())` | терминальная | собрать в список | почти везде |
| `collect(Collectors.toMap(k, v))` | терминальная | собрать в `Map` | `searchByGuestName` |
| `count()` | терминальная | количество | статистика |
| `sum()`, `average()` | терминальная | сумма, среднее (`OptionalDouble`) | статистика |
| `anyMatch(pred)` | терминальная | есть ли хоть один | `checkRoomAvailability` |
| `forEach(action)` | терминальная | действие для каждого | `printUsers` |

**Сортировка через Comparator:**
```java
.sorted(Comparator.comparing(Booking::getCheckInDate))                 // по возрастанию даты
.sorted(Comparator.comparingDouble(Booking::getTotalPrice).reversed()) // по убыванию цены
```

**Проверка пересечения дат** (главное бизнес-правило):
```java
boolean occupied = bookingRepository.findAll().stream()
        .filter(b -> b.getRoomNumber() == roomNumber)
        .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
        .filter(b -> excludeBookingId == null || b.getId() != excludeBookingId)
        .anyMatch(b -> checkIn.isBefore(b.getCheckOutDate()) && checkOut.isAfter(b.getCheckInDate()));
```
Два интервала `[A1, A2)` и `[B1, B2)` пересекаются, если `A1 < B2` и `A2 > B1`. Поэтому если один гость выезжает 10-го, другой может заехать 10-го: пересечения нет.

> Замечание для защиты: фильтровать в Java после `findAll()` удобно для учебного проекта, но на больших данных фильтрацию лучше делать в SQL (`WHERE room_number = ?`), чтобы не тянуть всю таблицу в память.

---

## 11. Даты: java.time

| Класс | Хранит | Пример |
|---|---|---|
| `LocalDate` | дату без времени | `2026-10-01` — даты заезда и выезда |
| `LocalDateTime` | дату и время | `createdAt` |
| `ChronoUnit.DAYS.between(a, b)` | разница в днях | количество ночей |

```java
LocalDate.parse("2026-10-01");       // строка ISO → дата; при ошибке DateTimeParseException
LocalDate.now();                     // сегодня
checkOut.isAfter(checkIn);           // сравнение
today.plusDays(365);                 // арифметика (возвращает НОВЫЙ объект)
```

Объекты `java.time` **неизменяемые** (immutable): `plusDays` не меняет дату, а возвращает новую.
`LocalDate.parse("2026-02-30")` бросит исключение: несуществующие даты отсекаются автоматически.

Стоимость: `nights * roomType.getPricePerNight()`.

---

## 12. Строки и регулярные выражения (валидация)

**Строки неизменяемы**: `trim()`, `toLowerCase()`, `replaceAll()` возвращают **новую** строку.

| Метод | Что делает |
|---|---|
| `trim()` | убирает пробелы по краям |
| `isBlank()` | пустая или только пробелы (Java 11+) |
| `toLowerCase()` | в нижний регистр |
| `equalsIgnoreCase(s)` | сравнение без учёта регистра |
| `contains(s)`, `startsWith(s)` | поиск подстроки |
| `replaceAll(regex, repl)` | замена по регулярному выражению |
| `substring(a, b)` | подстрока `[a, b)` |
| `String.format("%.2f", x)` | форматирование числа (2 знака после запятой) |

**Регулярное выражение (regex)** — шаблон для проверки строки. В Java: `Pattern` (скомпилированный шаблон) и `Matcher` (результат сопоставления).

```java
private static final Pattern PHONE_PATTERN = Pattern.compile("^(?:\\+7|8)(\\d{10})$");

Matcher m = PHONE_PATTERN.matcher("89161112233");
if (m.matches()) {
    String digits = m.group(1);   // "9161112233" — содержимое 1-й группы (...)
}
```

`Pattern` компилируется **один раз** в `static final` поле: так быстрее, чем `String.matches()` при каждом вызове.

Шпаргалка по синтаксису:

| Символ | Значение |
|---|---|
| `^` / `$` | начало / конец строки |
| `\d` | цифра (в Java-строке пишется `\\d`) |
| `\s` | пробельный символ |
| `[A-Za-zА-Яа-яЁё]` | один символ из набора |
| `+` / `*` / `?` | 1 и более / 0 и более / 0 или 1 |
| `{10}` / `{1,3}` | ровно 10 / от 1 до 3 раз |
| `(...)` | группа с запоминанием (`group(1)`) |
| `(?:...)` | группа без запоминания |
| `a\|b` | a или b |
| `\\+`, `\\.` | экранирование спецсимвола |

**Правила из `Validator.java`:**

| Поле | Regex / правило | Нормализация |
|---|---|---|
| ФИО | 2–4 слова из букв, внутри слова `-` или `'` | лишние пробелы → один |
| Телефон | `^(?:\+7\|8)(\d{10})$` после удаления пробелов, скобок и дефисов | → `+7 916 111-22-33` |
| Email | `^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$` + запрет `..` | → нижний регистр |
| Паспорт | `^\d{10}$` после удаления пробелов, не из одних нулей | → 10 цифр подряд |
| Номер комнаты | 101–999, не кратен 100 | — |

**Зачем нормализовать?** `IVANOV@mail.ru` и `ivanov@mail.ru` — один и тот же адрес. Если хранить как ввели, проверка уникальности пропустит дубликат. Телефон в едином формате удобно искать и выводить.

---

## 13. static, final, утилитные классы

- **`static`** — принадлежит **классу**, а не объекту. Вызывается без `new`: `DatabaseManager.getConnection()`, `Validator.validateEmail(...)`.
- **`final` у поля** — значение присваивается один раз. `private final UserRepository userRepository;` задаётся в конструкторе и больше не меняется.
- **`static final`** — константа: `private static final String URL = "jdbc:postgresql://..."`. Имена констант пишут `UPPER_SNAKE_CASE`.
- **`final` у класса** — от него нельзя наследоваться (`public final class Validator`).
- **Утилитный класс** — только статические методы; конструктор `private`, чтобы нельзя было создать объект:
  ```java
  public final class Validator {
      private Validator() { }
      public static String validateEmail(String email) { ... }
  }
  ```
- **Статический блок инициализации** `static { ... }` выполняется один раз при первой загрузке класса. В `ExcelExporter` он настраивает логгер Apache POI.

**Внедрение зависимостей через конструктор:** сервис не создаёт репозиторий сам, а получает его снаружи:
```java
public BookingService(BookingRepository bookingRepository, UserRepository userRepository) { ... }
```
Классы слабо связаны: для тестов можно подставить другой репозиторий.

---

## 14. Современный синтаксис: switch-выражения, var

**switch со стрелками** (Java 14+): нет «проваливания» (`break` не нужен), в одной ветке можно перечислить несколько значений.
```java
switch (choice) {
    case "1" -> guestsMenu();
    case "2" -> bookingsMenu();
    case "0" -> running = false;
    default  -> System.out.println("Неизвестный пункт меню.");
}
```

**`var`** (Java 10+): тип локальной переменной выводится компилятором. В `Validator`: `var matcher = PHONE_PATTERN.matcher(compact);` (тип — `Matcher`). Типизация остаётся строгой, просто короче запись.

**Тернарный оператор:** `условие ? a : b`, например `bookings.isEmpty() ? 0.0 : ...`.

---

## 15. Консольный ввод, Scanner, кодировки

```java
Scanner scanner = new Scanner(System.in, consoleCharset());
String line = scanner.nextLine().trim();
int n = Integer.parseInt(line);          // NumberFormatException, если не число
```

Надёжный ввод = **цикл + try/catch**: пока пользователь не введёт корректное значение, спрашиваем снова (`readInt`, `readDate`, `readValidString`).

Мы всегда читаем строку через `nextLine()` и сами переводим её в число. Если смешивать `nextInt()` и `nextLine()`, после `nextInt()` в буфере остаётся перевод строки, и следующий `nextLine()` вернёт пустую строку.

**Кодировки.** Консоль Windows часто работает в cp866 или cp1251, а Java 18+ по умолчанию считает всё UTF-8. Если читать ввод не в той кодировке, русские буквы превращаются в «кракозябры». Поэтому `Scanner` создаётся с кодировкой консоли (свойство `stdin.encoding`).

Если поток ввода закрыт (Ctrl+Z в Windows, Ctrl+D в Linux), `nextLine()` бросает `NoSuchElementException`. В `main` она ловится, и программа корректно завершается.

---

## 16. JDBC — работа с PostgreSQL

**JDBC** (Java Database Connectivity) — стандартный API для работы с реляционными БД. Конкретную СУБД подключает **драйвер** (зависимость `org.postgresql:postgresql` в `pom.xml`).

Основные объекты:

| Объект | Роль |
|---|---|
| `DriverManager.getConnection(url, user, pass)` | открывает соединение |
| `Connection` | соединение с БД |
| `PreparedStatement` | SQL-запрос с параметрами `?` |
| `ResultSet` | результат `SELECT`, читается построчно курсором |

**Чтение:**
```java
String sql = "SELECT * FROM users WHERE id = ?";
try (Connection c = DatabaseManager.getConnection();
     PreparedStatement st = c.prepareStatement(sql)) {
    st.setInt(1, id);                                  // параметры нумеруются с 1!
    try (ResultSet rs = st.executeQuery()) {
        if (rs.next()) {                               // курсор на следующую строку
            return Optional.of(mapRow(rs));            // rs.getString("full_name") ...
        }
    }
}
```

**Запись и получение сгенерированного id:**
```java
PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
st.executeUpdate();                                    // INSERT / UPDATE / DELETE
try (ResultSet keys = st.getGeneratedKeys()) {
    if (keys.next()) user.setId(keys.getInt(1));       // id, выданный SERIAL
}
```

- `executeQuery()` — для `SELECT` (возвращает `ResultSet`);
- `executeUpdate()` — для `INSERT/UPDATE/DELETE` (возвращает число изменённых строк).

**Почему `PreparedStatement`, а не склейка строк — защита от SQL-инъекций:**
```java
// ОПАСНО: если ввести  ' OR '1'='1  — запрос вернёт всех
"SELECT * FROM users WHERE email = '" + email + "'"

// БЕЗОПАСНО: значение передаётся отдельно от текста запроса и никогда не выполняется как SQL
"SELECT * FROM users WHERE email = ?"   +   st.setString(1, email)
```

**Преобразование типов Java ↔ SQL:**

| Java | SQL | Как |
|---|---|---|
| `LocalDate` | `DATE` | `Date.valueOf(localDate)` / `rs.getDate(...).toLocalDate()` |
| `LocalDateTime` | `TIMESTAMP` | `rs.getTimestamp(...).toLocalDateTime()` |
| `enum` | `VARCHAR` | `status.name()` / `BookingStatus.valueOf(str)` |
| `double` | `NUMERIC(10,2)` | `setDouble` / `getDouble` |

`mapRow(ResultSet)` — метод-«маппер»: превращает строку таблицы в Java-объект (идея ORM, сделанная вручную).

> Замечание для защиты: деньги правильнее хранить в `BigDecimal`, а не в `double`: у `double` бывают ошибки округления (`0.1 + 0.2 = 0.30000000000000004`). В проекте цены целые, поэтому проблем нет, но об этом стоит знать.

---

## 17. База данных: ключи и ограничения

```sql
CREATE TABLE users (
    id              SERIAL PRIMARY KEY,               -- автоинкремент + первичный ключ
    email           VARCHAR(150) NOT NULL UNIQUE,     -- обязательное и уникальное
    ...
);
CREATE TABLE bookings (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id),     -- ВНЕШНИЙ КЛЮЧ
    room_number INTEGER NOT NULL CHECK (room_number > 0),  -- проверка значения
    ...
    CHECK (check_out_date > check_in_date)                 -- проверка на уровне строки
);
```

| Ограничение | Смысл |
|---|---|
| `PRIMARY KEY` | уникальный идентификатор строки |
| `FOREIGN KEY` / `REFERENCES` | ссылка на другую таблицу; нельзя сослаться на несуществующего гостя и нельзя удалить гостя, на которого ссылаются брони |
| `UNIQUE` | значения не повторяются |
| `NOT NULL` | поле обязательно |
| `CHECK` | произвольное условие |

Связь **один-ко-многим**: у одного гостя много бронирований (`bookings.user_id → users.id`).

---

## 18. Maven

**Maven** — сборщик и менеджер зависимостей. Всё описано в `pom.xml`.

- **Координаты** проекта и библиотек: `groupId : artifactId : version`.
- **`<dependencies>`**: Maven сам скачивает библиотеки (драйвер PostgreSQL, Apache POI) из центрального репозитория в `~/.m2`.
- **`maven.compiler.source/target = 17`**: для какой версии Java компилировать.
- **`maven-shade-plugin`**: собирает «толстый» jar со всеми зависимостями внутри и указывает `Main-Class` в манифесте, поэтому работает `java -jar target/hotel-booking-system.jar`.

Жизненный цикл: `clean` → `compile` → `test` → `package` → `install`.
`mvn clean package` = удалить `target/`, скомпилировать, упаковать jar.

**JDK vs JRE vs JVM:** JVM исполняет байт-код (`.class`), JRE = JVM + стандартная библиотека, JDK = JRE + компилятор `javac` и инструменты. Java «пишется один раз, работает везде», потому что компилируется в байт-код для JVM, а не в машинный код.

---

## 19. Где проверять данные: многоуровневая валидация

В проекте проверка идёт на **трёх уровнях**:

| Уровень | Где | Зачем |
|---|---|---|
| 1. Интерфейс | `Main.readValidString / readValidInt / readValidDate` | удобство: ошибка видна сразу, переспрашивается только одно поле |
| 2. Бизнес-логика | `UserService`, `BookingService` (через `Validator`) | **обязательная** проверка: её не обойти ни из какого интерфейса |
| 3. База данных | `UNIQUE`, `CHECK`, `FOREIGN KEY`, `NOT NULL` | последний рубеж: защищает, даже если данные пришли в обход Java |

Уровни 1 и 2 используют **один и тот же** класс `Validator`, поэтому правила описаны в одном месте (принцип **DRY** — Don't Repeat Yourself).

Если два пользователя одновременно регистрируют одинаковый email, проверка в Java может пропустить обоих, но сработает `UNIQUE` в БД. Код `23505` переводится в понятное сообщение.

---

## 20. Вопросы на защите — короткие ответы

**Почему поля `private`?** Инкапсуляция: объект сам контролирует своё состояние, изменить его можно только через методы.

**Зачем два конструктора в `User`?** Для нового гостя `id` ещё нет (его выдаст БД), а при чтении из БД он уже известен. Это перегрузка.

**Чем интерфейс отличается от абстрактного класса?** У интерфейса нет состояния, и класс может реализовать несколько интерфейсов; наследовать можно только один класс.

**Почему наши исключения наследуются от `RuntimeException`?** Они непроверяемые, и их не нужно объявлять в `throws` каждого метода. Это ошибки бизнес-логики, их ловит меню.

**Что такое try-with-resources?** Конструкция, которая автоматически закрывает `Connection`, `PreparedStatement`, `ResultSet` (всё, что реализует `AutoCloseable`).

**Как защититься от SQL-инъекций?** `PreparedStatement` с параметрами `?`: данные передаются отдельно от SQL.

**Что делает `Optional`?** Явно показывает, что значения может не быть, и заставляет обработать этот случай (`orElseThrow`, `isEmpty`).

**Что такое Stream API?** Декларативная обработка коллекций конвейером `filter → sorted → collect`. Ленивый, одноразовый.

**`==` или `equals`?** `==` сравнивает ссылки (для примитивов — значения), `equals` сравнивает содержимое. Строки — только `equals`. Enum можно через `==`.

**Контракт `equals`/`hashCode`?** Равные объекты обязаны иметь одинаковый `hashCode`, иначе сломаются `HashMap`/`HashSet`.

**Почему enum, а не строки для статуса?** Компилятор проверяет значения, опечатки невозможны, у enum есть свои поля (цена за ночь) и методы.

**Как проверяется, что номер свободен?** Stream по бронированиям этой комнаты, кроме отменённых; пересечение, если `новыйЗаезд < старыйВыезд && новыйВыезд > старыйЗаезд`.

**Как устроены переходы статусов?** `Map<BookingStatus, Set<BookingStatus>>` разрешённых переходов (конечный автомат). Новый статус проверяется через `contains`.

**Как проверяется email/телефон?** Регулярными выражениями в `Validator`. Значения ещё и нормализуются: email в нижний регистр, телефон в формат `+7 XXX XXX-XX-XX`, паспорт в 10 цифр.

**Почему проверки и в `Main`, и в сервисе?** В `Main` для удобства (переспросить поле), в сервисе для надёжности (правило нельзя обойти). Код общий, в `Validator`.

**Что будет, если удалить гостя с бронированиями?** БД не даст (внешний ключ, код `23503`), сервис превратит это в понятное сообщение.

**Зачем Maven shade plugin?** Чтобы собрать один jar со всеми библиотеками и запускать его через `java -jar`.

**Почему `double` для денег — не лучший выбор?** Двоичная дробь не представляет точно `0.1`, копейки могут «поплыть». Правильно использовать `BigDecimal`.
