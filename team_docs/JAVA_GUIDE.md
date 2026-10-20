# Java — кратко, всё, что есть в нашем проекте

> Для тех, кто умеет программировать (C++/C#/Python), но не знает Java.
> Каждый раздел: что это + пример из нашего кода + где посмотреть.
> Внутренний файл команды для подготовки к защите.

---

## 1. Как устроена Java-программа

- Весь код — внутри **классов**. Один публичный класс = один файл с таким же именем (`Guest` → `Guest.java`).
- **Пакет** (`package ru.mirea.hotel.model;`) = папка `ru/mirea/hotel/model/`. Нужен, чтобы группировать классы.
- **import** подключает класс из другого пакета: `import java.util.List;`. Классы из `java.lang` (String, Integer, System) доступны без import.
- Точка входа — метод `public static void main(String[] args)` (у нас в `Main.java`).
- Компиляция: `.java` → компилятор `javac` → байткод `.class` → выполняет **JVM** (виртуальная машина Java).
  Поэтому одна программа работает на Windows/Linux/Mac без пересборки.
- **Maven** (`pom.xml`) — система сборки: сам скачивает библиотеки и компилирует проект (как pip/npm/NuGet).

```java
package ru.mirea.hotel;          // в каком пакете файл

import java.util.List;           // что используем из других пакетов

public class Main {              // класс
    public static void main(String[] args) {   // отсюда стартует программа
        System.out.println("Привет");          // печать строки с переводом строки
    }
}
```

## 2. Типы данных

| Тип | Что хранит | Пример у нас |
|---|---|---|
| `int` | целое (32 бита) | `id`, `capacity`, `roomNumber` |
| `long` | большое целое | `getNights()` |
| `double` | дробное | `pricePerNight`, `totalPrice` |
| `boolean` | true/false | `canChangeTo()` возвращает boolean |
| `String` | строка (объект!) | `fullName`, `email` |
| `LocalDate` | дата без времени | `checkIn`, `checkOut` |

- `int`, `double`, `boolean`, `long` — **примитивы** (хранят значение). Всё остальное — **ссылочные типы** (объекты).
- У примитивов есть классы-обёртки: `Integer`, `Double`, `Long`. Они нужны, например, в коллекциях: `Map<BookingStatus, Long>`.
- Сравнение строк — **только** `a.equals(b)`, а не `a == b` (`==` сравнивает адреса объектов, а не текст).
  У нас: `answer.equals("да")` в `ConsoleInput.confirm`.
- `1_000_000` — подчёркивания в числе для читаемости (`RoomService.validate`).

## 3. Модификаторы

| Слово | Значение |
|---|---|
| `public` | доступно всем |
| `private` | только внутри этого класса |
| `protected` | внутри класса, наследникам и в своём пакете (`BaseMenu.input`) |
| (ничего) | внутри своего пакета |
| `static` | принадлежит классу, а не объекту: `TablePrinter.printGuests(...)` вызывается без `new` |
| `final` | нельзя изменить: `private final GuestRepository guestRepository;` — присваивается один раз в конструкторе |
| `static final` | константа: `private static final int MAX_NIGHTS = 30;` |

## 4. Классы, объекты, конструкторы

```java
public class Guest {
    private int id;               // поля (состояние объекта)
    private String fullName;

    public Guest(String fullName, String phone, String email) {  // конструктор - имя как у класса, без типа
        this(0, fullName, phone, email);                         // вызов другого конструктора этого класса
    }

    public Guest(int id, String fullName, String phone, String email) {
        this.id = id;             // this.id - поле объекта, id - параметр
        this.fullName = fullName;
        ...
    }

    public String getFullName() { return fullName; }             // геттер
    public void setFullName(String fullName) { this.fullName = fullName; }  // сеттер
}

Guest guest = new Guest("Иванов Иван", "+7 900 111-11-11", "ivanov@mail.ru");  // создание объекта
guest.getFullName();
```
- Несколько конструкторов с разными параметрами — **перегрузка**.
- `this` — ссылка на текущий объект.
- Где: `model/Guest.java`, `Room.java`, `Booking.java`.

## 5. Инкапсуляция

Поля `private` + публичные методы доступа. Снаружи нельзя написать `guest.email = "x"` — только `guest.setEmail("x")`.
Зачем: класс сам контролирует свои данные, внутреннее устройство можно менять, не ломая остальной код.

## 6. Наследование и абстрактные классы

```java
public abstract class BaseMenu {                     // abstract - нельзя создать new BaseMenu()
    protected final ConsoleInput input;

    protected BaseMenu(ConsoleInput input) { this.input = input; }

    protected abstract String getTitle();            // метод без тела - наследник ОБЯЗАН реализовать

    public void run() {                              // общий готовый метод для всех меню
        System.out.println(getTitle());              // вызовет версию конкретного наследника
        ...
    }
}

public class GuestMenu extends BaseMenu {            // extends - наследование (только от ОДНОГО класса)
    public GuestMenu(ConsoleInput input, ...) {
        super(input);                                // вызов конструктора родителя (обязательно первой строкой)
    }

    @Override                                        // "переопределяю метод родителя" - проверка компилятором
    protected String getTitle() { return "ГОСТИ"; }
}
```
- Все классы неявно наследуются от `Object` — отсюда методы `toString()`, `equals()`, `hashCode()`.
- Где: `ui/BaseMenu.java` и 4 наследника; `exception/*` наследуют `RuntimeException`.

## 7. Интерфейсы

```java
public interface CrudRepository<T> {        // только объявления методов
    T save(T entity) throws SQLException;
    List<T> findAll() throws SQLException;
    ...
}

public class GuestRepository implements CrudRepository<Guest> {   // implements - реализует интерфейс
    @Override
    public Guest save(Guest guest) throws SQLException { ... }   // обязан реализовать ВСЕ методы
}
```
- Класс может реализовать много интерфейсов, но наследоваться только от одного класса.
- **Функциональный интерфейс** — ровно один абстрактный метод. Вместо класса можно передать **лямбду**:
  `MenuAction` (`ui/MenuAction.java`) → `execute(() -> handle(choice))`.

## 8. Полиморфизм

Переменная типа «родитель/интерфейс» может хранить объект любого наследника, и вызывается метод **реального** объекта:
```java
CrudRepository<Guest> repo = new GuestRepository();   // тип интерфейса, объект - конкретного класса
BaseMenu menu = new GuestMenu(...);
menu.run();   // внутри run() вызывается GuestMenu.getTitle(), а не BaseMenu
```
У нас: `BaseMenu.run()` + 4 меню, 3 реализации `CrudRepository`, переопределённый `toString()` в моделях и enum'ах.
Перегрузка методов (`readInt(String)`, `readInt(String, int)`, `readInt(String, int, int)`) — тоже вид полиморфизма (статический).

## 9. enum

```java
public enum BookingStatus {
    CREATED("Создано"), CONFIRMED("Подтверждено"), COMPLETED("Завершено"), CANCELLED("Отменено");

    private final String title;                       // у enum могут быть поля
    BookingStatus(String title) { this.title = title; } // и конструктор (всегда private)

    public boolean canChangeTo(BookingStatus next) { ... }  // и методы
}

BookingStatus s = BookingStatus.CREATED;
s.name();                            // "CREATED" - имя константы (пишем в базу)
s.toString();                        // "Создано" - у нас переопределён (печатаем на экран)
BookingStatus.valueOf("CREATED");    // строка -> enum (читаем из базы)
BookingStatus.values();              // массив всех значений (для меню выбора)
s == BookingStatus.CREATED           // enum'ы можно сравнивать через ==
```
Где: `model/BookingStatus.java`, `model/RoomType.java`.

## 10. switch

Новый синтаксис со стрелкой (Java 14+), без `break`:
```java
switch (choice) {                    // как оператор
    case 1 -> addGuest();
    case 2 -> TablePrinter.printGuests(guestService.getAll());
}

List<Booking> result = switch (type) {   // как выражение - возвращает значение
    case 1 -> bookingService.sortByCheckIn();
    case 2 -> bookingService.sortByPriceDesc();
    default -> bookingService.sortByGuestName();
};
```
Где: все `handle()` в меню, `BookingMenu.sort()`, `BookingStatus.canChangeTo()`.

## 11. Исключения

```java
// Бросить
throw new BusinessException("Дата выезда должна быть позже даты заезда.");

// Поймать
try {
    return Integer.parseInt(line);
} catch (NumberFormatException e) {
    System.out.println("Ошибка: нужно ввести целое число.");
}

// Объявить, что метод может бросить проверяемое исключение
public Guest save(Guest guest) throws SQLException { ... }
```
- **Checked** (проверяемые, наследники `Exception`): `SQLException`, `IOException`. Компилятор заставляет
  либо поймать (`catch`), либо объявить (`throws`). Поэтому у методов репозиториев и сервисов есть `throws SQLException`.
- **Unchecked** (наследники `RuntimeException`): `NumberFormatException`, `NullPointerException`, наши
  `BusinessException` и `EntityNotFoundException`. `throws` писать не обязательно.
- Несколько `catch` проверяются сверху вниз — первым ставят более конкретный тип.
- **Своё исключение** — просто класс-наследник:
  ```java
  public class BusinessException extends RuntimeException {
      public BusinessException(String message) { super(message); }   // сообщение потом берём через e.getMessage()
  }
  ```
- **try-with-resources** — ресурсы в скобках закроются автоматически (вызовется `close()`), даже при исключении:
  ```java
  try (Connection connection = DatabaseManager.getConnection();
       PreparedStatement statement = connection.prepareStatement(sql)) {
      ...
  }   // здесь statement.close() и connection.close() вызовутся сами
  ```
Где: `exception/*`, `ui/BaseMenu.execute()`, `ui/ConsoleInput`, все репозитории.

## 12. Коллекции

| Интерфейс | Реализация | Что это | У нас |
|---|---|---|---|
| `List<T>` | `ArrayList<T>` | упорядоченный список, как `vector` / Python list | результаты `findAll()` |
| `Map<K,V>` | `HashMap`, `LinkedHashMap` | словарь «ключ → значение» | статистика `Map<String, String>` |
| `Optional<T>` | — | «коробка»: либо объект, либо пусто (вместо null) | `findById()` |

```java
List<Guest> guests = new ArrayList<>();   // <> - тип выведется сам (Guest)
guests.add(guest);
guests.size();
guests.isEmpty();
for (Guest g : guests) { ... }            // перебор (for-each)

Map<String, String> stats = new LinkedHashMap<>();   // LinkedHashMap помнит порядок добавления
stats.put("Всего гостей", "6");
stats.getOrDefault(key, 0L);               // значение или значение по умолчанию
stats.forEach((name, value) -> System.out.println(name + ": " + value));

Optional<Guest> found = guestRepository.findById(id);
found.isPresent(); found.isEmpty(); found.get();
found.orElseThrow(() -> new EntityNotFoundException("..."));   // достать или бросить исключение
```
- Слева обычно пишут интерфейс (`List`), справа — конкретный класс (`ArrayList`). Так код не зависит от реализации.
- `list.stream().toList()` возвращает **неизменяемый** список.

## 13. Generics (обобщения)

`<T>` — параметр-тип, как шаблоны в C++ или generics в C#:
```java
public interface CrudRepository<T> { T save(T entity); ... }   // T подставится: Guest, Room, Booking
public <T> T choose(String title, T[] options) { ... }         // generic-метод (ConsoleInput)
RoomType type = input.choose("Тип номера:", RoomType.values());  // T = RoomType
```

## 14. Лямбды и ссылки на методы

```java
b -> b.getStatus() == status            // лямбда: параметр b, возвращает boolean
(name, value) -> System.out.println(...) // два параметра
() -> handle(choice)                     // без параметров
Booking::getCheckIn                      // ссылка на метод = b -> b.getCheckIn()
```
Лямбда — короткая запись объекта функционального интерфейса. Переменные снаружи можно использовать,
только если они не меняются после присваивания («effectively final»), как `choice` в `BaseMenu.run()`.

## 15. Stream API

Поток — цепочка операций над коллекцией (как LINQ в C# или генераторы в Python):
```java
bookings.stream()                                         // начать поток
        .filter(b -> b.getStatus() == BookingStatus.COMPLETED)   // оставить подходящие
        .mapToDouble(Booking::getTotalPrice)              // превратить каждый в число
        .sum();                                           // сумма

bookings.stream().sorted(Comparator.comparing(Booking::getCheckIn)).toList();   // сортировка
bookings.stream().map(Booking::getRoomId).distinct().count();                   // кол-во уникальных
notCancelled.stream().mapToDouble(Booking::getTotalPrice).average().orElse(0);  // среднее (или 0, если пусто)

// группировка: Map<статус, количество>
bookings.stream().collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));
```
| Операция | Что делает |
|---|---|
| `filter(условие)` | оставить элементы, где условие true |
| `map(функция)` | преобразовать каждый элемент |
| `sorted(comparator)` | отсортировать |
| `distinct()` | убрать повторы |
| `count()`, `sum()`, `average()` | посчитать |
| `toList()`, `collect(...)` | собрать результат |

Где: `service/BookingService.java` (поиск, фильтры, сортировка, статистика).

## 16. Comparator

```java
Comparator.comparing(Booking::getCheckIn)                     // по возрастанию даты
Comparator.comparing(Booking::getTotalPrice).reversed()       // по убыванию цены
Comparator.comparing(Booking::getGuestName).thenComparing(Booking::getCheckIn)  // по имени, потом по дате
```

## 17. Строки

```java
"Гость #" + id + ": " + fullName          // склейка через +
String.format("%.2f", price)               // "2500,00" - форматирование (2 знака после запятой)
System.out.printf("%-4d %-30s%n", id, name) // печать по шаблону: %d - целое, %s - строка, %f - дробное,
                                            // -30 - ширина 30 с выравниванием влево, %n - перевод строки
s.trim()               // убрать пробелы по краям
s.toLowerCase()        // в нижний регистр
s.isEmpty()            // пустая ли
s.contains("иван")     // содержит ли подстроку
s.matches("регулярка") // соответствует ли регулярному выражению
"-".repeat(80)         // повторить строку
s.replace(',', '.')    // замена символа
```
**Text block** (многострочная строка, Java 15+) — `BookingRepository.SELECT_WITH_JOIN`:
```java
String sql = """
        SELECT b.id, ...
        FROM bookings b
        """;
```

## 18. Даты: java.time

```java
LocalDate.now()                       // сегодня
LocalDate.parse("2026-11-05")         // из строки (формат ГГГГ-ММ-ДД), иначе DateTimeParseException
a.isBefore(b), a.isAfter(b), a.equals(b)
ChronoUnit.DAYS.between(checkIn, checkOut)   // разница в днях = количество ночей
```

## 19. Ввод с клавиатуры

```java
Scanner scanner = new Scanner(System.in, charset);  // читать из консоли в нужной кодировке
String line = scanner.nextLine();                   // прочитать строку
int n = Integer.parseInt(line);                     // строка -> int (NumberFormatException при ошибке)
double d = Double.parseDouble(line);                // строка -> double
```
Где: `ui/ConsoleInput.java`.

## 20. JDBC — работа с базой данных

JDBC — стандартный набор интерфейсов Java для любой СУБД. Конкретную реализацию даёт **драйвер** (у нас PostgreSQL, зависимость в `pom.xml`).

| Класс | Роль |
|---|---|
| `DriverManager` | открывает соединение по URL `jdbc:postgresql://localhost:5432/hotel_db` |
| `Connection` | соединение с базой |
| `PreparedStatement` | SQL-запрос с параметрами `?` |
| `ResultSet` | результат SELECT, читается построчно |
| `SQLException` | любая ошибка базы; `getSQLState()` — стандартный код ошибки |

```java
String sql = "UPDATE guests SET full_name = ?, phone = ?, email = ? WHERE id = ?";
try (Connection connection = DatabaseManager.getConnection();
     PreparedStatement statement = connection.prepareStatement(sql)) {
    statement.setString(1, guest.getFullName());   // нумерация ? с единицы!
    statement.setString(2, guest.getPhone());
    statement.setString(3, guest.getEmail());
    statement.setInt(4, guest.getId());
    statement.executeUpdate();                     // INSERT/UPDATE/DELETE
}

try (ResultSet rs = statement.executeQuery()) {    // SELECT
    while (rs.next()) {                            // перейти к следующей строке
        int id = rs.getInt("id");                  // взять значение колонки по имени
        String name = rs.getString("full_name");
        LocalDate d = rs.getDate("check_in").toLocalDate();
    }
}
statement.setDate(3, Date.valueOf(localDate));     // LocalDate -> java.sql.Date
```
**Statement vs PreparedStatement:**
- `Statement` — SQL склеивается из строк: `"... WHERE email = '" + email + "'"`. Ввод `' OR '1'='1` превращается в часть команды — **SQL-инъекция**.
- `PreparedStatement` — SQL с `?`, значения передаются отдельно через `setString/setInt`. Инъекция невозможна, код чище. Используем только его.

Где: `util/DatabaseManager.java`, `repository/*`.

## 21. Ресурсы и Properties

Файлы из `src/main/resources` при сборке попадают в программу и читаются так:
```java
InputStream in = DatabaseManager.class.getClassLoader().getResourceAsStream("database.properties");
Properties properties = new Properties();
properties.load(in);                       // файл вида ключ=значение
properties.getProperty("db.url");
```

## 22. Apache POI — Excel

```java
try (Workbook workbook = new XSSFWorkbook();                 // новый .xlsx
     FileOutputStream out = new FileOutputStream(fileName)) {
    Sheet sheet = workbook.createSheet("Гости");             // лист
    Row row = sheet.createRow(0);                            // строка (с нуля)
    row.createCell(0).setCellValue("ID");                    // ячейка
    sheet.autoSizeColumn(0);                                 // ширина по содержимому
    workbook.write(out);                                     // записать в файл
}
```
`String... titles` в `createHeader` — **varargs**: метод принимает любое количество строк, внутри это массив.
Где: `util/ExcelExporter.java`.

## 23. Аннотации

`@Override` — «переопределяю метод родителя/интерфейса», компилятор проверит.
`@FunctionalInterface` — «у интерфейса ровно один метод», компилятор проверит (`MenuAction`).

## 24. Maven

```xml
<dependency>                                  <!-- библиотека, Maven скачает её сам -->
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.13</version>
</dependency>
```
| Команда | Что делает |
|---|---|
| `mvn compile` | скачать зависимости и скомпилировать в `target/classes` |
| `mvn compile exec:java` | скомпилировать и запустить `ru.mirea.hotel.Main` |
| `mvn clean` | удалить папку `target` |

Структура папок стандартная: код в `src/main/java`, ресурсы в `src/main/resources`.

## 25. JavaFX (КР2)

### 25.1 Основные понятия
| Понятие | Что это | У нас |
|---|---|---|
| `Application` | базовый класс JavaFX-программы, метод `start(Stage)` | `fx/HotelFxApp.java` |
| `Stage` | окно | главный Stage приходит в `start()`; у диалогов свой |
| `Scene` | содержимое окна | `new Scene(root, 1280, 780)` |
| `Node` | любой элемент: кнопка, таблица, панель | всё из FXML |
| JavaFX Application Thread | единственный поток, где можно трогать интерфейс | все обработчики выполняются в нём |

```java
public class HotelFxApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Parent root = new FXMLLoader(getClass().getResource("main.fxml")).load(); // интерфейс из FXML
        stage.setScene(new Scene(root, 1280, 780));
        stage.show();
    }
}
Application.launch(HotelFxApp.class, args);   // запуск (HotelFxLauncher)
```

### 25.2 Контейнеры (раскладка элементов)
| Контейнер | Как раскладывает | У нас |
|---|---|---|
| `BorderPane` | 5 зон: top, bottom, left, right, center | корень главного окна и вкладок |
| `HBox` / `VBox` | в строку / в столбик | панели кнопок |
| `FlowPane` | в строку с переносом, если не помещается | панель фильтров |
| `GridPane` | таблица из строк и столбцов | формы (подпись — поле), статистика |
| `TabPane` + `Tab` | вкладки | главное окно |
| `SplitPane` | две части с перетаскиваемой границей | гости/номера + их брони |
| `Region` с `HBox.hgrow="ALWAYS"` | «пружина», раздвигает соседей | кнопка «Обновить» справа |

### 25.3 Элементы управления
`Button`, `Label`, `TextField` (`getText()`, `setText()`, `promptText` — серая подсказка),
`ComboBox<T>` (`getItems()`, `getValue()`, `setValue()`), `DatePicker` (`getValue()` → `LocalDate`),
`TableView<T>` + `TableColumn<T, V>`, `PieChart`.

### 25.4 FXML и контроллер
```xml
<!-- fx:controller - класс-контроллер; stylesheets="@app.css" - CSS-файл рядом с этим FXML -->
<BorderPane xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="ru.mirea.hotel.fx.controller.GuestsController"
            stylesheets="@app.css">
    <top>
        <!-- onAction="#onDelete": при клике вызывается метод onDelete() контроллера -->
        <Button text="Удалить" onAction="#onDelete" styleClass="danger-button"/>
    </top>
    <center>
        <!-- fx:id="table": объект попадёт в поле контроллера с именем table -->
        <TableView fx:id="table"/>
    </center>
</BorderPane>
```
```java
public class GuestsController {
    @FXML private TableView<Guest> table;        // FXMLLoader сам положит сюда объект с fx:id="table"

    @FXML private void initialize() { ... }      // вызывается после заполнения всех @FXML-полей
    @FXML private void onDelete() { ... }        // обработчик onAction="#onDelete"
}
```
- `<?import javafx.scene.control.Button?>` в начале FXML — как `import` в Java.
- `fx:include source="guests-tab.fxml" fx:id="guestsView"` — вставить другой FXML; его контроллер попадёт в поле `guestsViewController`.
- `FXMLLoader.load()` читает FXML, создаёт все узлы и контроллер; `loader.getController()` — получить контроллер.

### 25.5 Свойства и слушатели
У элементов JavaFX значения хранятся в **свойствах** (property), на изменение которых можно подписаться:
```java
searchField.textProperty().addListener((obs, oldValue, newValue) -> refresh()); // текст изменился
statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> showDetails(selected));
sorted.comparatorProperty().bind(table.comparatorProperty());   // bind - значение всегда равно другому свойству
```

### 25.6 ObservableList, TableView, SortedList
```java
ObservableList<Booking> bookings = FXCollections.observableArrayList(); // "наблюдаемый" список
SortedList<Booking> sorted = new SortedList<>(bookings);                // отсортированный вид этого списка
sorted.comparatorProperty().bind(table.comparatorProperty());          // порядок = клик по заголовку
table.setItems(sorted);
bookings.setAll(bookingService.getAll());   // заменили данные -> таблица перерисовалась сама
```
- `PropertyValueFactory("guestName")` (в FXML) — значение ячейки = `booking.getGuestName()`.
- `setCellFactory(...)` — **как** показывать значение (формат даты/денег), значение для сортировки не меняется.
- `setRowFactory(...)` — настройка целой строки (у нас — CSS-класс по статусу).
- Анонимный класс: `new TableCell<>() { @Override protected void updateItem(...) {...} }` — наследник прямо на месте, без отдельного файла.

### 25.7 Диалоги
```java
Alert alert = new Alert(Alert.AlertType.ERROR, "Текст ошибки", ButtonType.OK);
alert.showAndWait();                                    // показать и ждать, пока закроют

new ChoiceDialog<>(первый, список).showAndWait();       // выбор из списка -> Optional<T>

FileChooser chooser = new FileChooser();
File file = chooser.showSaveDialog(window);              // пользователь выбирает куда сохранить (null = отмена)
```
`Dialog` + `DialogPane` из FXML — наши формы (`FxDialogs.openForm`). `addEventFilter(ActionEvent.ACTION, e -> e.consume())` —
перехватить нажатие кнопки до диалога и отменить его (окно не закроется).

### 25.8 StringConverter
Как объект выглядит в `ComboBox`:
```java
guestBox.setConverter(new StringConverter<>() {
    public String toString(Guest g) { return g == null ? "" : g.getFullName() + " (" + g.getEmail() + ")"; }
    public Guest fromString(String s) { return null; }   // не нужен: список не редактируется вручную
});
```

### 25.9 CSS в JavaFX
```css
.primary-button { -fx-background-color: #2f6fde; -fx-text-fill: white; }   /* styleClass="primary-button" */
.button:hover { -fx-background-color: #eef2f9; }                            /* :hover - под мышью */
.table-row-cell.status-cancelled .table-cell { -fx-text-fill: #a0a7b4; }    /* строка с двумя классами */
```
Все свойства начинаются с `-fx-`. Добавить/убрать класс из кода: `node.getStyleClass().add("field-error")` / `.remove(...)`.

### 25.10 Pattern matching instanceof (Java 16+)
```java
if (e instanceof SQLException sql) {   // проверили тип и сразу получили переменную sql нужного типа
    sql.getSQLState();
}
```
Где: `FxDialogs.toMessage`.

### 25.11 Generic-метод с ограничением
```java
public static <C extends FormController> boolean openForm(Window owner, String fxml, String title, Consumer<C> init)
```
`C` — любой наследник `FormController` (GuestDialogController, RoomDialogController, ...).
`Consumer<C>` — функция «принять объект и что-то с ним сделать», передаётся лямбдой: `(GuestDialogController f) -> f.setGuest(g)`.

## 26. Шпаргалка: Java vs другие языки

| Java | C++ | Python | C# |
|---|---|---|---|
| `String s = "a";` | `std::string s = "a";` | `s = "a"` | `string s = "a";` |
| `List<Integer> l = new ArrayList<>();` | `std::vector<int> l;` | `l = []` | `List<int> l = new();` |
| `Map<String,Integer> m = new HashMap<>();` | `std::map<...>` | `m = {}` | `Dictionary<...>` |
| `a.equals(b)` для строк | `a == b` | `a == b` | `a == b` |
| `extends` / `implements` | `: public Base` | `class A(Base)` | `: Base, IFace` |
| `x -> x * 2` | `[](int x){ return x*2; }` | `lambda x: x*2` | `x => x * 2` |
| `list.stream().filter(...)` | `std::copy_if` | list comprehension | LINQ `.Where(...)` |
| `try (Res r = ...) {}` | RAII (деструктор) | `with ... as r:` | `using (var r = ...) {}` |
| `throws SQLException` | — | — | — (в C# нет checked-исключений) |
| нет `delete` — мусор собирает GC | `delete` / умные указатели | GC | GC |
