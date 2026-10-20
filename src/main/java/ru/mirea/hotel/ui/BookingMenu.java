package ru.mirea.hotel.ui;

import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.BookingStatus;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Меню "Бронирования" (основная сущность): CRUD и смена статуса.
 * Также здесь методы поиска, фильтрации и сортировки - их вызывает главное меню.
 */
public class BookingMenu extends BaseMenu {

    private final BookingService bookingService;
    private final GuestService guestService;
    private final RoomService roomService;

    public BookingMenu(ConsoleInput input, BookingService bookingService, GuestService guestService,
                       RoomService roomService) {
        super(input);
        this.bookingService = bookingService;
        this.guestService = guestService;
        this.roomService = roomService;
    }

    @Override
    protected String getTitle() {
        return "БРОНИРОВАНИЯ";
    }

    @Override
    protected String[] getItems() {
        return new String[]{
                "Создать бронирование",
                "Показать все бронирования",
                "Найти бронирование по ID",
                "Изменить бронирование (номер, даты, гости)",
                "Изменить статус бронирования",
                "Удалить бронирование"
        };
    }

    @Override
    protected void handle(int choice) throws SQLException {
        switch (choice) {
            case 1 -> createBooking();
            case 2 -> TablePrinter.printBookings(bookingService.getAll());
            case 3 -> System.out.println(bookingService.getById(input.readInt("Введите ID бронирования: ")));
            case 4 -> updateBooking();
            case 5 -> changeStatus();
            case 6 -> deleteBooking();
        }
    }

    private void createBooking() throws SQLException {
        TablePrinter.printGuests(guestService.getAll());
        int guestId = input.readInt("ID гостя: ");
        LocalDate checkIn = input.readDate("Дата заезда (ГГГГ-ММ-ДД): ");
        LocalDate checkOut = input.readDate("Дата выезда (ГГГГ-ММ-ДД): ");

        // Подсказка пользователю: какие номера свободны на эти даты
        System.out.println("Свободные номера на эти даты:");
        TablePrinter.printRooms(roomService.findFreeRooms(checkIn, checkOut));
        int roomId = input.readInt("ID номера (колонка ID): ");
        int guestsCount = input.readInt("Количество гостей: ");

        Booking booking = bookingService.create(guestId, roomId, checkIn, checkOut, guestsCount);
        System.out.println("Бронирование создано!");
        System.out.println(booking);
    }

    private void updateBooking() throws SQLException {
        int id = input.readInt("Введите ID бронирования: ");
        Booking booking = bookingService.getEditableById(id); // сразу проверяем, можно ли его менять
        System.out.println(booking);
        System.out.println("Введите новые значения (Enter - оставить как есть):");
        int roomId = input.readInt("ID номера", booking.getRoomId());
        LocalDate checkIn = input.readDate("Дата заезда", booking.getCheckIn());
        LocalDate checkOut = input.readDate("Дата выезда", booking.getCheckOut());
        int guestsCount = input.readInt("Количество гостей", booking.getGuestsCount());

        Booking updated = bookingService.update(id, roomId, checkIn, checkOut, guestsCount);
        System.out.println("Бронирование обновлено:");
        System.out.println(updated);
    }

    private void changeStatus() throws SQLException {
        int id = input.readInt("Введите ID бронирования: ");
        Booking booking = bookingService.getById(id);
        System.out.println("Текущий статус: " + booking.getStatus());
        BookingStatus newStatus = input.choose("Новый статус:", BookingStatus.values());
        bookingService.changeStatus(id, newStatus);
        System.out.println("Статус изменён на «" + newStatus + "».");
    }

    private void deleteBooking() throws SQLException {
        int id = input.readInt("Введите ID бронирования: ");
        System.out.println(bookingService.getById(id));
        if (input.confirm("Удалить это бронирование?")) {
            bookingService.delete(id);
            System.out.println("Бронирование удалено.");
        } else {
            System.out.println("Удаление отменено.");
        }
    }

    // ===== Поиск, фильтрация, сортировка (вызываются из главного меню) =====

    public void search() throws SQLException {
        int type = input.readInt("Искать: 1 - по ФИО гостя, 2 - по номеру комнаты: ", 1, 2);
        List<Booking> result;
        if (type == 1) {
            result = bookingService.searchByGuestName(input.readLine("Введите часть ФИО: "));
        } else {
            result = bookingService.searchByRoomNumber(input.readInt("Введите номер комнаты: "));
        }
        TablePrinter.printBookings(result);
    }

    public void filter() throws SQLException {
        int type = input.readInt("Фильтр: 1 - по статусу, 2 - по типу номера, 3 - по периоду заезда: ", 1, 3);
        List<Booking> result;
        if (type == 1) {
            BookingStatus status = input.choose("Статус:", BookingStatus.values());
            result = bookingService.filterByStatus(status);
        } else if (type == 2) {
            RoomType roomType = input.choose("Тип номера:", RoomType.values());
            result = bookingService.filterByRoomType(roomType);
        } else {
            LocalDate from = input.readDate("Заезд с (ГГГГ-ММ-ДД): ");
            LocalDate to = input.readDate("Заезд по (ГГГГ-ММ-ДД): ");
            result = bookingService.filterByCheckInPeriod(from, to);
        }
        TablePrinter.printBookings(result);
    }

    public void sort() throws SQLException {
        int type = input.readInt("Сортировать: 1 - по дате заезда, 2 - по стоимости (убывание), "
                + "3 - по ФИО гостя: ", 1, 3);
        List<Booking> result = switch (type) {
            case 1 -> bookingService.sortByCheckIn();
            case 2 -> bookingService.sortByPriceDesc();
            default -> bookingService.sortByGuestName();
        };
        TablePrinter.printBookings(result);
    }
}
