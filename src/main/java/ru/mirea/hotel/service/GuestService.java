package ru.mirea.hotel.service;

import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.exception.EntityNotFoundException;
import ru.mirea.hotel.model.Guest;
import ru.mirea.hotel.repository.BookingRepository;
import ru.mirea.hotel.repository.GuestRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Бизнес-логика для гостей: проверки данных и правила, а сохранение - через репозиторий.
 */
public class GuestService {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestService(GuestRepository guestRepository, BookingRepository bookingRepository) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }

    public Guest create(String fullName, String phone, String email) throws SQLException {
        Guest guest = new Guest(fullName.trim(), phone.trim(), email.trim().toLowerCase());
        validate(guest);

        // Правило: email гостя уникален
        if (guestRepository.findByEmail(guest.getEmail()).isPresent()) {
            throw new BusinessException("Гость с email " + guest.getEmail() + " уже существует.");
        }
        return guestRepository.save(guest);
    }

    public List<Guest> getAll() throws SQLException {
        return guestRepository.findAll();
    }

    public Guest getById(int id) throws SQLException {
        // orElseThrow: если Optional пустой - бросаем исключение, иначе достаём гостя
        return guestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Гость с ID " + id + " не найден."));
    }

    public void update(int id, String fullName, String phone, String email) throws SQLException {
        Guest guest = getById(id);
        guest.setFullName(fullName.trim());
        guest.setPhone(phone.trim());
        guest.setEmail(email.trim().toLowerCase());
        validate(guest);

        // Правило: новый email не должен принадлежать другому гостю
        Optional<Guest> sameEmail = guestRepository.findByEmail(guest.getEmail());
        if (sameEmail.isPresent() && sameEmail.get().getId() != id) {
            throw new BusinessException("Email " + guest.getEmail() + " уже занят другим гостем.");
        }
        guestRepository.update(guest);
    }

    public void delete(int id) throws SQLException {
        getById(id); // проверка, что гость существует

        // Правило: нельзя удалить гостя, у которого есть бронирования
        int bookingsCount = bookingRepository.countByGuestId(id);
        if (bookingsCount > 0) {
            throw new BusinessException("Нельзя удалить гостя: у него есть бронирования (" + bookingsCount
                    + " шт.). Сначала удалите их.");
        }
        guestRepository.deleteById(id);
    }

    /** Правило: нельзя сохранить гостя с пустыми или некорректными данными. */
    private void validate(Guest guest) {
        if (guest.getFullName().isEmpty()) {
            throw new BusinessException("ФИО не может быть пустым.");
        }
        if (guest.getFullName().length() > 100) {
            throw new BusinessException("ФИО слишком длинное (максимум 100 символов).");
        }
        // matches(...) - проверка строки регулярным выражением: только буквы, пробелы и дефис
        if (!guest.getFullName().matches("[А-Яа-яЁёA-Za-z\\- ]+")) {
            throw new BusinessException("ФИО может содержать только буквы, пробелы и дефис.");
        }
        if (!guest.getPhone().matches("\\+?[0-9 ()\\-]{7,20}")) {
            throw new BusinessException("Телефон должен состоять из цифр (можно +, пробелы, скобки, дефис), "
                    + "например +7 900 123-45-67.");
        }
        if (guest.getEmail().length() > 100 || !guest.getEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BusinessException("Некорректный email, пример: ivanov@mail.ru");
        }
    }
}
