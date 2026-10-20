package ru.mirea.hotel.service;

import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.exception.EntityNotFoundException;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.repository.BookingRepository;
import ru.mirea.hotel.repository.RoomRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Бизнес-логика для номеров гостиницы.
 */
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public Room create(int roomNumber, RoomType type, int capacity, double pricePerNight) throws SQLException {
        Room room = new Room(roomNumber, type, capacity, pricePerNight);
        validate(room);

        // Правило: номер на двери уникален
        if (roomRepository.findByNumber(roomNumber).isPresent()) {
            throw new BusinessException("Номер " + roomNumber + " уже существует.");
        }
        return roomRepository.save(room);
    }

    public List<Room> getAll() throws SQLException {
        return roomRepository.findAll();
    }

    public Room getById(int id) throws SQLException {
        return roomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Номер с ID " + id + " не найден."));
    }

    public void update(int id, int roomNumber, RoomType type, int capacity, double pricePerNight)
            throws SQLException {
        Room room = getById(id);
        room.setRoomNumber(roomNumber);
        room.setType(type);
        room.setCapacity(capacity);
        room.setPricePerNight(pricePerNight); // цена уже созданных бронирований при этом не меняется
        validate(room);

        Optional<Room> sameNumber = roomRepository.findByNumber(roomNumber);
        if (sameNumber.isPresent() && sameNumber.get().getId() != id) {
            throw new BusinessException("Номер " + roomNumber + " уже есть в гостинице.");
        }
        roomRepository.update(room);
    }

    public void delete(int id) throws SQLException {
        Room room = getById(id);

        // Правило: нельзя удалить номер, на который есть бронирования
        int bookingsCount = bookingRepository.countByRoomId(id);
        if (bookingsCount > 0) {
            throw new BusinessException("Нельзя удалить номер " + room.getRoomNumber()
                    + ": на него есть бронирования (" + bookingsCount + " шт.).");
        }
        roomRepository.deleteById(id);
    }

    /** Свободные номера на период: те, у которых нет пересекающихся бронирований. */
    public List<Room> findFreeRooms(LocalDate checkIn, LocalDate checkOut) throws SQLException {
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("Дата выезда должна быть позже даты заезда.");
        }
        List<Room> freeRooms = new ArrayList<>();
        for (Room room : roomRepository.findAll()) {
            if (!bookingRepository.hasOverlap(room.getId(), checkIn, checkOut, 0)) {
                freeRooms.add(room);
            }
        }
        return freeRooms;
    }

    /** Правило: нельзя сохранить номер с некорректными данными. */
    private void validate(Room room) {
        if (room.getRoomNumber() < 1 || room.getRoomNumber() > 9999) {
            throw new BusinessException("Номер комнаты должен быть от 1 до 9999.");
        }
        if (room.getType() == null) {
            throw new BusinessException("Тип номера обязателен.");
        }
        if (room.getCapacity() < 1 || room.getCapacity() > 6) {
            throw new BusinessException("Вместимость номера должна быть от 1 до 6 человек.");
        }
        if (room.getPricePerNight() <= 0 || room.getPricePerNight() > 1_000_000) {
            throw new BusinessException("Цена за ночь должна быть больше 0 и не больше 1 000 000 руб.");
        }
    }
}
