package ru.mirea.hotel.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.service.RoomService;

import java.sql.SQLException;

/**
 * Контроллер формы номера (room-dialog.fxml).
 */
public class RoomDialogController extends FormController {

    @FXML
    private TextField numberField;
    @FXML
    private ComboBox<RoomType> typeBox;
    @FXML
    private TextField capacityField;
    @FXML
    private TextField priceField;

    private final RoomService roomService = AppContext.getRoomService();
    private Room room; // null - новый номер

    /** initialize() FXMLLoader вызывает сам, когда все @FXML-поля уже заполнены. */
    @FXML
    private void initialize() {
        typeBox.getItems().setAll(RoomType.values());
    }

    public void setRoom(Room room) {
        this.room = room;
        if (room != null) {
            numberField.setText(String.valueOf(room.getRoomNumber()));
            typeBox.setValue(room.getType());
            capacityField.setText(String.valueOf(room.getCapacity()));
            priceField.setText(String.valueOf(room.getPricePerNight()));
        }
    }

    @Override
    protected void doSave() throws SQLException {
        int number = readInt(numberField, "Номер комнаты");
        if (typeBox.getValue() == null) {
            throw new BusinessException("Поле «Тип номера»: выберите тип из списка.");
        }
        int capacity = readInt(capacityField, "Вместимость");
        double price = readDouble(priceField, "Цена за ночь");

        if (room == null) {
            roomService.create(number, typeBox.getValue(), capacity, price);
        } else {
            roomService.update(room.getId(), number, typeBox.getValue(), capacity, price);
        }
    }
}
