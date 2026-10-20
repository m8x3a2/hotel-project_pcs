package ru.mirea.hotel;

import ru.mirea.hotel.repository.BookingRepository;
import ru.mirea.hotel.repository.GuestRepository;
import ru.mirea.hotel.repository.RoomRepository;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;
import ru.mirea.hotel.ui.ConsoleInput;
import ru.mirea.hotel.ui.MainMenu;
import ru.mirea.hotel.util.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;

/**
 * Точка входа в программу. Здесь только запуск: проверка базы,
 * создание объектов всех слоёв и показ главного меню.
 *
 * Слои: Console UI (ui) -> Service (service) -> Repository/JDBC (repository) -> PostgreSQL
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("Подключение к базе данных...");
        try (Connection connection = DatabaseManager.getConnection()) {
            System.out.println("Подключение установлено: " + connection.getMetaData().getURL());
        } catch (SQLException e) {
            System.out.println("Ошибка подключения к базе данных: " + e.getMessage());
            System.out.println(connectionHint(e.getSQLState()));
            return;
        }

        // Создаём репозитории (работа с таблицами)
        GuestRepository guestRepository = new GuestRepository();
        RoomRepository roomRepository = new RoomRepository();
        BookingRepository bookingRepository = new BookingRepository();

        // Создаём сервисы и передаём им репозитории через конструктор
        GuestService guestService = new GuestService(guestRepository, bookingRepository);
        RoomService roomService = new RoomService(roomRepository, bookingRepository);
        BookingService bookingService = new BookingService(bookingRepository, guestRepository, roomRepository);

        // Создаём консольный интерфейс и запускаем главное меню
        MainMenu mainMenu = new MainMenu(new ConsoleInput(), guestService, roomService, bookingService);
        try {
            mainMenu.run();
        } catch (NoSuchElementException e) {
            // Поток ввода закрыт (например, нажали Ctrl+D) - просто выходим
            System.out.println();
        }
        System.out.println("Работа программы завершена. До свидания!");
    }

    /**
     * Понятная подсказка по коду ошибки SQLState.
     * Нужна потому, что в русской Windows PostgreSQL иногда присылает текст ошибки "кракозябрами".
     */
    private static String connectionHint(String sqlState) {
        if ("28P01".equals(sqlState)) {
            return "Причина: неверный логин или пароль (см. src/main/resources/database.properties).";
        }
        if ("3D000".equals(sqlState)) {
            return "Причина: база данных не существует. Создайте её: CREATE DATABASE hotel_db; (см. README).";
        }
        return "Проверьте, что PostgreSQL запущен и адрес/порт в src/main/resources/database.properties верные.";
    }
}
