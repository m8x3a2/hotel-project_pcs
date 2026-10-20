package ru.mirea.hotel.ui;

import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;

import java.sql.SQLException;

/**
 * Меню "Гости": добавление, просмотр, изменение и удаление гостей.
 * Здесь нет SQL и нет бизнес-правил - только ввод/вывод и вызов методов сервиса.
 */
public class GuestMenu extends BaseMenu {

    private final GuestService guestService;
    private final BookingService bookingService;

    public GuestMenu(ConsoleInput input, GuestService guestService, BookingService bookingService) {
        super(input); // вызываем конструктор родителя BaseMenu
        this.guestService = guestService;
        this.bookingService = bookingService;
    }

    @Override
    protected String getTitle() {
        return "ГОСТИ";
    }

    @Override
    protected String[] getItems() {
        return new String[]{
                "Добавить гостя",
                "Показать всех гостей",
                "Найти гостя по ID",
                "Изменить данные гостя",
                "Удалить гостя"
        };
    }

    @Override
    protected void handle(int choice) throws SQLException {
        switch (choice) {
            case 1 -> addGuest();
            case 2 -> TablePrinter.printGuests(guestService.getAll());
            case 3 -> showGuest();
            case 4 -> updateGuest();
            case 5 -> deleteGuest();
        }
    }

    private void addGuest() throws SQLException {
        String fullName = input.readLine("ФИО: ");
        String phone = input.readLine("Телефон: ");
        String email = input.readLine("Email: ");
        Guest guest = guestService.create(fullName, phone, email);
        System.out.println("Гость добавлен: " + guest);
    }

    /** Показывает гостя и все его бронирования (связанные данные). */
    private void showGuest() throws SQLException {
        int id = input.readInt("Введите ID гостя: ");
        Guest guest = guestService.getById(id);
        System.out.println(guest);
        System.out.println("Бронирования гостя:");
        TablePrinter.printBookings(bookingService.getByGuestId(id));
    }

    private void updateGuest() throws SQLException {
        int id = input.readInt("Введите ID гостя: ");
        Guest guest = guestService.getById(id);
        System.out.println("Текущие данные: " + guest);
        System.out.println("Введите новые значения (Enter - оставить как есть):");
        String fullName = input.readLine("ФИО", guest.getFullName());
        String phone = input.readLine("Телефон", guest.getPhone());
        String email = input.readLine("Email", guest.getEmail());
        guestService.update(id, fullName, phone, email);
        System.out.println("Данные гостя обновлены.");
    }

    private void deleteGuest() throws SQLException {
        int id = input.readInt("Введите ID гостя: ");
        Guest guest = guestService.getById(id);
        System.out.println(guest);
        if (input.confirm("Удалить этого гостя?")) {
            guestService.delete(id);
            System.out.println("Гость удалён.");
        } else {
            System.out.println("Удаление отменено.");
        }
    }
}
