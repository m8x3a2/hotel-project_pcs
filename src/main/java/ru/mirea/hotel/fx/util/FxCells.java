package ru.mirea.hotel.fx.util;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import ru.mirea.hotel.model.Booking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Общие настройки таблиц и списков, которые нужны в нескольких контроллерах.
 *
 * CellFactory отвечает за то, КАК значение показывается в ячейке. Само значение в ячейке остаётся
 * прежним (LocalDate, Double), поэтому сортировка по столбцу работает правильно - по датам и числам, а не по тексту.
 */
public final class FxCells {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FxCells() {
    }

    /** Деньги в виде "22 500,00 руб.". */
    public static String money(double value) {
        return String.format("%,.2f руб.", value);
    }

    /** Столбец с датой показывает её как 20.10.2026. */
    public static <S> void dateColumn(TableColumn<S, LocalDate> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : DATE_FORMAT.format(value));
            }
        });
    }

    /** Столбец с деньгами: формат "2 500,00 руб." и выравнивание по правому краю (класс money-cell в CSS). */
    public static <S> void moneyColumn(TableColumn<S, Double> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value));
            }
        });
        column.getStyleClass().add("money-cell");
    }

    /**
     * Строки бронирований окрашиваются по статусу: к строке добавляется CSS-класс
     * status-created / status-confirmed / status-completed / status-cancelled (цвета заданы в app.css).
     */
    public static void colorByStatus(TableView<Booking> table) {
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(Booking booking, boolean empty) {
                super.updateItem(booking, empty);
                getStyleClass().removeAll("status-created", "status-confirmed", "status-completed", "status-cancelled");
                if (!empty && booking != null) {
                    getStyleClass().add("status-" + booking.getStatus().name().toLowerCase());
                }
            }
        });
    }

    /**
     * Фильтр-ComboBox: когда значение сброшено в null, показываем promptText ("Все статусы").
     * Без этого JavaFX после сброса показывает пустое поле (известная особенность ComboBox).
     * buttonCell - это то, что видно в закрытом ComboBox.
     */
    public static <T> void showPromptWhenEmpty(ComboBox<T> box) {
        box.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? box.getPromptText() : item.toString());
            }
        });
    }
}
