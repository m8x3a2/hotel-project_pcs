# Что где лежит

```
hotel-project_pcs/
├── src/main/java/hotel/     ← весь код программы
├── sql/                     ← скрипт базы данных
├── konspekty/               ← конспекты по Java (для подготовки, в программе не участвуют)
├── target/                  ← результат сборки (создаётся Maven'ом)
└── файлы в корне            ← настройки сборки и документация
```

---

## Файлы в корне

| Файл | Про что |
|---|---|
| [README.md](README.md) | инструкция: что установить, как создать базу, собрать и запустить |
| [O_PROGRAMME.md](O_PROGRAMME.md) | правила программы (телефон, email, даты…) и в какой строке кода они написаны |
| [STRUKTURA.md](STRUKTURA.md) | этот файл: карта папок и файлов |
| [pom.xml](pom.xml) | настройки Maven: версия Java, библиотеки (PostgreSQL, Apache POI), сборка jar |
| `dependency-reduced-pom.xml` | создаётся автоматически при сборке (плагин shade), руками не трогать, можно удалить |
| `bookings_export.xlsx` | Excel-файл, который создаёт пункт меню 6 «Экспорт» |
| `.gitignore` | какие файлы git не отслеживает (`target/` и т.п.) |
| `.git/` | служебная папка git (история изменений), не трогать |

## `sql/`

| Файл | Про что |
|---|---|
| [init.sql](sql/init.sql) | создаёт таблицы `users` и `bookings` и заполняет тестовыми данными (5 гостей, 12 броней). **Внимание:** удаляет старые таблицы |

## `konspekty/`

| Файл | Про что |
|---|---|
| [KONSPEKT_JAVA.md](konspekty/KONSPEKT_JAVA.md) | краткий конспект по теории Java на примерах из проекта + вопросы к защите |
| [konspekt_java_easy.md](konspekty/konspekt_java_easy.md) | то же подробно и простым языком: память, equals/hashCode, лямбды, стримы по шагам + «проверь себя» |

## `src/main/java/hotel/` — код программы

| Файл | Про что |
|---|---|
| [Main.java](src/main/java/hotel/Main.java) | точка входа, консольное меню, ввод с клавиатуры и проверка полей при вводе |

### `model/` — данные (что храним)

| Файл | Про что |
|---|---|
| [User.java](src/main/java/hotel/model/User.java) | гость: ФИО, телефон, email, паспорт |
| [Booking.java](src/main/java/hotel/model/Booking.java) | бронирование: гость, номер, тип, даты, статус, цена |
| [RoomType.java](src/main/java/hotel/model/RoomType.java) | типы номеров и цена за ночь (STANDARD, DELUXE, SUITE, FAMILY) |
| [BookingStatus.java](src/main/java/hotel/model/BookingStatus.java) | статусы брони (CREATED, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED) |

### `service/` — правила (бизнес-логика)

| Файл | Про что |
|---|---|
| [UserService.java](src/main/java/hotel/service/UserService.java) | добавление/изменение/удаление гостей, проверка дубликатов email и паспорта |
| [BookingService.java](src/main/java/hotel/service/BookingService.java) | брони: проверка дат и занятости номера, расчёт цены, смена статусов, поиск, фильтры, сортировка, статистика |

### `repository/` — работа с базой (SQL)

| Файл | Про что |
|---|---|
| [CrudRepository.java](src/main/java/hotel/repository/CrudRepository.java) | общий интерфейс: save, findAll, findById, update, delete |
| [UserRepository.java](src/main/java/hotel/repository/UserRepository.java) | SQL-запросы к таблице `users` |
| [BookingRepository.java](src/main/java/hotel/repository/BookingRepository.java) | SQL-запросы к таблице `bookings` |

### `util/` — вспомогательное

| Файл | Про что |
|---|---|
| [Validator.java](src/main/java/hotel/util/Validator.java) | все проверки форматов: ФИО, телефон, email, паспорт, номер комнаты, даты |
| [DatabaseManager.java](src/main/java/hotel/util/DatabaseManager.java) | подключение к PostgreSQL (адрес, логин, пароль) |
| [ExcelExporter.java](src/main/java/hotel/util/ExcelExporter.java) | сохранение таблицы в .xlsx |

### `exception/` — свои ошибки

| Файл | Про что |
|---|---|
| [BusinessException.java](src/main/java/hotel/exception/BusinessException.java) | нарушено правило (неверный email, номер занят и т.п.) |
| [EntityNotFoundException.java](src/main/java/hotel/exception/EntityNotFoundException.java) | гость или бронь с таким ID не найдены |

## `target/` — результат сборки

Создаётся командой `mvn clean package`, в git не попадает. Главное здесь —
`target/hotel-booking-system.jar`, его и запускают: `java -jar target/hotel-booking-system.jar`.
Папку можно удалить: при следующей сборке она появится снова.
