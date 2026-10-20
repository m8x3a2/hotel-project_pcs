package ru.mirea.hotel.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.fx.util.FxCells;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Контроллер формы бронирования (booking-dialog.fxml).
 * Гостя и номер пользователь выбирает из выпадающих списков (ComboBox), а не вводит ID вручную.
 */
public class BookingDialogController extends FormController {

    @FXML
    private ComboBox<Guest> guestBox;
    @FXML
    private ComboBox<Room> roomBox;
    @FXML
    private DatePicker checkInPicker;
    @FXML
    private DatePicker checkOutPicker;
    @FXML
    private TextField guestsField;
    @FXML
    private Label priceLabel;

    private final BookingService bookingService = AppContext.getBookingService();
    private final GuestService guestService = AppContext.getGuestService();
    private final RoomService roomService = AppContext.getRoomService();
    private Booking booking; // null - новое бронирование

    @FXML
    private void initialize() {
        // Как показывать гостя и номер в выпадающем списке
        guestBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Guest guest) {
                return guest == null ? "" : guest.getFullName() + " (" + guest.getEmail() + ")";
            }

            @Override
            public Guest fromString(String text) {
                return null; // список не редактируется вручную, обратное преобразование не нужно
            }
        });
        roomBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Room room) {
                return room == null ? "" : room.getRoomNumber() + " · " + room.getType() + " · мест: "
                        + room.getCapacity() + " · " + FxCells.money(room.getPricePerNight()) + " за ночь";
            }

            @Override
            public Room fromString(String text) {
                return null;
            }
        });

        // Загружаем гостей и номера из базы
        FxDialogs.run(null, () -> {
            guestBox.getItems().setAll(guestService.getAll());
            roomBox.getItems().setAll(roomService.getAll());
        });

        // При любом изменении номера или дат пересчитываем предварительную стоимость
        roomBox.valueProperty().addListener((obs, oldValue, newValue) -> updatePrice());
        checkInPicker.valueProperty().addListener((obs, oldValue, newValue) -> updatePrice());
        checkOutPicker.valueProperty().addListener((obs, oldValue, newValue) -> updatePrice());
        updatePrice();
    }

    /** Передать бронирование для изменения (null - создаём новое). */
    public void setBooking(Booking booking) {
        this.booking = booking;
        if (booking == null) {
            guestsField.setText("1");
            return;
        }
        guestBox.getItems().stream().filter(g -> g.getId() == booking.getGuestId()).findFirst()
                .ifPresent(guestBox::setValue);
        roomBox.getItems().stream().filter(r -> r.getId() == booking.getRoomId()).findFirst()
                .ifPresent(roomBox::setValue);
        checkInPicker.setValue(booking.getCheckIn());
        checkOutPicker.setValue(booking.getCheckOut());
        guestsField.setText(String.valueOf(booking.getGuestsCount()));
        guestBox.setDisable(true); // у существующего бронирования гостя не меняем
    }

    private void updatePrice() {
        Room room = roomBox.getValue();
        LocalDate checkIn = checkInPicker.getValue();
        LocalDate checkOut = checkOutPicker.getValue();
        if (room == null || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            priceLabel.setText("Стоимость: выберите номер и даты");
            return;
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        double price = bookingService.calculatePrice(room, checkIn, checkOut);
        priceLabel.setText("Стоимость: " + nights + " ноч. × " + FxCells.money(room.getPricePerNight())
                + " = " + FxCells.money(price));
    }

    @Override
    protected void doSave() throws SQLException {
        if (guestBox.getValue() == null) {
            throw new BusinessException("Поле «Гость»: выберите гостя из списка.");
        }
        if (roomBox.getValue() == null) {
            throw new BusinessException("Поле «Номер»: выберите номер из списка.");
        }
        int guestsCount = readInt(guestsField, "Количество гостей");
        int roomId = roomBox.getValue().getId();

        // Остальное (даты, вместимость, занятость номера) проверяет BookingService
        if (booking == null) {
            bookingService.create(guestBox.getValue().getId(), roomId, checkInPicker.getValue(),
                    checkOutPicker.getValue(), guestsCount);
        } else {
            bookingService.update(booking.getId(), roomId, checkInPicker.getValue(), checkOutPicker.getValue(),
                    guestsCount);
        }
    }
}
