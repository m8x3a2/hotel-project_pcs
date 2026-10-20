package ru.mirea.hotel.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Выдаёт соединения с базой данных PostgreSQL.
 * Адрес базы, логин и пароль хранятся не в коде, а в файле src/main/resources/database.properties.
 */
public class DatabaseManager {

    private static final String SETTINGS_FILE = "database.properties";

    // Настройки читаются из файла один раз - при первом подключении
    private static Properties settings;

    /**
     * Открывает новое соединение с базой.
     * Тот, кто вызвал метод, обязан закрыть соединение (мы делаем это через try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (settings == null) {
            settings = loadSettings();
        }
        return DriverManager.getConnection(
                settings.getProperty("db.url"),
                settings.getProperty("db.user"),
                settings.getProperty("db.password"));
    }

    /** Читает database.properties из ресурсов программы. */
    private static Properties loadSettings() throws SQLException {
        Properties properties = new Properties();
        try (InputStream in = DatabaseManager.class.getClassLoader().getResourceAsStream(SETTINGS_FILE)) {
            if (in == null) {
                throw new SQLException("Не найден файл настроек " + SETTINGS_FILE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new SQLException("Не удалось прочитать файл " + SETTINGS_FILE + ": " + e.getMessage());
        }
        return properties;
    }
}
