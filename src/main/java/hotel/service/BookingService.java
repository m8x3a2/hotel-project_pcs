package hotel.service;

import hotel.exception.BusinessException;
import hotel.exception.EntityNotFoundException;
import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.RoomType;
import hotel.model.User;
import hotel.repository.BookingRepository;
import hotel.repository.UserRepository;
import hotel.util.Validator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервисный слой для бронирований. Содержит все бизнес-правила предметной области
 * "Гостиница: бронирование номеров", а также операции поиска, фильтрации,
 * сортировки и подсчёта статистики (реализованы через Stream API).
 */
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    // Карта разрешённых переходов между статусами бронирования (бизнес-правило №5)
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.CREATED, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.CHECKED_IN, BookingStatus.CANCELLED),
            BookingStatus.CHECKED_IN, EnumSet.of(BookingStatus.CHECKED_OUT),
            BookingStatus.CHECKED_OUT, EnumSet.noneOf(BookingStatus.class),
            BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class)
    );

    public BookingService(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    // ===================== СОЗДАНИЕ БРОНИРОВАНИЯ =====================

    public Booking createBooking(int userId, int roomNumber, RoomType roomType,
                                  LocalDate checkIn, LocalDate checkOut) throws SQLException {

        Validator.validateId(userId, "гостя");

        // Бизнес-правило 1: гость должен существовать
        if (userRepository.findById(userId).isEmpty()) {
            throw new EntityNotFoundException("Гость с ID " + userId + " не найден. Бронирование невозможно.");
        }

        validateRoomNumber(roomNumber);
        validateRoomType(roomType);
        validateDates(checkIn, checkOut);
        // Новое бронирование нельзя оформить задним числом или слишком далеко вперёд
        Validator.validateCheckInForNewBooking(checkIn, LocalDate.now());

        // Бизнес-правило: номер не должен быть уже занят на пересекающиеся даты
        checkRoomAvailability(roomNumber, checkIn, checkOut, null);

        double totalPrice = calculatePrice(roomType, checkIn, checkOut);

        Booking booking = new Booking(userId, roomNumber, roomType, checkIn, checkOut,
                BookingStatus.CREATED, totalPrice);
        booking.setCreatedAt(LocalDateTime.now());
        return bookingRepository.save(booking);
    }

    // ===================== ПРОСТОЙ CRUD =====================

    public List<Booking> getAllBookings() throws SQLException {
        return bookingRepository.findAll();
    }

    public Booking getBookingById(int id) throws SQLException {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Бронирование с ID " + id + " не найдено."));
    }

    public void updateBooking(int id, int roomNumber, RoomType roomType,
                               LocalDate checkIn, LocalDate checkOut) throws SQLException {
        Booking booking = getEditableBooking(id);

        validateRoomNumber(roomNumber);
        validateRoomType(roomType);
        validateDates(checkIn, checkOut);
        // Если дату заезда перенесли, новая дата тоже не должна быть в прошлом
        if (!checkIn.equals(booking.getCheckInDate())) {
            Validator.validateCheckInForNewBooking(checkIn, LocalDate.now());
        }
        checkRoomAvailability(roomNumber, checkIn, checkOut, id);

        booking.setRoomNumber(roomNumber);
        booking.setRoomType(roomType);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setTotalPrice(calculatePrice(roomType, checkIn, checkOut));

        bookingRepository.update(booking);
    }

    /**
     * Возвращает бронирование, если его ещё можно редактировать.
     * Бизнес-правило: менять можно только ещё не начавшееся бронирование (CREATED или CONFIRMED).
     */
    public Booking getEditableBooking(int id) throws SQLException {
        Booking booking = getBookingById(id);
        if (booking.getStatus() != BookingStatus.CREATED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessException("Нельзя изменить бронирование в статусе " + booking.getStatus() +
                    ". Изменять можно только бронирования в статусах CREATED и CONFIRMED.");
        }
        return booking;
    }

    /** Изменение статуса бронирования с проверкой допустимости перехода (бизнес-правило). */
    public void changeStatus(int id, BookingStatus newStatus) throws SQLException {
        if (newStatus == null) {
            throw new BusinessException("Новый статус не указан.");
        }
        Booking booking = getBookingById(id);

        Set<BookingStatus> allowed = ALLOWED_TRANSITIONS.get(booking.getStatus());
        if (!allowed.contains(newStatus)) {
            throw new BusinessException("Нельзя перевести бронирование из статуса " +
                    booking.getStatus() + " в статус " + newStatus + ".");
        }

        booking.setStatus(newStatus);
        bookingRepository.update(booking);
    }

    public void deleteBooking(int id) throws SQLException {
        getBookingById(id); // проверка существования
        bookingRepository.delete(id);
    }

    // ===================== ПОИСК (минимум 2 способа) =====================

    /** Поиск по имени гостя (подстрока, без учёта регистра). */
    public List<Booking> searchByGuestName(String namePart) throws SQLException {
        Map<Integer, User> usersById = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        String query = namePart.toLowerCase();
        return bookingRepository.findAll().stream()
                .filter(b -> {
                    User u = usersById.get(b.getUserId());
                    return u != null && u.getFullName().toLowerCase().contains(query);
                })
                .collect(Collectors.toList());
    }

    /** Поиск по номеру комнаты. */
    public List<Booking> searchByRoomNumber(int roomNumber) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoomNumber() == roomNumber)
                .collect(Collectors.toList());
    }

    // ===================== ФИЛЬТРАЦИЯ (минимум 2 способа) =====================

    public List<Booking> filterByStatus(BookingStatus status) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Booking> filterByRoomType(RoomType roomType) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoomType() == roomType)
                .collect(Collectors.toList());
    }

    // ===================== СОРТИРОВКА (минимум 2 способа) =====================

    public List<Booking> sortByCheckInDate() throws SQLException {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getCheckInDate))
                .collect(Collectors.toList());
    }

    public List<Booking> sortByTotalPriceDescending() throws SQLException {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparingDouble(Booking::getTotalPrice).reversed())
                .collect(Collectors.toList());
    }

    // ===================== СТАТИСТИКА (минимум 5 показателей) =====================

    public Map<String, String> getStatistics() throws SQLException {
        List<Booking> bookings = bookingRepository.findAll();
        List<User> users = userRepository.findAll();

        long active = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.CHECKED_IN)
                .count();
        long completed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_OUT).count();
        long cancelled = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        double totalRevenue = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .mapToDouble(Booking::getTotalPrice)
                .sum();

        double averagePrice = bookings.isEmpty() ? 0.0 :
                bookings.stream().mapToDouble(Booking::getTotalPrice).average().orElse(0.0);

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Всего гостей", String.valueOf(users.size()));
        stats.put("Всего бронирований", String.valueOf(bookings.size()));
        stats.put("Активных бронирований (подтверждено/заселено)", String.valueOf(active));
        stats.put("Завершённых бронирований", String.valueOf(completed));
        stats.put("Отменённых бронирований", String.valueOf(cancelled));
        stats.put("Общая выручка (без отменённых), руб.", String.format("%.2f", totalRevenue));
        stats.put("Средняя стоимость бронирования, руб.", String.format("%.2f", averagePrice));
        return stats;
    }

    // ===================== ПРИВАТНЫЕ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (бизнес-правила) =====================

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        // Бизнес-правило: дата выезда позже даты заезда, срок проживания ограничен
        Validator.validateStayDates(checkIn, checkOut);
    }

    private void validateRoomNumber(int roomNumber) {
        Validator.validateRoomNumber(roomNumber);
    }

    private void validateRoomType(RoomType roomType) {
        if (roomType == null) {
            throw new BusinessException("Тип номера обязателен.");
        }
    }

    /**
     * Бизнес-правило: номер нельзя забронировать, если он уже занят
     * (в статусе, отличном от CANCELLED) на пересекающиеся даты.
     * excludeBookingId - ID бронирования, которое нужно исключить из проверки (при редактировании).
     */
    private void checkRoomAvailability(int roomNumber, LocalDate checkIn, LocalDate checkOut,
                                        Integer excludeBookingId) throws SQLException {
        boolean occupied = bookingRepository.findAll().stream()
                .filter(b -> b.getRoomNumber() == roomNumber)
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> excludeBookingId == null || b.getId() != excludeBookingId)
                .anyMatch(b -> checkIn.isBefore(b.getCheckOutDate()) && checkOut.isAfter(b.getCheckInDate()));

        if (occupied) {
            throw new BusinessException("Номер " + roomNumber +
                    " уже забронирован на пересекающиеся даты. Выберите другой номер или даты.");
        }
    }

    private double calculatePrice(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights * roomType.getPricePerNight();
    }
}
