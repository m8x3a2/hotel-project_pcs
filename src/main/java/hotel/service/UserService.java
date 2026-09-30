package hotel.service;

import hotel.exception.BusinessException;
import hotel.exception.EntityNotFoundException;
import hotel.model.User;
import hotel.repository.UserRepository;
import hotel.util.Validator;

import java.sql.SQLException;
import java.util.List;

/**
 * Сервисный слой для гостей. Здесь проверяются бизнес-правила
 * перед тем, как данные попадут в базу данных.
 */
public class UserService {

    // Коды ошибок PostgreSQL (SQLSTATE)
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String fullName, String phone, String email, String passportNumber) throws SQLException {
        // Проверка формата + приведение к единому виду (телефон "+7 900 111-11-11", email в нижнем регистре и т.д.)
        User user = new User(
                Validator.validateFullName(fullName),
                Validator.validatePhone(phone),
                Validator.validateEmail(email),
                Validator.validatePassport(passportNumber));

        // Бизнес-правило: email и номер паспорта должны быть уникальными
        checkUniqueness(user.getEmail(), user.getPassportNumber(), null);

        try {
            return userRepository.save(user);
        } catch (SQLException e) {
            throwIfDuplicate(e);
            throw e;
        }
    }

    public List<User> getAllUsers() throws SQLException {
        return userRepository.findAll();
    }

    public User getUserById(int id) throws SQLException {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Гость с ID " + id + " не найден."));
    }

    public void updateUser(int id, String fullName, String phone, String email, String passportNumber) throws SQLException {
        User user = getUserById(id); // бросит EntityNotFoundException, если гостя нет

        String newFullName = Validator.validateFullName(fullName);
        String newPhone = Validator.validatePhone(phone);
        String newEmail = Validator.validateEmail(email);
        String newPassport = Validator.validatePassport(passportNumber);

        // Проверка уникальности email/паспорта среди ДРУГИХ гостей
        checkUniqueness(newEmail, newPassport, id);

        user.setFullName(newFullName);
        user.setPhone(newPhone);
        user.setEmail(newEmail);
        user.setPassportNumber(newPassport);
        try {
            userRepository.update(user);
        } catch (SQLException e) {
            throwIfDuplicate(e);
            throw e;
        }
    }

    public void deleteUser(int id) throws SQLException {
        getUserById(id); // проверка, что гость существует
        try {
            userRepository.delete(id);
        } catch (SQLException e) {
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                // Нарушение внешнего ключа - у гостя есть бронирования
                throw new BusinessException("Нельзя удалить гостя: у него есть бронирования. " +
                        "Сначала удалите или переназначьте его бронирования.");
            }
            throw e; // другая ошибка БД - пробрасываем как есть
        }
    }

    /**
     * Проверяет, что email и паспорт не заняты другим гостем.
     * excludeUserId - ID гостя, которого не учитываем (при редактировании он сам себе не дубликат).
     */
    private void checkUniqueness(String email, String passportNumber, Integer excludeUserId) throws SQLException {
        for (User existing : userRepository.findAll()) {
            if (excludeUserId != null && existing.getId() == excludeUserId) {
                continue;
            }
            if (existing.getEmail().equalsIgnoreCase(email)) {
                throw new BusinessException("Гость с таким email уже существует: " + email);
            }
            if (existing.getPassportNumber().replaceAll("\\s", "").equals(passportNumber)) {
                throw new BusinessException("Гость с таким номером паспорта уже существует: " + passportNumber);
            }
        }
    }

    /** Если два пользователя одновременно добавили одинаковый email/паспорт, сработает UNIQUE в базе. */
    private void throwIfDuplicate(SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            throw new BusinessException("Гость с таким email или номером паспорта уже существует.");
        }
    }
}
