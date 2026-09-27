package hotel.service;

import hotel.exception.BusinessException;
import hotel.exception.EntityNotFoundException;
import hotel.model.User;
import hotel.repository.UserRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Сервисный слой для гостей. Здесь проверяются бизнес-правила
 * перед тем, как данные попадут в базу данных.
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(String fullName, String phone, String email, String passportNumber) throws SQLException {
        validateNotBlank(fullName, "ФИО");
        validateNotBlank(phone, "телефон");
        validateEmail(email);
        validateNotBlank(passportNumber, "номер паспорта");

        // Бизнес-правило: email и номер паспорта должны быть уникальными
        for (User existing : userRepository.findAll()) {
            if (existing.getEmail().equalsIgnoreCase(email)) {
                throw new BusinessException("Гость с таким email уже существует: " + email);
            }
            if (existing.getPassportNumber().equals(passportNumber)) {
                throw new BusinessException("Гость с таким номером паспорта уже существует: " + passportNumber);
            }
        }

        User user = new User(fullName, phone, email, passportNumber);
        return userRepository.save(user);
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

        validateNotBlank(fullName, "ФИО");
        validateNotBlank(phone, "телефон");
        validateEmail(email);
        validateNotBlank(passportNumber, "номер паспорта");

        // Проверка уникальности email/паспорта среди ДРУГИХ гостей
        for (User existing : userRepository.findAll()) {
            if (existing.getId() != id) {
                if (existing.getEmail().equalsIgnoreCase(email)) {
                    throw new BusinessException("Гость с таким email уже существует: " + email);
                }
                if (existing.getPassportNumber().equals(passportNumber)) {
                    throw new BusinessException("Гость с таким номером паспорта уже существует: " + passportNumber);
                }
            }
        }

        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassportNumber(passportNumber);
        userRepository.update(user);
    }

    public void deleteUser(int id) throws SQLException {
        getUserById(id); // проверка, что гость существует
        try {
            userRepository.delete(id);
        } catch (SQLException e) {
            // Нарушение внешнего ключа - у гостя есть бронирования
            throw new BusinessException("Нельзя удалить гостя: у него есть бронирования. " +
                    "Сначала удалите или переназначьте его бронирования.");
        }
    }

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Поле '" + fieldName + "' не может быть пустым.");
        }
    }

    private void validateEmail(String email) {
        validateNotBlank(email, "email");
        if (!email.contains("@") || !email.contains(".")) {
            throw new BusinessException("Некорректный формат email: " + email);
        }
    }
}
