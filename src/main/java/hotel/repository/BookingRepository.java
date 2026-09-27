package hotel.repository;

import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.RoomType;
import hotel.util.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий бронирований. Все SQL-запросы находятся только здесь.
 * Поиск, фильтрация и сортировка выполняются на уровне сервиса через Stream API,
 * а здесь только "сырые" операции с базой данных.
 */
public class BookingRepository implements CrudRepository<Booking> {

    @Override
    public Booking save(Booking booking) throws SQLException {
        String sql = "INSERT INTO bookings (user_id, room_number, room_type, check_in_date, " +
                "check_out_date, status, total_price) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, booking.getUserId());
            statement.setInt(2, booking.getRoomNumber());
            statement.setString(3, booking.getRoomType().name());
            statement.setDate(4, Date.valueOf(booking.getCheckInDate()));
            statement.setDate(5, Date.valueOf(booking.getCheckOutDate()));
            statement.setString(6, booking.getStatus().name());
            statement.setDouble(7, booking.getTotalPrice());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    booking.setId(keys.getInt(1));
                }
            }
            return booking;
        }
    }

    @Override
    public List<Booking> findAll() throws SQLException {
        String sql = "SELECT * FROM bookings ORDER BY id";
        List<Booking> bookings = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                bookings.add(mapRow(resultSet));
            }
        }
        return bookings;
    }

    @Override
    public Optional<Booking> findById(int id) throws SQLException {
        String sql = "SELECT * FROM bookings WHERE id = ?";
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
    public void update(Booking booking) throws SQLException {
        String sql = "UPDATE bookings SET user_id = ?, room_number = ?, room_type = ?, " +
                "check_in_date = ?, check_out_date = ?, status = ?, total_price = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, booking.getUserId());
            statement.setInt(2, booking.getRoomNumber());
            statement.setString(3, booking.getRoomType().name());
            statement.setDate(4, Date.valueOf(booking.getCheckInDate()));
            statement.setDate(5, Date.valueOf(booking.getCheckOutDate()));
            statement.setString(6, booking.getStatus().name());
            statement.setDouble(7, booking.getTotalPrice());
            statement.setInt(8, booking.getId());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM bookings WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /** Преобразует одну строку ResultSet в объект Booking. */
    private Booking mapRow(ResultSet resultSet) throws SQLException {
        return new Booking(
                resultSet.getInt("id"),
                resultSet.getInt("user_id"),
                resultSet.getInt("room_number"),
                RoomType.valueOf(resultSet.getString("room_type")),
                resultSet.getDate("check_in_date").toLocalDate(),
                resultSet.getDate("check_out_date").toLocalDate(),
                BookingStatus.valueOf(resultSet.getString("status")),
                resultSet.getDouble("total_price"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
