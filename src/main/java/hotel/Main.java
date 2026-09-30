package hotel;

import hotel.exception.BusinessException;
import hotel.exception.EntityNotFoundException;
import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.RoomType;
import hotel.model.User;
import hotel.repository.BookingRepository;
import hotel.repository.UserRepository;
import hotel.service.BookingService;
import hotel.service.UserService;
import hotel.util.DatabaseManager;
import hotel.util.ExcelExporter;
import hotel.util.Validator;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/**
 * Консольная информационная система "Гостиница: бронирование номеров".
 * Главный класс - отвечает только за меню и ввод/вывод.
 * Вся логика вынесена в сервисный и репозиторный слои.
 */
public class Main {

    // Читаем ввод в кодировке консоли (в Windows это часто cp866/cp1251, а не UTF-8),
    // иначе русские буквы в ФИО превратятся в "кракозябры" и не пройдут проверку.
    private static final Scanner scanner = new Scanner(System.in, consoleCharset());

    private static final UserRepository userRepository = new UserRepository();
    private static final BookingRepository bookingRepository = new BookingRepository();

    private static final UserService userService = new UserService(userRepository);
    private static final BookingService bookingService = new BookingService(bookingRepository, userRepository);

    public static void main(String[] args) {
        System.out.println("Проверка подключения к базе данных...");
        try (Connection connection = DatabaseManager.getConnection()) {
            System.out.println("Подключение к базе данных установлено успешно.\n");
        } catch (SQLException e) {
            System.out.println("Не удалось подключиться к базе данных: " + e.getMessage());
            System.out.println("Проверьте настройки в файле util/DatabaseManager.java и работу PostgreSQL.");
            return;
        }

        try {
            mainMenu();
        } catch (NoSuchElementException e) {
            // Поток ввода закрыт (Ctrl+Z / Ctrl+D или конец файла при перенаправлении ввода)
            System.out.println("\nВвод завершён.");
        }
        System.out.println("Работа программы завершена. До свидания!");
    }

    // ===================== ГЛАВНОЕ МЕНЮ =====================

