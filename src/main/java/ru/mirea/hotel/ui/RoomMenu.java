package ru.mirea.hotel.ui;

import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.RoomService;

import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Меню "Номера": создание, просмотр, изменение и удаление номеров + поиск свободных номеров.
 */
public class RoomMenu extends BaseMenu {

    private final RoomService roomService;
    private final BookingService bookingService;

    public RoomMenu(ConsoleInput input, RoomService roomService, BookingService bookingService) {
        super(input);
        this.roomService = roomService;
        this.bookingService = bookingService;
    }

    @Override
    protected String getTitle() {
        return "НОМЕРА";
    }

    @Override
    protected String[] getItems() {
        return new String[]{
                "Добавить номер",
                "Показать все номера",
                "Найти номер по ID",
                "Изменить номер",
                "Удалить номер",
                "Свободные номера на даты"
        };
    }

    @Override
    protected void handle(int choice) throws SQLException {
        switch (choice) {
            case 1 -> addRoom();
            case 2 -> TablePrinter.printRooms(roomService.getAll());
            case 3 -> showRoom();
            case 4 -> updateRoom();
            case 5 -> deleteRoom();
            case 6 -> showFreeRooms();
        }
    }

    private void addRoom() throws SQLException {
        int number = input.readInt("Номер комнаты (например, 105): ");
        RoomType type = input.choose("Тип номера:", RoomType.values());
        int capacity = input.readInt("Вместимость (человек): ");
        double price = input.readDouble("Цена за ночь, руб.: ");
        Room room = roomService.create(number, type, capacity, price);
        System.out.println("Номер добавлен: " + room);
    }

    private void showRoom() throws SQLException {
        int id = input.readInt("Введите ID номера: ");
        Room room = roomService.getById(id);
        System.out.println(room);
        System.out.println("Бронирования этого номера:");
        TablePrinter.printBookings(bookingService.getByRoomId(id));
    }

    private void updateRoom() throws SQLException {
        int id = input.readInt("Введите ID номера: ");
        Room room = roomService.getById(id);
        System.out.println("Текущие данные: " + room);
        System.out.println("Введите новые значения (Enter - оставить как есть):");
        int number = input.readInt("Номер комнаты", room.getRoomNumber());
        RoomType type = input.choose("Тип номера (сейчас: " + room.getType() + "):", RoomType.values());
        int capacity = input.readInt("Вместимость", room.getCapacity());
        double price = input.readDouble("Цена за ночь", room.getPricePerNight());
        roomService.update(id, number, type, capacity, price);
        System.out.println("Номер обновлён.");
    }

    private void deleteRoom() throws SQLException {
        int id = input.readInt("Введите ID номера: ");
        Room room = roomService.getById(id);
        System.out.println(room);
        if (input.confirm("Удалить этот номер?")) {
            roomService.delete(id);
            System.out.println("Номер удалён.");
        } else {
            System.out.println("Удаление отменено.");
        }
    }

    private void showFreeRooms() throws SQLException {
        LocalDate checkIn = input.readDate("Дата заезда (ГГГГ-ММ-ДД): ");
        LocalDate checkOut = input.readDate("Дата выезда (ГГГГ-ММ-ДД): ");
        TablePrinter.printRooms(roomService.findFreeRooms(checkIn, checkOut));
    }
}
