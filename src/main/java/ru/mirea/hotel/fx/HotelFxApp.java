package ru.mirea.hotel.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.mirea.hotel.fx.util.FxDialogs;
import ru.mirea.hotel.util.DatabaseManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Главный класс JavaFX-приложения (КР2).
 *
 * Stage - это окно, Scene - содержимое окна, Node - любой элемент внутри (кнопка, таблица, панель).
 * Интерфейс главного окна описан в main.fxml, а логика - в контроллерах (пакет fx.controller).
 */
public class HotelFxApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Любая непредвиденная ошибка показывается окном, а не stack trace в консоли
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) ->
                FxDialogs.error(null, "Непредвиденная ошибка: " + error.getMessage()));

        // Сначала проверяем подключение к базе данных
        try (Connection connection = DatabaseManager.getConnection()) {
            System.out.println("Подключение к БД: " + connection.getMetaData().getURL());
        } catch (SQLException e) {
            FxDialogs.error(null, FxDialogs.toMessage(e));
            Platform.exit();
            return;
        }

        // Загружаем интерфейс из FXML. FXMLLoader сам создаст контроллер, указанный в fx:controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1280, 780);
        stage.setTitle("Гостиница — бронирование номеров");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args); // запускает JavaFX: создаёт Stage и вызывает start()
    }
}
