package ru.mirea.hotel.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * Контроллер главного окна (main.fxml). Каждая вкладка - отдельный FXML-файл со своим контроллером,
 * он подключается через fx:include. Здесь только переключение вкладок.
 */
public class MainController {

    @FXML
    private TabPane tabPane;
    @FXML
    private Tab bookingsTab;
    @FXML
    private Tab guestsTab;
    @FXML
    private Tab roomsTab;
    @FXML
    private Tab statisticsTab;

    // Контроллеры вложенных FXML. Правило имени: fx:id из fx:include + "Controller"
    @FXML
    private BookingsController bookingsViewController;
    @FXML
    private GuestsController guestsViewController;
    @FXML
    private RoomsController roomsViewController;
    @FXML
    private StatisticsController statisticsViewController;

    @FXML
    private void initialize() {
        // При переходе на вкладку обновляем её данные: они могли измениться на другой вкладке
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == bookingsTab) {
                bookingsViewController.refresh();
            } else if (newTab == guestsTab) {
                guestsViewController.refresh();
            } else if (newTab == roomsTab) {
                roomsViewController.refresh();
            } else if (newTab == statisticsTab) {
                statisticsViewController.refresh();
            }
        });
    }
}