    private static void mainMenu() {
        boolean running = true;
        while (running) {
            System.out.println("\n================ ГОСТИНИЦА: БРОНИРОВАНИЕ НОМЕРОВ ================");
            System.out.println("1. Гости");
            System.out.println("2. Бронирования");
            System.out.println("3. Поиск бронирований");
            System.out.println("4. Фильтрация бронирований");
            System.out.println("5. Статистика");
            System.out.println("6. Экспорт данных в Excel");
            System.out.println("7. Вывести таблицы базы данных");
            System.out.println("0. Выход");
            System.out.print("Выберите действие: ");

            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> guestsMenu();
                    case "2" -> bookingsMenu();
                    case "3" -> searchMenu();
                    case "4" -> filterMenu();
                    case "5" -> showStatistics();
                    case "6" -> exportMenu();
                    case "7" -> showRawTables();
                    case "0" -> running = false;
                    default -> System.out.println("Неизвестный пункт меню. Попробуйте снова.");
                }
            } catch (EntityNotFoundException | BusinessException e) {
                // Ожидаемые ошибки бизнес-логики - программа не должна падать
                System.out.println("Ошибка: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("Ошибка базы данных: " + e.getMessage());
            }
        }
    }

    // ===================== МЕНЮ "ГОСТИ" =====================

    private static void guestsMenu() throws SQLException {
        System.out.println("\n----- Гости -----");
        System.out.println("1. Добавить гостя");
        System.out.println("2. Список всех гостей");
        System.out.println("3. Найти гостя по ID");
        System.out.println("4. Изменить данные гостя");
        System.out.println("5. Удалить гостя");
        System.out.println("0. Назад");
        System.out.print("Выберите действие: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> createGuest();
            case "2" -> printUsers(userService.getAllUsers());
            case "3" -> System.out.println(userService.getUserById(readInt("Введите ID гостя: ")));
            case "4" -> updateGuest();
            case "5" -> deleteGuest();
            case "0" -> { }
            default -> System.out.println("Неизвестный пункт меню.");
        }
    }

    private static void createGuest() throws SQLException {
        String fullName = readValidString("Введите ФИО гостя (например, Иванов Иван Иванович): ", Validator::validateFullName);
        String phone = readValidString("Введите телефон (например, +7 900 123-45-67): ", Validator::validatePhone);
        String email = readValidString("Введите email (например, ivanov@mail.ru): ", Validator::validateEmail);
        String passport = readValidString("Введите номер паспорта (серия и номер, например 4500 123456): ", Validator::validatePassport);

        User user = userService.createUser(fullName, phone, email, passport);
        System.out.println("Гость успешно добавлен: " + user);
    }

    private static void updateGuest() throws SQLException {
        int id = readInt("Введите ID гостя, которого нужно изменить: ");
        System.out.println("Текущие данные: " + userService.getUserById(id));

        String fullName = readValidString("Новое ФИО: ", Validator::validateFullName);
        String phone = readValidString("Новый телефон: ", Validator::validatePhone);
        String email = readValidString("Новый email: ", Validator::validateEmail);
        String passport = readValidString("Новый номер паспорта: ", Validator::validatePassport);

        userService.updateUser(id, fullName, phone, email, passport);
        System.out.println("Данные гостя обновлены.");
    }

    private static void deleteGuest() throws SQLException {
        int id = readInt("Введите ID гостя для удаления: ");
        userService.deleteUser(id);
        System.out.println("Гость удалён.");
    }

    // ===================== МЕНЮ "БРОНИРОВАНИЯ" =====================

    private static void bookingsMenu() throws SQLException {
        System.out.println("\n----- Бронирования -----");
        System.out.println("1. Создать бронирование");
        System.out.println("2. Список всех бронирований");
        System.out.println("3. Найти бронирование по ID");
        System.out.println("4. Изменить данные бронирования");
        System.out.println("5. Изменить статус бронирования");
        System.out.println("6. Удалить бронирование");
        System.out.println("7. Сортировка бронирований");
        System.out.println("0. Назад");
        System.out.print("Выберите действие: ");

        switch (scanner.nextLine().trim()) {
            case "1" -> createBooking();
            case "2" -> printBookings(bookingService.getAllBookings());
            case "3" -> System.out.println(bookingService.getBookingById(readInt("Введите ID бронирования: ")));
            case "4" -> updateBooking();
            case "5" -> changeBookingStatus();
            case "6" -> deleteBooking();
            case "7" -> sortMenu();
            case "0" -> { }
            default -> System.out.println("Неизвестный пункт меню.");
        }
    }

    private static void createBooking() throws SQLException {
        int userId = readValidInt("Введите ID гостя: ", id -> Validator.validateId(id, "гостя"));
        int roomNumber = readValidInt("Введите номер комнаты (101-999, например 205): ", Validator::validateRoomNumber);
        RoomType roomType = readRoomType();
        LocalDate checkIn = readValidDate("Введите дату заезда (гггг-мм-дд): ",
                date -> Validator.validateCheckInForNewBooking(date, LocalDate.now()));
        LocalDate checkOut = readValidDate("Введите дату выезда (гггг-мм-дд): ",
                date -> Validator.validateStayDates(checkIn, date));

        Booking booking = bookingService.createBooking(userId, roomNumber, roomType, checkIn, checkOut);
        System.out.println("Бронирование успешно создано: " + booking);
    }

    private static void updateBooking() throws SQLException {
        int id = readInt("Введите ID бронирования для изменения: ");
        // Сразу проверяем, что бронь можно менять, чтобы не заставлять вводить все поля зря
        System.out.println("Текущие данные: " + bookingService.getEditableBooking(id));

        int roomNumber = readValidInt("Новый номер комнаты: ", Validator::validateRoomNumber);
        RoomType roomType = readRoomType();
        LocalDate checkIn = readDate("Новая дата заезда (гггг-мм-дд): ");
        LocalDate checkOut = readValidDate("Новая дата выезда (гггг-мм-дд): ",
                date -> Validator.validateStayDates(checkIn, date));

        bookingService.updateBooking(id, roomNumber, roomType, checkIn, checkOut);
        System.out.println("Бронирование обновлено.");
    }

    private static void changeBookingStatus() throws SQLException {
        int id = readInt("Введите ID бронирования: ");
        System.out.println("Текущий статус: " + bookingService.getBookingById(id).getStatus());
        BookingStatus newStatus = readBookingStatus();
        bookingService.changeStatus(id, newStatus);
        System.out.println("Статус бронирования изменён на " + newStatus + ".");
    }

    private static void deleteBooking() throws SQLException {
        int id = readInt("Введите ID бронирования для удаления: ");
        bookingService.deleteBooking(id);
        System.out.println("Бронирование удалено.");
    }

    private static void sortMenu() throws SQLException {
        System.out.println("1. По дате заезда (сначала ранние)");
        System.out.println("2. По стоимости (сначала дорогие)");
        System.out.print("Выберите способ сортировки: ");
        switch (scanner.nextLine().trim()) {
            case "1" -> printBookings(bookingService.sortByCheckInDate());
            case "2" -> printBookings(bookingService.sortByTotalPriceDescending());
            default -> System.out.println("Неизвестный вариант.");
        }
    }

    // ===================== МЕНЮ "ПОИСК" =====================

    private static void searchMenu() throws SQLException {
        System.out.println("\n----- Поиск бронирований -----");
        System.out.println("1. По имени гостя");
        System.out.println("2. По номеру комнаты");
        System.out.print("Выберите способ поиска: ");
        switch (scanner.nextLine().trim()) {
            case "1" -> printBookings(bookingService.searchByGuestName(readNonEmptyString("Введите имя (или часть имени): ")));
            case "2" -> printBookings(bookingService.searchByRoomNumber(readInt("Введите номер комнаты: ")));
            default -> System.out.println("Неизвестный вариант.");
        }
    }

    // ===================== МЕНЮ "ФИЛЬТРАЦИЯ" =====================

    private static void filterMenu() throws SQLException {
        System.out.println("\n----- Фильтрация бронирований -----");
        System.out.println("1. По статусу");
        System.out.println("2. По типу номера");
        System.out.print("Выберите способ фильтрации: ");
        switch (scanner.nextLine().trim()) {
            case "1" -> printBookings(bookingService.filterByStatus(readBookingStatus()));
            case "2" -> printBookings(bookingService.filterByRoomType(readRoomType()));
            default -> System.out.println("Неизвестный вариант.");
        }
    }

    // ===================== СТАТИСТИКА =====================

    private static void showStatistics() throws SQLException {
        System.out.println("\n----- Статистика системы -----");
        Map<String, String> stats = bookingService.getStatistics();
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    // ===================== ЭКСПОРТ В EXCEL =====================

    private static void exportMenu() throws SQLException {
        String[] headers = {"ID", "ID гостя", "Номер комнаты", "Тип номера", "Заезд", "Выезд", "Статус", "Стоимость"};
        List<String[]> rows = new ArrayList<>();
        for (Booking b : bookingService.getAllBookings()) {
            rows.add(new String[]{
                    String.valueOf(b.getId()),
                    String.valueOf(b.getUserId()),
                    String.valueOf(b.getRoomNumber()),
                    b.getRoomType().name(),
                    b.getCheckInDate().toString(),
                    b.getCheckOutDate().toString(),
                    b.getStatus().name(),
                    String.valueOf(b.getTotalPrice())
            });
        }

        String filePath = "bookings_export.xlsx";
        try {
            ExcelExporter.export(filePath, "Бронирования", headers, rows);
            System.out.println("Данные экспортированы в файл: " + filePath);
        } catch (Exception e) {
            System.out.println("Ошибка при экспорте в Excel: " + e.getMessage());
        }
    }

    // ===================== ВЫВОД СЫРЫХ ТАБЛИЦ БД =====================

    private static void showRawTables() throws SQLException {
        System.out.println("\n----- Таблица users -----");
        printUsers(userService.getAllUsers());

        System.out.println("\n----- Таблица bookings -----");
        printBookings(bookingService.getAllBookings());
    }

    // ===================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ВЫВОДА =====================

    private static void printUsers(List<User> users) {
        if (users.isEmpty()) {
            System.out.println("Список пуст.");
            return;
        }
        users.forEach(System.out::println);
    }

    private static void printBookings(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            System.out.println("Список пуст.");
            return;
        }
        bookings.forEach(System.out::println);
    }

    // ===================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ВВОДА С ОБРАБОТКОЙ ОШИБОК =====================

    /** Кодировка стандартного ввода: свойство stdin.encoding (Java 25+), затем кодировка консоли, затем системная. */
    private static Charset consoleCharset() {
        String stdinEncoding = System.getProperty("stdin.encoding");
        if (stdinEncoding != null && Charset.isSupported(stdinEncoding)) {
            return Charset.forName(stdinEncoding);
        }
        if (System.console() != null) {
            return System.console().charset();
        }
        return Charset.defaultCharset();
    }

    /** Считывает целое число, повторяя запрос, пока пользователь не введёт корректное значение. */
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно ввести целое число. Попробуйте снова.");
            }
        }
    }

    /** Считывает непустую строку. */
    private static String readNonEmptyString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Ошибка: значение не может быть пустым. Попробуйте снова.");
        }
    }

    /** Считывает дату в формате гггг-мм-дд. */
    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: неверный формат даты. Пример правильного формата: 2026-10-01");
            }
        }
    }

    /**
     * Считывает строку и сразу проверяет её валидатором (например, Validator::validateEmail).
     * Если значение некорректно - показывает причину и просит ввести это же поле ещё раз.
     * Возвращает значение, уже приведённое валидатором к единому формату.
     */
    private static String readValidString(String prompt, UnaryOperator<String> validator) {
        while (true) {
            String input = readNonEmptyString(prompt);
            try {
                return validator.apply(input);
            } catch (BusinessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /** Считывает целое число и проверяет его (например, номер комнаты), повторяя ввод при ошибке. */
    private static int readValidInt(String prompt, IntConsumer check) {
        while (true) {
            int value = readInt(prompt);
            try {
                check.accept(value);
                return value;
            } catch (BusinessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /** Считывает дату и проверяет её (не в прошлом, выезд позже заезда и т.д.), повторяя ввод при ошибке. */
    private static LocalDate readValidDate(String prompt, Consumer<LocalDate> check) {
        while (true) {
            LocalDate value = readDate(prompt);
            try {
                check.accept(value);
                return value;
            } catch (BusinessException e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        }
    }

    /** Показывает список типов номеров и считывает выбор пользователя. */
    private static RoomType readRoomType() {
        RoomType[] values = RoomType.values();
        while (true) {
            System.out.println("Выберите тип номера:");
            for (int i = 0; i < values.length; i++) {
                System.out.println((i + 1) + ". " + values[i] + " (" + values[i].getPricePerNight() + " руб/ночь)");
            }
            System.out.print("Ваш выбор: ");
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice >= 1 && choice <= values.length) {
                    return values[choice - 1];
                }
            } catch (NumberFormatException ignored) {
                // обработается ниже общим сообщением об ошибке
            }
            System.out.println("Ошибка: некорректный выбор типа номера.");
        }
    }

    /** Показывает список статусов бронирования и считывает выбор пользователя. */
    private static BookingStatus readBookingStatus() {
        BookingStatus[] values = BookingStatus.values();
        while (true) {
            System.out.println("Выберите статус:");
            for (int i = 0; i < values.length; i++) {
                System.out.println((i + 1) + ". " + values[i]);
            }
            System.out.print("Ваш выбор: ");
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice >= 1 && choice <= values.length) {
                    return values[choice - 1];
                }
            } catch (NumberFormatException ignored) {
                // обработается ниже общим сообщением об ошибке
            }
            System.out.println("Ошибка: некорректный выбор статуса.");
        }
    }
}
