package ru.mirea.hotel.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.fx.util.FxDialogs;

import java.sql.SQLException;

/**
 * Общий родитель контроллеров всех форм (гость, номер, бронирование).
 * Метод save() одинаковый: проверить и сохранить, а при ошибке показать её в красной надписи errorLabel.
 * Что именно сохранять - решает наследник в методе doSave() (полиморфизм, как BaseMenu в КР1).
 */
public abstract class FormController {

    // Надпись для ошибок. Она есть в каждом FXML-файле формы с fx:id="errorLabel"
    @FXML
    protected Label errorLabel;

    /** Вызывается при нажатии "Сохранить". Возвращает true, если всё сохранилось и окно можно закрыть. */
    public boolean save() {
        errorLabel.setText("");
        try {
            doSave();
            return true;
        } catch (SQLException | RuntimeException e) {
            errorLabel.setText(FxDialogs.toMessage(e)); // например: "Телефон должен состоять из цифр..."
            return false;
        }
    }

    /** Прочитать поля формы и вызвать нужный метод сервиса. Бизнес-правила проверяет сервис. */
    protected abstract void doSave() throws SQLException;

    /** Читает целое число из поля. Если там не число - подсвечивает поле красным и сообщает, что исправить. */
    protected int readInt(TextField field, String fieldName) {
        field.getStyleClass().remove("field-error");
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            field.getStyleClass().add("field-error");
            throw new BusinessException("Поле «" + fieldName + "»: введите целое число.");
        }
    }

    /** Читает дробное число (можно через запятую: 2500,50). */
    protected double readDouble(TextField field, String fieldName) {
        field.getStyleClass().remove("field-error");
        try {
            return Double.parseDouble(field.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            field.getStyleClass().add("field-error");
            throw new BusinessException("Поле «" + fieldName + "»: введите число, например 2500 или 2500,50.");
        }
    }
}
