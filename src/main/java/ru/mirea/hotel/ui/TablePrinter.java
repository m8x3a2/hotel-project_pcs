package ru.mirea.hotel.ui;

import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.model.Room;

import java.util.List;

/**
 * Вывод списков в консоль в виде таблиц.
 * printf: %-5s - строка шириной 5 символов, выровненная влево; %10.2f - число с 2 знаками после запятой.
 */
public class TablePrinter {

    public static void printGuests(List<Guest> guests) {
        if (guests.isEmpty()) {
            System.out.println("Гостей нет.");
            return;
        }
        String format = "%-4s %-30s %-18s %-25s%n";
        System.out.printf(format, "ID", "ФИО", "Телефон", "Email");
        System.out.println("-".repeat(80));
        for (Guest g : guests) {
            System.out.printf(format, g.getId(), g.getFullName(), g.getPhone(), g.getEmail());
        }
        System.out.println("Всего: " + guests.size());
    }

    public static void printRooms(List<Room> rooms) {
        if (rooms.isEmpty()) {
            System.out.println("Номеров нет.");
            return;
        }
        System.out.printf("%-4s %-6s %-10s %-5s %14s%n", "ID", "Номер", "Тип", "Мест", "Цена/ночь");
        System.out.println("-".repeat(43));
        for (Room r : rooms) {
            System.out.printf("%-4d %-6d %-10s %-5d %14.2f%n",
                    r.getId(), r.getRoomNumber(), r.getType(), r.getCapacity(), r.getPricePerNight());
        }
        System.out.println("Всего: " + rooms.size());
    }

    public static void printBookings(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            System.out.println("Бронирований не найдено.");
            return;
        }
        System.out.printf("%-4s %-28s %-6s %-9s %-11s %-11s %-6s %-13s %10s%n",
                "ID", "Гость", "Номер", "Тип", "Заезд", "Выезд", "Гостей", "Статус", "Сумма");
        System.out.println("-".repeat(106));
        for (Booking b : bookings) {
            System.out.printf("%-4d %-28s %-6d %-9s %-11s %-11s %-6d %-13s %10.2f%n",
                    b.getId(), b.getGuestName(), b.getRoomNumber(), b.getRoomType(), b.getCheckIn(),
                    b.getCheckOut(), b.getGuestsCount(), b.getStatus(), b.getTotalPrice());
        }
        System.out.println("Всего: " + bookings.size());
    }
}
