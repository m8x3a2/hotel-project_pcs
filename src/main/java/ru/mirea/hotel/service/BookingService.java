package ru.mirea.hotel.service;

import ru.mirea.hotel.exception.BusinessException;
import ru.mirea.hotel.exception.EntityNotFoundException;
import ru.mirea.hotel.model.Booking;
import ru.mirea.hotel.model.BookingStatus;
import ru.mirea.hotel.model.Room;
import ru.mirea.hotel.model.RoomType;
import ru.mirea.hotel.repository.BookingRepository;
import ru.mirea.hotel.repository.GuestRepository;
import ru.mirea.hotel.repository.RoomRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Бизнес-логика бронирований - главный сервис системы.
 *
 * Бизнес-правила бронирования:
 *  1. Нельзя указать несуществующего гостя или номер.
 *  2. Дата выезда должна быть позже даты заезда, срок - не больше 30 ночей.
 *  3. Нельзя бронировать задним числом (дата заезда не раньше сегодняшней).
 *  4. Количество гостей - от 1 до вместимости номера.
 *  5. Номер нельзя забронировать, если он уже занят на эти даты.
 *  6. Статусы меняются только по разрешённым переходам (см. BookingStatus.canChangeTo).
 *  7. Нельзя завершить бронирование, которое ещё не началось.
 *  8. Менять даты/номер можно только у активного бронирования (Создано или Подтверждено).
 *  9. Стоимость считается автоматически: количество ночей * цена номера за ночь.
 */
public class BookingService {

    private static final int MAX_NIGHTS = 30;

    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final RoomRepository roomRepository;

    public BookingService(BookingRepository bookingRepository, GuestRepository guestRepository,
                          RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
    }

    // ======================== CRUD ========================

    public Booking create(int guestId, int roomId, LocalDate checkIn, LocalDate checkOut, int guestsCount)
            throws SQLException {
        // Правило 1
        if (guestRepository.findById(guestId).isEmpty()) {
            throw new EntityNotFoundException("Гость с ID " + guestId + " не найден.");
        }
        Room room = findRoom(roomId);

        // Правило 3
        if (checkIn != null && checkIn.isBefore(LocalDate.now())) {
            throw new BusinessException("Дата заезда не может быть в прошлом.");
        }
        // Правила 2, 4, 5
        checkBookingData(room, checkIn, checkOut, guestsCount, 0);

        Booking booking = new Booking(guestId, roomId, checkIn, checkOut, guestsCount);
        booking.setStatus(BookingStatus.CREATED);
        booking.setTotalPrice(calculatePrice(room, checkIn, checkOut)); // Правило 9
        bookingRepository.save(booking);

        // Перечитываем из базы, чтобы заполнились имя гостя и номер комнаты (они приходят через JOIN)
        return getById(booking.getId());
    }

    public List<Booking> getAll() throws SQLException {
        return bookingRepository.findAll();
    }

    public Booking getById(int id) throws SQLException {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Бронирование с ID " + id + " не найдено."));
    }

    /**
     * Возвращает бронирование, только если его ещё можно изменять.
     * Меню вызывает этот метод до ввода новых данных, чтобы сразу сообщить об ошибке.
     */
    public Booking getEditableById(int id) throws SQLException {
        Booking booking = getById(id);
        // Правило 8
        if (!booking.getStatus().isEditable()) {
            throw new BusinessException("Нельзя изменить бронирование в статусе «" + booking.getStatus()
                    + "». Изменять можно только бронирования в статусах «Создано» и «Подтверждено».");
        }
        return booking;
    }

