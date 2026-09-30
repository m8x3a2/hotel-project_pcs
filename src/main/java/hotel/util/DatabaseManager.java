package hotel.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Отвечает за подключение к базе данных PostgreSQL.
 * ВАЖНО: перед запуском программы поменяйте значения URL, USER и PASSWORD
 * на свои (адрес базы данных, имя пользователя и пароль от PostgreSQL).
 */
public class DatabaseManager {

    // ===== Настройки подключения к базе данных =====
    private static final String URL = "jdbc:postgresql://localhost:5432/hotel_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "password";

    /**
     * Возвращает новое соединение с базой данных.
     * Каждый repository открывает своё соединение и закрывает его
     * через try-with-resources сразу после использования.
     */
    public static Connection getConnection() throws SQLException {
        // Значения можно переопределить при запуске, не меняя код:
        // java -Ddb.url=jdbc:postgresql://localhost:5432/other_db -Ddb.user=... -Ddb.password=... -jar ...
        return DriverManager.getConnection(
                System.getProperty("db.url", URL),
                System.getProperty("db.user", USER),
                System.getProperty("db.password", PASSWORD));
    }
}
