package ru.mirea.hotel.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Интерфейс - "договор": какие методы обязан иметь любой репозиторий.
 * Его реализуют GuestRepository, RoomRepository и BookingRepository (полиморфизм:
 * методы называются одинаково, а работают каждый со своей таблицей).
 *
 * <T> - обобщённый тип (generic): вместо T подставляется Guest, Room или Booking.
 * CRUD = Create, Read, Update, Delete.
 */
public interface CrudRepository<T> {

    /** Добавить запись (INSERT). Возвращает тот же объект, но уже с id из базы. */
    T save(T entity) throws SQLException;

    /** Получить все записи (SELECT). */
    List<T> findAll() throws SQLException;

    /** Найти запись по id. Optional - "коробка", которая может быть пустой, если записи нет. */
    Optional<T> findById(int id) throws SQLException;

    /** Изменить запись (UPDATE). */
    void update(T entity) throws SQLException;

    /** Удалить запись (DELETE). */
    void deleteById(int id) throws SQLException;
}
