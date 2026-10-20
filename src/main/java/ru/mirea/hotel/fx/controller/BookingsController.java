package ru.mirea.hotel.fx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.fx.util.FxCells;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.BookingStatus;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.service.BookingService;
import ru.mirea.hotel.service.GuestService;
import ru.mirea.hotel.service.RoomService;
import ru.mirea.hotel.util.ExcelExporter;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Вкладка "Бронирования" (bookings-tab.fxml): таблица, CRUD, смена статуса, поиск, фильтры, сортировка, экспорт.
 * Контроллер только принимает действия пользователя и вызывает сервисы. SQL здесь нет.
 */
public class BookingsController {

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<BookingStatus> statusFilter;
    @FXML
    private ComboBox<RoomType> typeFilter;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private ComboBox<String> sortBox;

    @FXML
    private TableView<Booking> table;
    @FXML
    private TableColumn<Booking, String> guestColumn;
    @FXML
    private TableColumn<Booking, LocalDate> checkInColumn;
    @FXML
    private TableColumn<Booking, LocalDate> checkOutColumn;
    @FXML
    private TableColumn<Booking, Double> priceColumn;

    @FXML
    private Button editButton;
    @FXML
    private Button statusButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Label countLabel;
    @FXML
    private Label detailsLabel;

    private final BookingService bookingService = AppContext.getBookingService();
    private final GuestService guestService = AppContext.getGuestService();
    private final RoomService roomService = AppContext.getRoomService();

    // ObservableList - список, за изменениями которого "следит" таблица: изменили список -> таблица перерисовалась
    private final ObservableList<Booking> bookings = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Фильтры
        statusFilter.getItems().setAll(BookingStatus.values());
        typeFilter.getItems().setAll(RoomType.values());
        FxCells.showPromptWhenEmpty(statusFilter);
        FxCells.showPromptWhenEmpty(typeFilter);
        sortBox.getItems().setAll("По дате заезда", "По стоимости (сначала дорогие)", "По ФИО гостя");

        // Фильтрация срабатывает сразу при изменении любого поля
        searchField.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        fromPicker.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        toPicker.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        sortBox.valueProperty().addListener((obs, oldValue, newValue) -> applySort());

