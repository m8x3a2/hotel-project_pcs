package hotel.repository;

import hotel.model.User;
import hotel.util.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий гостей. Все SQL-запросы находятся только здесь -
 * ни в сервисном слое, ни в консольном меню SQL не используется.
 * Все запросы параметризованы (PreparedStatement), что защищает от SQL-инъекций.
 */
public class UserRepository implements CrudRepository<User> {

    @Override
    public User save(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, phone, email, passport_number) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, user.getFullName());
            statement.setString(2, user.getPhone());
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getPassportNumber());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            return user;
        }
    }

    @Override
    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY id";
        List<User> users = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                users.add(mapRow(resultSet));
            }
        }
        return users;
    }

    @Override
    public Optional<User> findById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, phone = ?, email = ?, passport_number = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, user.getFullName());
            statement.setString(2, user.getPhone());
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getPassportNumber());
            statement.setInt(5, user.getId());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /** Преобразует одну строку ResultSet в объект User. */
    private User mapRow(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getInt("id"),
                resultSet.getString("full_name"),
                resultSet.getString("phone"),
                resultSet.getString("email"),
                resultSet.getString("passport_number")
        );
    }
}
