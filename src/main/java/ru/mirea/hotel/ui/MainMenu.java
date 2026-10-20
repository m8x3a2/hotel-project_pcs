package ru.mirea.hotel.ui;

import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;
import ru.mirea.hotel.util.ExcelExporter;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Главное меню программы. Тоже наследник BaseMenu.
 */
public class MainMenu extends BaseMenu {

    private final GuestService guestService;
    private final RoomService roomService;
    private final BookingService bookingService;

    // Вложенные меню
    private final GuestMenu guestMenu;
    private final RoomMenu roomMenu;
    private final BookingMenu bookingMenu;

    public MainMenu(ConsoleInput input, GuestService guestService, RoomService roomService,
                    BookingService bookingService) {
        super(input);
        this.guestService = guestService;
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.guestMenu = new GuestMenu(input, guestService, bookingService);
        this.roomMenu = new RoomMenu(input, roomService, bookingService);
        this.bookingMenu = new BookingMenu(input, bookingService, guestService, roomService);
    }

    @Override
    protected String getTitle() {
        return "ГОСТИНИЦА: БРОНИРОВАНИЕ НОМЕРОВ";
    }

    @Override
    protected String[] getItems() {
        return new String[]{
                "Гости",
                "Номера",
                "Бронирования",
                "Поиск бронирований",
                "Фильтрация бронирований",
                "Сортировка бронирований",
                "Статистика",
                "Экспорт данных в Excel",
                "Вывести таблицы базы данных"
        };
    }

    @Override
    protected String getExitItem() {
        return "Выход";
    }

    @Override
    protected void handle(int choice) throws SQLException, IOException {
        switch (choice) {
            case 1 -> guestMenu.run();
            case 2 -> roomMenu.run();
            case 3 -> bookingMenu.run();
            case 4 -> bookingMenu.search();
            case 5 -> bookingMenu.filter();
            case 6 -> bookingMenu.sort();
            case 7 -> showStatistics();
            case 8 -> exportToExcel();
            case 9 -> printAllTables();
        }
    }

    private void showStatistics() throws SQLException {
        Map<String, String> stats = bookingService.getStatistics();
        System.out.println("---------- Статистика ----------");
        // forEach с лямбдой: для каждой пары (название, значение) печатаем строку
        stats.forEach((name, value) -> System.out.println(name + ": " + value));
    }

    private void exportToExcel() throws SQLException, IOException {
        String fileName = input.readLine("Имя файла", "hotel_export.xlsx");
        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            fileName += ".xlsx";
        }
        ExcelExporter.export(fileName, guestService.getAll(), roomService.getAll(), bookingService.getAll());
        System.out.println("Данные выгружены в файл: " + new File(fileName).getAbsolutePath());
    }

    private void printAllTables() throws SQLException {
        System.out.println("\n--- Таблица guests ---");
        TablePrinter.printGuests(guestService.getAll());
        System.out.println("\n--- Таблица rooms ---");
        TablePrinter.printRooms(roomService.getAll());
        System.out.println("\n--- Таблица bookings ---");
        TablePrinter.printBookings(bookingService.getAll());
    }
}
