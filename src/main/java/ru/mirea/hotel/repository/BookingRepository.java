package ru.mirea.hotel.repository;

import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.BookingStatus;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.util.DatabaseManager;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Работа с таблицей bookings через JDBC.
 */
public class BookingRepository implements CrudRepository<Booking> {

    // Общая часть SELECT-запроса. JOIN "приклеивает" к бронированию строку гостя и строку номера,
    // чтобы сразу получить имя гостя и номер комнаты. """ ... """ - многострочная строка (text block).
    private static final String SELECT_WITH_JOIN = """
            SELECT b.id, b.guest_id, b.room_id, b.check_in, b.check_out, b.guests_count,
                   b.status, b.total_price, g.full_name, r.room_number, r.room_type
            FROM bookings b
            JOIN guests g ON g.id = b.guest_id
            JOIN rooms  r ON r.id = b.room_id
            """;

    @Override
    public Booking save(Booking booking) throws SQLException {
        String sql = "INSERT INTO bookings (guest_id, room_id, check_in, check_out, guests_count, status, total_price) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, booking.getGuestId());
            statement.setInt(2, booking.getRoomId());
            statement.setDate(3, Date.valueOf(booking.getCheckIn()));  // LocalDate -> java.sql.Date
            statement.setDate(4, Date.valueOf(booking.getCheckOut()));
            statement.setInt(5, booking.getGuestsCount());
            statement.setString(6, booking.getStatus().name());
            statement.setDouble(7, booking.getTotalPrice());

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                booking.setId(resultSet.getInt("id"));
            }
        }
        return booking;
    }

    @Override
    public List<Booking> findAll() throws SQLException {
        String sql = SELECT_WITH_JOIN + "ORDER BY b.id";
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
        String sql = SELECT_WITH_JOIN + "WHERE b.id = ?";

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

    @Override
    public void update(Booking booking) throws SQLException {
        // Гостя у бронирования не меняем - бронирование всегда принадлежит тому, кто его сделал
        String sql = "UPDATE bookings SET room_id = ?, check_in = ?, check_out = ?, guests_count = ?, "
                + "status = ?, total_price = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, booking.getRoomId());
            statement.setDate(2, Date.valueOf(booking.getCheckIn()));
            statement.setDate(3, Date.valueOf(booking.getCheckOut()));
            statement.setInt(4, booking.getGuestsCount());
            statement.setString(5, booking.getStatus().name());
            statement.setDouble(6, booking.getTotalPrice());
            statement.setInt(7, booking.getId());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM bookings WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    /**
     * Проверяет, занят ли номер на указанные даты другим (не отменённым) бронированием.
     * Два периода пересекаются, если старый заезд раньше нового выезда И старый выезд позже нового заезда.
     * excludeBookingId - какое бронирование не учитывать (при изменении - само себя; при создании - 0).
     */
    public boolean hasOverlap(int roomId, LocalDate checkIn, LocalDate checkOut, int excludeBookingId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings "
                + "WHERE room_id = ? AND status <> 'CANCELLED' AND id <> ? "
                + "AND check_in < ? AND check_out > ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomId);
            statement.setInt(2, excludeBookingId);
            statement.setDate(3, Date.valueOf(checkOut));
            statement.setDate(4, Date.valueOf(checkIn));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0; // getInt(1) - первая колонка результата, т.е. COUNT(*)
            }
        }
    }

    /** Сколько бронирований у гостя (нужно для запрета удаления гостя). */
    public int countByGuestId(int guestId) throws SQLException {
        return count("SELECT COUNT(*) FROM bookings WHERE guest_id = ?", guestId);
    }

    /** Сколько бронирований у номера (нужно для запрета удаления номера). */
    public int countByRoomId(int roomId) throws SQLException {
        return count("SELECT COUNT(*) FROM bookings WHERE room_id = ?", roomId);
    }

    /** Выполняет запрос вида SELECT COUNT(*) ... WHERE поле = ? и возвращает число. */
    private int count(String sql, int id) throws SQLException {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private Booking mapRow(ResultSet resultSet) throws SQLException {
        Booking booking = new Booking(
                resultSet.getInt("id"),
                resultSet.getInt("guest_id"),
                resultSet.getInt("room_id"),
                resultSet.getDate("check_in").toLocalDate(),   // java.sql.Date -> LocalDate
                resultSet.getDate("check_out").toLocalDate(),
                resultSet.getInt("guests_count"),
                BookingStatus.valueOf(resultSet.getString("status")),
                resultSet.getDouble("total_price"));
        // Поля из присоединённых (JOIN) таблиц guests и rooms
        booking.setGuestName(resultSet.getString("full_name"));
        booking.setRoomNumber(resultSet.getInt("room_number"));
        booking.setRoomType(RoomType.valueOf(resultSet.getString("room_type")));
        return booking;
    }
}