        // Таблица. SortedList - отсортированный "вид" списка bookings; порядок берётся из таблицы
        // (по какому столбцу кликнули). Поэтому сортировка сохраняется и после обновления данных.
        FxCells.dateColumn(checkInColumn);
        FxCells.dateColumn(checkOutColumn);
        FxCells.moneyColumn(priceColumn);
        FxCells.colorByStatus(table);
        SortedList<Booking> sorted = new SortedList<>(bookings);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        // При выборе строки показываем связанные данные и включаем кнопки
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> showDetails(selected));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && getSelected() != null) {
                onEdit(); // двойной клик - изменить
            }
        });

        showDetails(null);
        refresh();
    }

    /** Перечитать данные из базы с учётом поиска и фильтров. */
    public void refresh() {
        FxDialogs.run(owner(), () -> {
            List<Booking> result = bookingService.search(searchField.getText(), statusFilter.getValue(),
                    typeFilter.getValue(), fromPicker.getValue(), toPicker.getValue());
            bookings.setAll(result);
            countLabel.setText("Найдено бронирований: " + result.size());
        });
    }

    // ---------------------- Кнопки ----------------------

    @FXML
    private void onAdd() {
        boolean saved = FxDialogs.openForm(owner(), "booking-dialog.fxml", "Новое бронирование",
                (BookingDialogController form) -> form.setBooking(null));
        if (saved) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Booking selected = getSelected();
        if (selected == null) {
            return;
        }
        // Сразу проверяем правило "менять можно только активное бронирование" (сервис бросит исключение)
        FxDialogs.run(owner(), () -> {
            Booking editable = bookingService.getEditableById(selected.getId());
            boolean saved = FxDialogs.openForm(owner(), "booking-dialog.fxml", "Изменение бронирования №" + editable.getId(),
                    (BookingDialogController form) -> form.setBooking(editable));
            if (saved) {
                refresh();
            }
        });
    }

    @FXML
    private void onChangeStatus() {
        Booking selected = getSelected();
        if (selected == null) {
            return;
        }
        // Предлагаем только разрешённые статусы (правило переходов из enum BookingStatus)
        List<BookingStatus> allowed = Arrays.stream(BookingStatus.values())
                .filter(status -> selected.getStatus().canChangeTo(status))
                .toList();
        if (allowed.isEmpty()) {
            FxDialogs.info(owner(), "Бронирование в статусе «" + selected.getStatus() + "» изменить уже нельзя.");
            return;
        }
        FxDialogs.choose(owner(), "Смена статуса", "Бронирование №" + selected.getId()
                        + ", сейчас: «" + selected.getStatus() + "». Новый статус:", allowed)
                .ifPresent(newStatus -> FxDialogs.run(owner(), () -> {
                    bookingService.changeStatus(selected.getId(), newStatus);
                    refresh();
                }));
    }

    @FXML
    private void onDelete() {
        Booking selected = getSelected();
        if (selected == null) {
            return;
        }
        String question = "Удалить бронирование №" + selected.getId() + " (" + selected.getGuestName()
                + ", номер " + selected.getRoomNumber() + ")?";
        if (FxDialogs.confirm(owner(), question)) {
            FxDialogs.run(owner(), () -> {
                bookingService.delete(selected.getId());
                refresh();
            });
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onResetFilters() {
        searchField.clear();
        statusFilter.setValue(null);
        typeFilter.setValue(null);
        fromPicker.setValue(null);
        toPicker.setValue(null);
    }

    /** Экспорт текущего набора данных (то, что сейчас видно в таблице) в Excel. */
    @FXML
    private void onExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить в Excel");
        chooser.setInitialFileName("hotel_export.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(owner()); // путь выбирает пользователь
        if (file == null) {
            return; // нажали "Отмена"
        }
        FxDialogs.run(owner(), () -> {
            List<Booking> visible = new ArrayList<>(table.getItems());
            ExcelExporter.export(file.getPath(), guestService.getAll(), roomService.getAll(), visible);
            FxDialogs.info(owner(), "Экспортировано бронирований: " + visible.size() + "\nФайл: " + file.getPath());
        });
    }

    // ---------------------- Вспомогательные методы ----------------------

    /** Сортировка из выпадающего списка: задаём таблице столбец и направление сортировки. */
    private void applySort() {
        String choice = sortBox.getValue();
        if (choice == null) {
            return;
        }
        TableColumn<Booking, ?> column;
        TableColumn.SortType type = TableColumn.SortType.ASCENDING;
        if (choice.startsWith("По дате")) {
            column = checkInColumn;
        } else if (choice.startsWith("По стоимости")) {
            column = priceColumn;
            type = TableColumn.SortType.DESCENDING;
        } else {
            column = guestColumn;
        }
        column.setSortType(type);
        table.getSortOrder().setAll(column);
    }

    /** Нижняя панель: данные гостя и номера выбранного бронирования (просмотр связанных данных). */
    private void showDetails(Booking selected) {
        boolean none = selected == null;
        editButton.setDisable(none);
        statusButton.setDisable(none);
        deleteButton.setDisable(none);
        if (none) {
            detailsLabel.setText("Выберите бронирование в таблице, чтобы увидеть данные гостя и номера.");
            return;
        }
        FxDialogs.run(owner(), () -> {
            Guest guest = guestService.getById(selected.getGuestId());
            Room room = roomService.getById(selected.getRoomId());
            detailsLabel.setText("Гость: " + guest.getFullName() + ", тел. " + guest.getPhone() + ", " + guest.getEmail()
                    + "\nНомер " + room.getRoomNumber() + ": " + room.getType() + ", мест: " + room.getCapacity()
                    + ", " + FxCells.money(room.getPricePerNight()) + " за ночь"
                    + "\nПроживание: " + selected.getNights() + " ноч., гостей: " + selected.getGuestsCount()
                    + ", итого " + FxCells.money(selected.getTotalPrice()));
        });
    }

    private Booking getSelected() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** Окно, в котором находится таблица (нужно, чтобы диалоги открывались поверх него). */
    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
