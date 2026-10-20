package ru.mirea.hotel.ui;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Действие пункта меню. Функциональный интерфейс (ровно один метод),
 * поэтому вместо отдельного класса можно передать лямбду: () -> addGuest()
 */
@FunctionalInterface
public interface MenuAction {

    void run() throws SQLException, IOException;
}
