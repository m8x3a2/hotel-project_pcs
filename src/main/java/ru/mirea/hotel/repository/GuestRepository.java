package ru.mirea.hotel.repository;

import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Работа с таблицей guests через JDBC.
 * Везде используется PreparedStatement со знаками "?" вместо склейки строк -
 * это защищает от SQL-инъекций.
 */
public class GuestRepository implements CrudRepository<Guest> {

    @Override
    public Guest save(Guest guest) throws SQLException {
        // RETURNING id - PostgreSQL сразу возвращает id, который он выдал новой строке
        String sql = "INSERT INTO guests (full_name, phone, email) VALUES (?, ?, ?) RETURNING id";

        // try-with-resources: connection и statement закроются автоматически, даже если будет ошибка
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guest.getFullName()); // подставляем значение вместо первого "?"
            statement.setString(2, guest.getPhone());
            statement.setString(3, guest.getEmail());

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                guest.setId(resultSet.getInt("id"));
            }
        }
        return guest;
    }

    @Override
    public List<Guest> findAll() throws SQLException {
        String sql = "SELECT id, full_name, phone, email FROM guests ORDER BY id";
        List<Guest> guests = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            // ResultSet - это результат запроса; next() переходит к следующей строке
            while (resultSet.next()) {
                guests.add(mapRow(resultSet));
            }
        }
        return guests;
    }

    @Override
    public Optional<Guest> findById(int id) throws SQLException {
        String sql = "SELECT id, full_name, phone, email FROM guests WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    /** Поиск гостя по email (нужен, чтобы не допустить двух гостей с одинаковым email). */
    public Optional<Guest> findByEmail(String email) throws SQLException {
        String sql = "SELECT id, full_name, phone, email FROM guests WHERE email = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public void update(Guest guest) throws SQLException {
        String sql = "UPDATE guests SET full_name = ?, phone = ?, email = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guest.getFullName());
            statement.setString(2, guest.getPhone());
            statement.setString(3, guest.getEmail());
            statement.setInt(4, guest.getId());
            statement.executeUpdate(); // executeUpdate - для INSERT/UPDATE/DELETE без результата
        }
    }

    @Override
    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM guests WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /** Превращает текущую строку ResultSet в объект Guest. */
    private Guest mapRow(ResultSet resultSet) throws SQLException {
        return new Guest(
                resultSet.getInt("id"),
                resultSet.getString("full_name"),
                resultSet.getString("phone"),
                resultSet.getString("email"));
    }
}