    /** Изменение номера, дат и количества гостей. Стоимость пересчитывается. */
    public Booking update(int id, int roomId, LocalDate checkIn, LocalDate checkOut, int guestsCount)
            throws SQLException {
        Booking booking = getEditableById(id);
        Room room = findRoom(roomId);

        // Правило 3: если дату заезда перенесли, новая дата тоже не может быть в прошлом
        if (checkIn != null && !checkIn.equals(booking.getCheckIn()) && checkIn.isBefore(LocalDate.now())) {
            throw new BusinessException("Новая дата заезда не может быть в прошлом.");
        }
        // Правила 2, 4, 5 (само это бронирование при проверке занятости не учитываем)
        checkBookingData(room, checkIn, checkOut, guestsCount, id);

        booking.setRoomId(roomId);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);
        booking.setGuestsCount(guestsCount);
        booking.setTotalPrice(calculatePrice(room, checkIn, checkOut));
        bookingRepository.update(booking);
        return getById(id);
    }

    public void changeStatus(int id, BookingStatus newStatus) throws SQLException {
        Booking booking = getById(id);

        // Правило 6
        if (newStatus == null || !booking.getStatus().canChangeTo(newStatus)) {
            throw new BusinessException("Нельзя перевести бронирование из статуса «" + booking.getStatus()
                    + "» в статус «" + newStatus + "». Разрешено: Создано -> Подтверждено/Отменено, "
                    + "Подтверждено -> Завершено/Отменено.");
        }
        // Правило 7
        if (newStatus == BookingStatus.COMPLETED && booking.getCheckIn().isAfter(LocalDate.now())) {
            throw new BusinessException("Нельзя завершить бронирование, которое ещё не началось (заезд "
                    + booking.getCheckIn() + ").");
        }
        booking.setStatus(newStatus);
        bookingRepository.update(booking);
    }

    public void delete(int id) throws SQLException {
        getById(id); // если такого нет - будет EntityNotFoundException
        bookingRepository.deleteById(id);
    }

    /** Бронирования одного гостя (для просмотра связанных данных). */
    public List<Booking> getByGuestId(int guestId) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getGuestId() == guestId)
                .toList();
    }

    /** Бронирования одного номера. */
    public List<Booking> getByRoomId(int roomId) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoomId() == roomId)
                .toList();
    }

    // ======================== ПОИСК ========================
    // Используем Stream API: берём список из базы и "пропускаем" его через фильтр.

    /** Поиск 1: по части ФИО гостя (без учёта регистра). */
    public List<Booking> searchByGuestName(String text) throws SQLException {
        String query = text.trim().toLowerCase();
        if (query.isEmpty()) {
            throw new BusinessException("Строка поиска не может быть пустой.");
        }
        return bookingRepository.findAll().stream()
                .filter(b -> b.getGuestName().toLowerCase().contains(query))
                .toList();
    }

    /** Поиск 2: по номеру комнаты (например, 101). */
    public List<Booking> searchByRoomNumber(int roomNumber) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoomNumber() == roomNumber)
                .toList();
    }

    // ======================== ФИЛЬТРАЦИЯ ========================

    /** Фильтр 1: по статусу. */
    public List<Booking> filterByStatus(BookingStatus status) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == status)
                .toList();
    }

    /** Фильтр 2: по типу номера. */
    public List<Booking> filterByRoomType(RoomType roomType) throws SQLException {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getRoomType() == roomType)
                .toList();
    }

    /** Фильтр 3: дата заезда попадает в период [from; to]. */
    public List<Booking> filterByCheckInPeriod(LocalDate from, LocalDate to) throws SQLException {
        if (to.isBefore(from)) {
            throw new BusinessException("Конец периода не может быть раньше начала.");
        }
        return bookingRepository.findAll().stream()
                .filter(b -> !b.getCheckIn().isBefore(from) && !b.getCheckIn().isAfter(to))
                .toList();
    }

    /**
     * Поиск и все фильтры сразу (для окна JavaFX, КР2). Если параметр null - этот фильтр не применяется.
     * Текст ищется в двух полях: ФИО гостя и номер комнаты.
     */
    public List<Booking> search(String text, BookingStatus status, RoomType roomType, LocalDate from, LocalDate to)
            throws SQLException {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("Период заезда: дата «по» не может быть раньше даты «с».");
        }
        String query = text == null ? "" : text.trim().toLowerCase();
        return bookingRepository.findAll().stream()
                .filter(b -> query.isEmpty()
                        || b.getGuestName().toLowerCase().contains(query)
                        || String.valueOf(b.getRoomNumber()).contains(query))
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> roomType == null || b.getRoomType() == roomType)
                .filter(b -> from == null || !b.getCheckIn().isBefore(from))
                .filter(b -> to == null || !b.getCheckIn().isAfter(to))
                .toList();
    }

    // ======================== СОРТИРОВКА ========================
    // Comparator говорит, как сравнивать два бронирования между собой.

    /** Сортировка 1: по дате заезда (сначала ранние). */
    public List<Booking> sortByCheckIn() throws SQLException {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getCheckIn))
                .toList();
    }

    /** Сортировка 2: по стоимости (сначала самые дорогие). */
    public List<Booking> sortByPriceDesc() throws SQLException {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getTotalPrice).reversed())
                .toList();
    }

    /** Сортировка 3: по ФИО гостя по алфавиту, внутри одного гостя - по дате заезда. */
    public List<Booking> sortByGuestName() throws SQLException {
        return bookingRepository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getGuestName).thenComparing(Booking::getCheckIn))
                .toList();
    }

    // ======================== СТАТИСТИКА ========================

    /**
     * Считает показатели системы. Возвращает Map "название показателя -> значение".
     * LinkedHashMap запоминает порядок добавления, поэтому показатели выведутся в нужном порядке.
     */
    public Map<String, String> getStatistics() throws SQLException {
        List<Booking> bookings = bookingRepository.findAll();
        List<Booking> notCancelled = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .toList();
        LocalDate today = LocalDate.now();

        // groupingBy раскладывает бронирования по статусам и считает их: {CREATED=4, CONFIRMED=3, ...}
        Map<BookingStatus, Long> countByStatus = bookings.stream()
                .collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));

        // Выручка - сумма завершённых бронирований
        double revenue = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .mapToDouble(Booking::getTotalPrice)
                .sum();

        // Ожидаемая выручка - сумма активных (ещё не завершённых и не отменённых) бронирований
        double expectedRevenue = bookings.stream()
                .filter(b -> b.getStatus().isEditable())
                .mapToDouble(Booking::getTotalPrice)
                .sum();

        double averagePrice = notCancelled.stream().mapToDouble(Booking::getTotalPrice).average().orElse(0);
        double averageNights = notCancelled.stream().mapToLong(Booking::getNights).average().orElse(0);

        // Сколько номеров занято сегодня (подтверждённая бронь, в период которой попадает сегодняшний день)
        long busyToday = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> !b.getCheckIn().isAfter(today) && b.getCheckOut().isAfter(today))
                .map(Booking::getRoomId)
                .distinct()
                .count();

        // Самый популярный тип номера (по неотменённым бронированиям)
        Map<RoomType, Long> countByType = notCancelled.stream()
                .collect(Collectors.groupingBy(Booking::getRoomType, Collectors.counting()));
        String popularType = "нет данных";
        long maxCount = 0;
        for (RoomType type : RoomType.values()) {
            long count = countByType.getOrDefault(type, 0L);
            if (count > maxCount) {
                maxCount = count;
                popularType = type + " (" + count + " брон.)";
            }
        }

        int roomsCount = roomRepository.findAll().size();

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("Всего гостей", String.valueOf(guestRepository.findAll().size()));
        stats.put("Всего номеров", String.valueOf(roomsCount));
        stats.put("Всего бронирований", String.valueOf(bookings.size()));
        for (BookingStatus status : BookingStatus.values()) {
            stats.put("   - " + status, String.valueOf(countByStatus.getOrDefault(status, 0L)));
        }
        stats.put("Номеров занято сегодня", busyToday + " из " + roomsCount);
        stats.put("Выручка (завершённые), руб.", String.format("%.2f", revenue));
        stats.put("Ожидаемая выручка (активные), руб.", String.format("%.2f", expectedRevenue));
        stats.put("Средняя стоимость бронирования, руб.", String.format("%.2f", averagePrice));
        stats.put("Средняя длительность, ночей", String.format("%.1f", averageNights));
        stats.put("Самый популярный тип номера", popularType);
        return stats;
    }

    /** Количество бронирований в каждом статусе (для круговой диаграммы в КР2). */
    public Map<BookingStatus, Long> countByStatus() throws SQLException {
        List<Booking> bookings = bookingRepository.findAll();
        Map<BookingStatus, Long> result = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            long count = bookings.stream().filter(b -> b.getStatus() == status).count();
            result.put(status, count);
        }
        return result;
    }

    // ======================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ========================

    private Room findRoom(int roomId) throws SQLException {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Номер с ID " + roomId + " не найден."));
    }

    /** Проверки правил 2, 4 и 5 - общие для создания и изменения бронирования. */
    private void checkBookingData(Room room, LocalDate checkIn, LocalDate checkOut, int guestsCount,
                                  int excludeBookingId) throws SQLException {
        // Правило 2
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("Даты заезда и выезда обязательны.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("Дата выезда должна быть позже даты заезда.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw new BusinessException("Нельзя забронировать больше чем на " + MAX_NIGHTS + " ночей.");
        }
        // Правило 4
        if (guestsCount < 1) {
            throw new BusinessException("Количество гостей должно быть не меньше 1.");
        }
        if (guestsCount > room.getCapacity()) {
            throw new BusinessException("Номер " + room.getRoomNumber() + " вмещает только "
                    + room.getCapacity() + " чел., а указано " + guestsCount + ".");
        }
        // Правило 5
        if (bookingRepository.hasOverlap(room.getId(), checkIn, checkOut, excludeBookingId)) {
            throw new BusinessException("Номер " + room.getRoomNumber() + " уже занят на даты "
                    + checkIn + " - " + checkOut + ". Выберите другой номер или даты.");
        }
    }

    /**
     * Правило 9: стоимость = ночи * цена за ночь.
     * Метод открытый, потому что окно бронирования (КР2) показывает предварительную стоимость.
     */
    public double calculatePrice(Room room, LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights * room.getPricePerNight();
    }
}
