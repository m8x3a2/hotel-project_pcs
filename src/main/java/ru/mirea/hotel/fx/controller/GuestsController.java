package ru.mirea.hotel.fx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.fx.util.FxCells;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;

import java.time.LocalDate;

/**
 * Вкладка "Гости" (guests-tab.fxml): список гостей и бронирования выбранного гостя (связанные данные).
 */
public class GuestsController {

    @FXML
    private TableView<Guest> table;
    @FXML
    private TableView<Booking> bookingsTable;
    @FXML
    private TableColumn<Booking, LocalDate> checkInColumn;
    @FXML
    private TableColumn<Booking, LocalDate> checkOutColumn;
    @FXML
    private TableColumn<Booking, Double> priceColumn;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Label bookingsTitle;

    private final GuestService guestService = AppContext.getGuestService();
    private final BookingService bookingService = AppContext.getBookingService();

    private final ObservableList<Guest> guests = FXCollections.observableArrayList();
    private final ObservableList<Booking> guestBookings = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        table.setItems(guests);
        bookingsTable.setItems(guestBookings);
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

    public void refresh() {
        FxDialogs.run(owner(), () -> guests.setAll(guestService.getAll()));
    }

    @FXML
    private void onAdd() {
        if (FxDialogs.openForm(owner(), "guest-dialog.fxml", "Новый гость",
                (GuestDialogController form) -> form.setGuest(null))) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Guest selected = getSelected();
        if (selected != null && FxDialogs.openForm(owner(), "guest-dialog.fxml", "Изменение гостя",
                (GuestDialogController form) -> form.setGuest(selected))) {
            refresh();
        }
    }

    @FXML
    private void onDelete() {
        Guest selected = getSelected();
        if (selected != null && FxDialogs.confirm(owner(), "Удалить гостя «" + selected.getFullName() + "»?")) {
            FxDialogs.run(owner(), () -> {
                guestService.delete(selected.getId()); // если у гостя есть брони - сервис не даст удалить
                refresh();
            });
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void showBookings(Guest selected) {
        editButton.setDisable(selected == null);
        deleteButton.setDisable(selected == null);
        if (selected == null) {
            bookingsTitle.setText("Бронирования гостя (выберите гостя в таблице)");
            guestBookings.clear();
            return;
        }
        bookingsTitle.setText("Бронирования гостя: " + selected.getFullName());
        FxDialogs.run(owner(), () -> guestBookings.setAll(bookingService.getByGuestId(selected.getId())));
    }

    private Guest getSelected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
