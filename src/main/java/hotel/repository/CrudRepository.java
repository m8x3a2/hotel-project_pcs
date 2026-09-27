package hotel.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Обобщённый интерфейс для стандартных CRUD-операций с базой данных.
 * Реализуется классами UserRepository и BookingRepository (полиморфизм).
 *
 * @param <T> тип сущности, с которой работает репозиторий
 */
public interface CrudRepository<T> {

    T save(T entity) throws SQLException;

    List<T> findAll() throws SQLException;

    Optional<T> findById(int id) throws SQLException;

    void update(T entity) throws SQLException;

    void delete(int id) throws SQLException;
}
