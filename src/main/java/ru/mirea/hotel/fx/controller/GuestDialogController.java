package ru.mirea.hotel.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import ru.mirea.hotel.fx.AppContext;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.service.GuestService;

import java.sql.SQLException;

/**
 * Контроллер формы гостя (guest-dialog.fxml). Одна форма и для добавления, и для изменения.
 */
public class GuestDialogController extends FormController {

    // @FXML - поле связывается с элементом из FXML, у которого такой же fx:id
    @FXML
    private TextField fullNameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;

    private final GuestService guestService = AppContext.getGuestService();
    private Guest guest; // null - добавляем нового гостя, иначе - изменяем этого

    /** Передать гостя для изменения: форма откроется с его текущими данными. */
    public void setGuest(Guest guest) {
        this.guest = guest;
        if (guest != null) {
            fullNameField.setText(guest.getFullName());
            phoneField.setText(guest.getPhone());
            emailField.setText(guest.getEmail());
        }
    }

    @Override
    protected void doSave() throws SQLException {
        if (guest == null) {
            guestService.create(fullNameField.getText(), phoneField.getText(), emailField.getText());
        } else {
            guestService.update(guest.getId(), fullNameField.getText(), phoneField.getText(), emailField.getText());
        }
    }
}
