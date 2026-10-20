package ru.mirea.hotel.fx;

import ru.mirea.hotel.repository.BookingRepository;
import ru.mirea.hotel.repository.GuestRepository;
import ru.mirea.hotel.repository.RoomRepository;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;

/**
 * Здесь один раз создаются репозитории и сервисы из КР1 - так же, как в Main.java консольной версии.
 * Контроллеры JavaFX создаёт сам FXMLLoader, поэтому сервисы они берут отсюда: AppContext.getBookingService().
 */
public final class AppContext {

    private static final GuestRepository guestRepository = new GuestRepository();
    private static final RoomRepository roomRepository = new RoomRepository();
    private static final BookingRepository bookingRepository = new BookingRepository();

    private static final GuestService guestService = new GuestService(guestRepository, bookingRepository);
    private static final RoomService roomService = new RoomService(roomRepository, bookingRepository);
    private static final BookingService bookingService =
            new BookingService(bookingRepository, guestRepository, roomRepository);

    private AppContext() {
        // объект этого класса не нужен - только статические методы
    }

    public static GuestService getGuestService() {
        return guestService;
    }

    public static RoomService getRoomService() {
        return roomService;
    }

    public static BookingService getBookingService() {
        return bookingService;
    }
}
