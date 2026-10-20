package ru.mirea.hotel.fx.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.model.BookingStatus;
import ru.mirea.hotel.service.BookingService;

import java.util.Map;

/**
 * Вкладка "Статистика" (statistics-tab.fxml). Все показатели считает BookingService.getStatistics() из КР1,
 * контроллер только раскладывает их по экрану.
 */
public class StatisticsController {

    @FXML
    private GridPane statsGrid;
    @FXML
    private PieChart statusChart;

    private final BookingService bookingService = AppContext.getBookingService();

    @FXML
    private void initialize() {
        refresh();
    }

    public void refresh() {
        FxDialogs.run(null, () -> {
            // Таблица показателей: название слева, значение справа
            statsGrid.getChildren().clear();
            int row = 0;
            for (Map.Entry<String, String> entry : bookingService.getStatistics().entrySet()) {
                Label name = new Label(entry.getKey());
                name.getStyleClass().add("stat-name");
                Label value = new Label(entry.getValue());
                value.getStyleClass().add("stat-value");
                statsGrid.addRow(row++, name, value);
            }

            // Круговая диаграмма по статусам (статусы с нулём не показываем). Список данных заменяем целиком
            ObservableList<PieChart.Data> slices = FXCollections.observableArrayList();
            for (Map.Entry<BookingStatus, Long> entry : bookingService.countByStatus().entrySet()) {
                if (entry.getValue() > 0) {
                    slices.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
                }
            }
            statusChart.setData(slices);
        });
    }

    @FXML
    private void onRefresh() {
        refresh();
    }
}
