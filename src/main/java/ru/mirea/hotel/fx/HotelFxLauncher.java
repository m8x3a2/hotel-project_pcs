package ru.mirea.hotel.fx;

import javafx.application.Application;

/**
 * Запуск JavaFX-версии из IntelliJ IDEA (зелёная кнопка рядом с main).
 *
 * Зачем отдельный класс: если запускать напрямую класс-наследник Application без модулей Java,
 * IDEA выдаёт ошибку "JavaFX runtime components are missing". Обычный класс с main такой проблемы не имеет.
 */
public class HotelFxLauncher {

    public static void main(String[] args) {
        Application.launch(HotelFxApp.class, args);
    }
}
