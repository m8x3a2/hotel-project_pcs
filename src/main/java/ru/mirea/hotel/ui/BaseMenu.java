package ru.mirea.hotel.ui;

import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.exception.EntityNotFoundException;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Общий родитель всех меню (абстрактный класс - от него нельзя создать объект, только наследоваться).
 *
 * Метод run() одинаковый для всех меню: напечатать пункты -> прочитать выбор -> выполнить.
 * А вот заголовок, пункты и действия у каждого меню свои - их задают наследники,
 * переопределяя абстрактные методы. Это и есть полиморфизм.
 */
public abstract class BaseMenu {

    protected final ConsoleInput input;

    protected BaseMenu(ConsoleInput input) {
        this.input = input;
    }

    /** Заголовок меню. */
    protected abstract String getTitle();

    /** Пункты меню (без пункта 0). */
    protected abstract String[] getItems();

    /** Что сделать, если выбран пункт с номером choice (от 1 до количества пунктов). */
    protected abstract void handle(int choice) throws SQLException, IOException;

    /** Название пункта 0. В главном меню переопределено на "Выход". */
    protected String getExitItem() {
        return "Назад";
    }

    /** Цикл меню: работает, пока пользователь не выберет 0. */
    public void run() {
        while (true) {
            System.out.println();
            System.out.println("==================== " + getTitle() + " ====================");
            String[] items = getItems();
            for (int i = 0; i < items.length; i++) {
                System.out.println((i + 1) + ". " + items[i]);
            }
            System.out.println("0. " + getExitItem());

            int choice = input.readInt("Выберите действие: ", 0, items.length);
            if (choice == 0) {
                return;
            }
            execute(() -> handle(choice));
        }
    }

    /**
     * Выполняет действие и перехватывает ошибки, чтобы программа не завершалась аварийно.
     * После ошибки пользователь просто возвращается в меню.
     */
    protected void execute(MenuAction action) {
        try {
            action.run();
        } catch (EntityNotFoundException e) {
            System.out.println("Не найдено: " + e.getMessage());
        } catch (BusinessException e) {
            System.out.println("Ошибка: " + e.getMessage());
        } catch (SQLException e) {
            // По стандарту SQL коды ошибок, начинающиеся с "08", означают проблемы с соединением
            if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                System.out.println("Ошибка подключения к базе данных: " + e.getMessage());
            } else {
                System.out.println("Ошибка выполнения SQL-запроса: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Ошибка работы с файлом: " + e.getMessage());
        }
    }
}
