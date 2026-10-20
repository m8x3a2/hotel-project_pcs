package ru.mirea.hotel.fx.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.exception.EntityNotFoundException;
import ru.mirea.hotel.fx.controller.FormController;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Все диалоговые окна приложения в одном месте, чтобы не повторять один и тот же код в каждом контроллере:
 * ошибка, информация, подтверждение, выбор из списка и окно-форма из FXML.
 */
public final class FxDialogs {

    /** Путь к общему файлу стилей - подключаем его и к диалогам, у них своё отдельное окно. */
    public static final String CSS = FxDialogs.class.getResource("/ru/mirea/hotel/fx/app.css").toExternalForm();

    // Свои кнопки с русскими надписями (стандартные ButtonType.YES/CANCEL подписаны по-английски)
    private static final ButtonType YES = new ButtonType("Да", ButtonBar.ButtonData.YES);
    private static final ButtonType NO = new ButtonType("Нет", ButtonBar.ButtonData.NO);
    private static final ButtonType SAVE = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CHOOSE = new ButtonType("Выбрать", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

    private FxDialogs() {
    }

    // ---------------------- Простые окна-сообщения ----------------------

    public static void error(Window owner, String message) {
        createAlert(Alert.AlertType.ERROR, owner, "Операция не выполнена", message, ButtonType.OK).showAndWait();
    }

    public static void info(Window owner, String message) {
        createAlert(Alert.AlertType.INFORMATION, owner, null, message, ButtonType.OK).showAndWait();
    }

    /** Вопрос "Да/Нет". Возвращает true, если нажали "Да". */
    public static boolean confirm(Window owner, String message) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, owner, "Подтверждение", message, YES, NO);
        return alert.showAndWait().orElse(NO) == YES;
    }

    /** Выбор одного варианта из списка (например, нового статуса). */
    public static <T> Optional<T> choose(Window owner, String title, String header, List<T> options) {
        ChoiceDialog<T> dialog = new ChoiceDialog<>(options.get(0), options);
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().setAll(CHOOSE, CANCEL);
        dialog.getDialogPane().getStylesheets().add(CSS);
        return dialog.showAndWait();
    }

    private static Alert createAlert(Alert.AlertType type, Window owner, String header, String message,
                                     ButtonType... buttons) {
        Alert alert = new Alert(type, message, buttons);
        alert.initOwner(owner);
        alert.setTitle("Гостиница");
        alert.setHeaderText(header);
        alert.getDialogPane().getStylesheets().add(CSS);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE); // чтобы длинный текст не обрезался
        return alert;
    }

    // ---------------------- Окно-форма из FXML ----------------------

    /**
     * Открывает форму добавления/изменения, описанную в FXML (например, guest-dialog.fxml).
     * init - что сделать с контроллером до показа окна (например, передать запись для изменения).
     * При нажатии "Сохранить" вызывается controller.save(): если он вернул false (ошибка проверки),
     * окно НЕ закрывается, а пользователь видит сообщение и может исправить данные.
     *
     * @return true, если данные сохранены
     */
    public static <C extends FormController> boolean openForm(Window owner, String fxmlFile, String title,
                                                              Consumer<C> init) {
        try {
            FXMLLoader loader = new FXMLLoader(FxDialogs.class.getResource("/ru/mirea/hotel/fx/" + fxmlFile));
            DialogPane pane = loader.load();
            C controller = loader.getController();
            init.accept(controller);

            pane.getButtonTypes().setAll(SAVE, CANCEL);
            Button saveButton = (Button) pane.lookupButton(SAVE);
            saveButton.addEventFilter(ActionEvent.ACTION, event -> {
                if (!controller.save()) {
                    event.consume(); // отменяем закрытие окна
                }
            });

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle(title);
            dialog.setDialogPane(pane);
            return dialog.showAndWait().orElse(CANCEL) == SAVE;
        } catch (IOException e) {
            error(owner, "Не удалось открыть окно " + fxmlFile + ": " + e.getMessage());
            return false;
        }
    }

    // ---------------------- Обработка ошибок ----------------------

    /**
     * Выполняет действие; если произошла ошибка - показывает понятное сообщение в окне.
     * Так программа не падает и не показывает пользователю stack trace.
     */
    public static void run(Window owner, FxAction action) {
        try {
            action.run();
        } catch (SQLException | IOException | RuntimeException e) {
            error(owner, toMessage(e));
        }
    }

    /** Превращает исключение в понятный пользователю текст. */
    public static String toMessage(Exception e) {
        if (e instanceof BusinessException || e instanceof EntityNotFoundException) {
            return e.getMessage();
        }
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState() == null ? "" : sql.getSQLState();
            if (state.equals("28P01")) {
                return "Неверный логин или пароль к базе данных (см. src/main/resources/database.properties).";
            }
            if (state.equals("3D000")) {
                return "База данных не найдена. Создайте базу hotel_db и выполните sql/init.sql.";
            }
            if (state.startsWith("08")) {
                return "Нет подключения к базе данных. Проверьте, что PostgreSQL запущен.\n" + sql.getMessage();
            }
            return "Ошибка выполнения SQL-запроса: " + sql.getMessage();
        }
        if (e instanceof IOException) {
            return "Ошибка работы с файлом: " + e.getMessage();
        }
        return "Непредвиденная ошибка: " + e.getMessage();
    }
}
