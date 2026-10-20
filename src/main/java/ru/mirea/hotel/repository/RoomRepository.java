package ru.mirea.hotel.repository;

import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Работа с таблицей rooms через JDBC.
 */
public class RoomRepository implements CrudRepository<Room> {

    @Override
    public Room save(Room room) throws SQLException {
        String sql = "INSERT INTO rooms (room_number, room_type, capacity, price_per_night) "
                + "VALUES (?, ?, ?, ?) RETURNING id";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, room.getRoomNumber());
            statement.setString(2, room.getType().name()); // в базе enum хранится как текст: "STANDARD"
            statement.setInt(3, room.getCapacity());
            statement.setDouble(4, room.getPricePerNight());

            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                room.setId(resultSet.getInt("id"));
            }
        }
        return room;
    }

    @Override
    public List<Room> findAll() throws SQLException {
        String sql = "SELECT id, room_number, room_type, capacity, price_per_night FROM rooms ORDER BY room_number";
        List<Room> rooms = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                rooms.add(mapRow(resultSet));
            }
        }
        return rooms;
    }

    @Override
    public Optional<Room> findById(int id) throws SQLException {
        String sql = "SELECT id, room_number, room_type, capacity, price_per_night FROM rooms WHERE id = ?";

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

    /** Поиск по номеру на двери (нужен, чтобы не было двух номеров 101). */
    public Optional<Room> findByNumber(int roomNumber) throws SQLException {
        String sql = "SELECT id, room_number, room_type, capacity, price_per_night FROM rooms WHERE room_number = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public void update(Room room) throws SQLException {
        String sql = "UPDATE rooms SET room_number = ?, room_type = ?, capacity = ?, price_per_night = ? WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, room.getRoomNumber());
            statement.setString(2, room.getType().name());
            statement.setInt(3, room.getCapacity());
            statement.setDouble(4, room.getPricePerNight());
            statement.setInt(5, room.getId());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteById(int id) throws SQLException {
        String sql = "DELETE FROM rooms WHERE id = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    private Room mapRow(ResultSet resultSet) throws SQLException {
        return new Room(
                resultSet.getInt("id"),
                resultSet.getInt("room_number"),
                RoomType.valueOf(resultSet.getString("room_type")), // "STANDARD" -> RoomType.STANDARD
                resultSet.getInt("capacity"),
                resultSet.getDouble("price_per_night"));
    }
}
