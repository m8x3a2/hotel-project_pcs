package ru.mirea.hotel.fx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.fx.util.FxCells;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.RoomService;

import java.time.LocalDate;

/**
 * Вкладка "Номера" (rooms-tab.fxml): список номеров, поиск свободных на даты и бронирования выбранного номера.
 */
public class RoomsController {

    @FXML
    private TableView<Room> table;
    @FXML
    private TableColumn<Room, Double> pricePerNightColumn;
    @FXML
    private TableView<Booking> bookingsTable;
    @FXML
    private TableColumn<Booking, LocalDate> checkInColumn;
    @FXML
    private TableColumn<Booking, LocalDate> checkOutColumn;
    @FXML
    private TableColumn<Booking, Double> priceColumn;
    @FXML
    private DatePicker freeFromPicker;
    @FXML
    private DatePicker freeToPicker;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Label listTitle;
    @FXML
    private Label bookingsTitle;

    private final RoomService roomService = AppContext.getRoomService();
    private final BookingService bookingService = AppContext.getBookingService();

    private final ObservableList<Room> rooms = FXCollections.observableArrayList();
    private final ObservableList<Booking> roomBookings = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        table.setItems(rooms);
        bookingsTable.setItems(roomBookings);
        FxCells.moneyColumn(pricePerNightColumn);
        FxCells.dateColumn(checkInColumn);
        FxCells.dateColumn(checkOutColumn);
        FxCells.moneyColumn(priceColumn);
        FxCells.colorByStatus(bookingsTable);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> showBookings(selected));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && getSelected() != null) {
                onEdit();
            }
        });
        showBookings(null);
        refresh();
    }

    /** Показать все номера. */
    public void refresh() {
        listTitle.setText("Все номера");
        FxDialogs.run(owner(), () -> rooms.setAll(roomService.getAll()));
    }

    @FXML
    private void onAdd() {
        if (FxDialogs.openForm(owner(), "room-dialog.fxml", "Новый номер",
                (RoomDialogController form) -> form.setRoom(null))) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Room selected = getSelected();
        if (selected != null && FxDialogs.openForm(owner(), "room-dialog.fxml", "Изменение номера " + selected.getRoomNumber(),
                (RoomDialogController form) -> form.setRoom(selected))) {
            refresh();
        }
    }

    @FXML
    private void onDelete() {
        Room selected = getSelected();
        if (selected != null && FxDialogs.confirm(owner(), "Удалить номер " + selected.getRoomNumber() + "?")) {
            FxDialogs.run(owner(), () -> {
                roomService.delete(selected.getId()); // если есть брони - сервис не даст удалить
                refresh();
            });
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    /** Показать только номера, свободные на выбранные даты. */
    @FXML
    private void onShowFree() {
        FxDialogs.run(owner(), () -> {
            LocalDate from = freeFromPicker.getValue();
            LocalDate to = freeToPicker.getValue();
            if (from == null || to == null) {
                throw new BusinessException("Выберите дату заезда и дату выезда.");
            }
            rooms.setAll(roomService.findFreeRooms(from, to));
            listTitle.setText("Свободные номера с " + from + " по " + to + ": " + rooms.size());
        });
    }

    private void showBookings(Room selected) {
        editButton.setDisable(selected == null);
        deleteButton.setDisable(selected == null);
        if (selected == null) {
            bookingsTitle.setText("Бронирования номера (выберите номер в таблице)");
            roomBookings.clear();
            return;
        }
        bookingsTitle.setText("Бронирования номера " + selected.getRoomNumber());
        FxDialogs.run(owner(), () -> roomBookings.setAll(bookingService.getByRoomId(selected.getId())));
    }

    private Room getSelected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
