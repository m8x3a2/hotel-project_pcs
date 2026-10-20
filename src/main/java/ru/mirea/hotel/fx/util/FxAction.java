package ru.mirea.hotel.fx.util;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Действие, которое может завершиться ошибкой базы или файла.
 * Передаётся лямбдой в FxDialogs.run(...): FxDialogs.run(owner, () -> bookingService.delete(id));
 */
@FunctionalInterface
public interface FxAction {

    void run() throws SQLException, IOException;
}
