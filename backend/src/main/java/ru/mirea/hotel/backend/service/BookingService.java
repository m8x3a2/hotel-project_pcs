package ru.mirea.hotel.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.config.AppProperties;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.Guest;
import ru.mirea.hotel.backend.domain.Room;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkAction;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkFailure;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkRequest;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkResult;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.error.NotFoundException;
import ru.mirea.hotel.backend.filter.BookingFilterParser;
import ru.mirea.hotel.backend.filter.BookingSpecifications;
import ru.mirea.hotel.backend.repository.BookingRepository;
import ru.mirea.hotel.backend.repository.GuestRepository;
import ru.mirea.hotel.backend.repository.RoomRepository;
import ru.mirea.hotel.backend.security.CurrentUserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Бизнес-логика бронирований: CRUD, архив, массовые операции и все правила предметной области.
 *
 * <p>Правила (те же, что в КР1, плюс новые для backend):</p>
 * <ol>
 *   <li>гость и номер должны существовать, отключённый номер нельзя выбрать для новой брони;</li>
 *   <li>дата выезда позже даты заезда, не больше {@value #MAX_NIGHTS} ночей;</li>
 *   <li>гостей не больше вместимости номера;</li>
 *   <li>номер не занят другой активной бронью на эти даты;</li>
 *   <li>новая бронь - только CREATED или CONFIRMED, заезд не в прошлом;</li>
 *   <li>смена статуса только по разрешённым переходам ({@link BookingStatus#canChangeTo});</li>
 *   <li>нельзя завершить бронь до даты заезда;</li>
 *   <li>у завершённой/отменённой брони нельзя менять гостя, номер, даты и число гостей;</li>
 *   <li>архивную бронь нельзя изменять - сначала восстановить;</li>
 *   <li>стоимость = ночи * цена номера и риск отмены считает сервер;</li>
 *   <li>изменение только при совпадении version (иначе 409).</li>
 * </ol>
 */
@Service
public class BookingService {
    static final int MAX_NIGHTS = 60;
    static final int MAX_TAGS = 20;
    private static final String DELETE_CONFIRMATION_HEADER = "X-Confirm-Delete";

    private final BookingRepository bookings;
    private final GuestRepository guests;
    private final RoomRepository rooms;
    private final CurrentUserService currentUser;
    private final BookingFilterParser parser;
    private final BookingMapper mapper;
    private final CancellationRiskCalculator riskCalculator;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public BookingService(BookingRepository bookings, GuestRepository guests, RoomRepository rooms,
                          CurrentUserService currentUser, BookingFilterParser parser, BookingMapper mapper,
                          CancellationRiskCalculator riskCalculator, AuditService audit,
                          AppProperties properties, Clock clock) {
        this.bookings = bookings;
        this.guests = guests;
        this.rooms = rooms;
        this.currentUser = currentUser;
        this.parser = parser;
        this.mapper = mapper;
        this.riskCalculator = riskCalculator;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> search(String query, Pageable pageable) {
        return bookings.findAll(BookingSpecifications.from(parser.parse(query)), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        UserAccount actor = currentUser.requireEntity();
        Checked checked = check(null, request, false);
        Booking booking = new Booking(UUID.randomUUID(), checked.code() != null ? checked.code() : nextCode(), actor);
        apply(booking, request, checked, actor);
        bookings.saveAndFlush(booking);
        audit.record("BOOKING_CREATED", "BOOKING", booking.getId(),
                "Создана бронь " + booking.getCode() + ": номер " + booking.getRoom().getRoomNumber()
                        + ", " + booking.getCheckIn() + " - " + booking.getCheckOut(), Map.of());
        return mapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse update(UUID id, BookingRequest request) {
        Booking booking = require(id);
        if (booking.isArchived()) throw new BadRequestException("Бронь в архиве. Сначала восстановите её");
        verifyVersion(request.version(), booking.getVersion());
        UserAccount actor = currentUser.requireEntity();
        BookingStatus oldStatus = booking.getStatus();
        Checked checked = check(booking, request, false);
        apply(booking, request, checked, actor);
        // flush сразу выполняет UPDATE: в ответ уйдёт уже увеличенная version,
        // а нарушение ограничений БД превратится в ошибку здесь, а не после ответа
        bookings.flush();
        audit.record("BOOKING_UPDATED", "BOOKING", id, "Изменена бронь " + booking.getCode(),
                oldStatus == booking.getStatus() ? Map.of()
                        : Map.of("statusFrom", oldStatus.name(), "statusTo", booking.getStatus().name()));
        return mapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse archive(UUID id) {
        Booking booking = require(id);
        archiveInternal(booking, currentUser.requireEntity());
        bookings.flush();
        audit.record("BOOKING_ARCHIVED", "BOOKING", id, "Бронь " + booking.getCode() + " перемещена в архив", Map.of());
        return mapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse restore(UUID id) {
        Booking booking = require(id);
        restoreInternal(booking, currentUser.requireEntity());
        bookings.flush();
        audit.record("BOOKING_RESTORED", "BOOKING", id, "Бронь " + booking.getCode() + " восстановлена из архива", Map.of());
        return mapper.toResponse(booking);
    }

    /**
     * Окончательное удаление (только ADMIN, проверяется в контроллере). Дополнительные предохранители:
     * функция включена в настройках, бронь уже в архиве, в заголовке X-Confirm-Delete повторён код брони.
     */
    @Transactional
    public void permanentDelete(UUID id, String confirmation) {
        if (!properties.permanentDeleteEnabled()) {
            throw new BadRequestException("Окончательное удаление отключено настройкой PERMANENT_DELETE_ENABLED");
        }
        Booking booking = require(id);
        if (!booking.isArchived()) throw new BadRequestException("Удалить можно только бронь из архива");
        if (confirmation == null || !booking.getCode().equalsIgnoreCase(confirmation.trim())) {
            throw new BadRequestException("Для удаления передайте код брони в заголовке " + DELETE_CONFIRMATION_HEADER);
        }
        String code = booking.getCode();
        bookings.delete(booking);
        audit.record("BOOKING_DELETED", "BOOKING", id, "Бронь " + code + " удалена окончательно", Map.of());
    }

    /** Массовая операция: каждая бронь обрабатывается отдельно, ошибки собираются в список. */
    @Transactional
    public BulkResult bulk(BulkRequest request) {
        if (request.action() == BulkAction.SET_STATUS && request.status() == null) {
            throw new BadRequestException("Для SET_STATUS укажите status");
        }
        String tag = null;
        if (request.action() == BulkAction.ADD_TAG || request.action() == BulkAction.REMOVE_TAG) {
            if (request.tag() == null || request.tag().isBlank()) throw new BadRequestException("Для операции с тегом укажите tag");
            tag = normalizeTag(request.tag());
        }
        UserAccount actor = currentUser.requireEntity();
        int succeeded = 0;
        List<BulkFailure> failures = new ArrayList<>();
        for (UUID id : request.ids()) {
            Booking booking = bookings.findById(id).orElse(null);
            if (booking == null) {
                failures.add(new BulkFailure(id, null, "Бронь не найдена"));
                continue;
            }
            try {
                applyBulk(booking, request, tag, actor);
                succeeded++;
            } catch (BadRequestException | ConflictException ex) {
                failures.add(new BulkFailure(id, booking.getCode(), ex.getMessage()));
            }
        }
        audit.record("BOOKING_BULK_" + request.action().name(), "BOOKING", null,
                "Массовая операция " + request.action() + ": успешно " + succeeded + " из " + request.ids().size(),
                Map.of("requested", request.ids().size(), "succeeded", succeeded, "failed", failures.size()));
        return new BulkResult(request.ids().size(), succeeded, failures);
    }

    // ---------------- для CSV-импорта ----------------

    /** Проверка строки CSV без записи в базу (для предварительного просмотра). */
    void checkImport(Booking existing, BookingRequest request) {
        check(existing, request, true);
    }

    /** Создаёт или обновляет бронь из строки CSV, с теми же проверками, что и обычный запрос. */
    Booking upsertFromImport(Booking existing, BookingRequest request, UserAccount actor) {
        Checked checked = check(existing, request, true);
        Booking booking = existing != null ? existing
                : new Booking(UUID.randomUUID(), checked.code() != null ? checked.code() : nextCode(), actor);
        apply(booking, request, checked, actor);
        return existing != null ? booking : bookings.save(booking);
    }

    // ---------------- внутренняя логика ----------------

    /** Результат проверки: найденные гость/номер и нормализованные значения. Сущность ещё не изменена. */
    private record Checked(String code, Guest guest, Room room, Set<String> tags, BigDecimal totalPrice) { }

    /**
     * Проверяет все правила и ничего не меняет. Поэтому ошибка в одной строке импорта или
     * в одной записи массовой операции не оставляет «полуизменённую» сущность.
     *
     * @param imported строка CSV: разрешены исторические данные (любой статус, прошлые даты)
     */
    private Checked check(Booking existing, BookingRequest request, boolean imported) {
        String code = normalizeCode(request.code());
        if (code != null) {
            boolean taken = existing == null
                    ? bookings.existsByCodeIgnoreCase(code)
                    : bookings.existsByCodeIgnoreCaseAndIdNot(code, existing.getId());
            if (taken) throw new ConflictException("Код брони " + code + " уже используется");
        }

        Guest guest = guests.findById(request.guestId())
                .orElseThrow(() -> new BadRequestException("Гость не найден"));
        Room room = rooms.findById(request.roomId())
                .orElseThrow(() -> new BadRequestException("Номер не найден"));
        boolean roomChanged = existing == null || !existing.getRoom().getId().equals(room.getId());
        if (!room.isActive() && roomChanged) {
            throw new BadRequestException("Номер " + room.getRoomNumber() + " отключён и недоступен для бронирования");
        }

        LocalDate checkIn = request.checkIn();
        LocalDate checkOut = request.checkOut();
        if (!checkOut.isAfter(checkIn)) throw new BadRequestException("Дата выезда должна быть позже даты заезда");
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) throw new BadRequestException("Бронь не может быть длиннее " + MAX_NIGHTS + " ночей");
        if (request.guestsCount() > room.getCapacity()) {
            throw new BadRequestException("Номер " + room.getRoomNumber() + " вмещает не больше "
                    + room.getCapacity() + " гостей");
        }

        BookingStatus status = request.status();
        LocalDate today = LocalDate.now(clock);
        if (existing == null && !imported) {
            if (status != BookingStatus.CREATED && status != BookingStatus.CONFIRMED) {
                throw new BadRequestException("Новая бронь может быть только в статусе CREATED или CONFIRMED");
            }
            if (checkIn.isBefore(today)) throw new BadRequestException("Дата заезда не может быть в прошлом");
        }
        if (existing != null && !imported) {
            if (!existing.getStatus().canChangeTo(status)) {
                throw new BadRequestException("Нельзя перевести бронь из статуса " + existing.getStatus() + " в " + status);
            }
            boolean stayChanged = roomChanged || !existing.getGuest().getId().equals(guest.getId())
                    || !existing.getCheckIn().equals(checkIn) || !existing.getCheckOut().equals(checkOut)
                    || existing.getGuestsCount() != request.guestsCount();
            if (!existing.getStatus().isEditable() && stayChanged) {
                throw new BadRequestException("У завершённой или отменённой брони нельзя менять гостя, номер, даты и число гостей");
            }
        }
        boolean becomesCompleted = status == BookingStatus.COMPLETED
                && (existing == null || existing.getStatus() != BookingStatus.COMPLETED);
        if (becomesCompleted && checkIn.isAfter(today)) {
            throw new BadRequestException("Нельзя завершить бронь до даты заезда (" + checkIn + ")");
        }

        boolean active = existing == null || !existing.isArchived();
        if (status.occupiesRoom() && active) {
            UUID self = existing == null ? UUID.randomUUID() : existing.getId();
            if (bookings.hasOverlap(room.getId(), checkIn, checkOut, self)) {
                throw new ConflictException("Номер " + room.getRoomNumber() + " уже забронирован на пересекающиеся даты");
            }
        }

        // Цена фиксируется при бронировании: если номер и даты не менялись, старая сумма сохраняется,
        // даже если цену номера в справочнике уже подняли.
        boolean repriced = existing == null || roomChanged
                || !existing.getCheckIn().equals(checkIn) || !existing.getCheckOut().equals(checkOut);
        BigDecimal totalPrice = repriced
                ? room.getPricePerNight().multiply(BigDecimal.valueOf(nights))
                : existing.getTotalPrice();
        return new Checked(code, guest, room, normalizeTags(request.tags()), totalPrice);
    }

    private void apply(Booking booking, BookingRequest request, Checked checked, UserAccount actor) {
        if (checked.code() != null) booking.setCode(checked.code());
        booking.setGuest(checked.guest());
        booking.setRoom(checked.room());
        booking.setCheckIn(request.checkIn());
        booking.setCheckOut(request.checkOut());
        booking.setGuestsCount(request.guestsCount());
        booking.setStatus(request.status());
        booking.setPrepaid(request.prepaid());
        booking.setSpecialRequests(normalizeNullable(request.specialRequests()));
        booking.setTags(checked.tags());
        booking.setTotalPrice(checked.totalPrice());
        recalculateRisk(booking);
        booking.setUpdatedBy(actor);
    }

    private void applyBulk(Booking booking, BulkRequest request, String tag, UserAccount actor) {
        switch (request.action()) {
            case ARCHIVE -> archiveInternal(booking, actor);
            case RESTORE -> restoreInternal(booking, actor);
            case SET_STATUS -> {
                BookingStatus next = request.status();
                if (booking.isArchived()) throw new BadRequestException("Бронь в архиве");
                if (booking.getStatus() == next) throw new BadRequestException("Бронь уже в статусе " + next);
                if (!booking.getStatus().canChangeTo(next)) {
                    throw new BadRequestException("Переход " + booking.getStatus() + " -> " + next + " запрещён");
                }
                if (next == BookingStatus.COMPLETED && booking.getCheckIn().isAfter(LocalDate.now(clock))) {
                    throw new BadRequestException("Заезд ещё не наступил (" + booking.getCheckIn() + ")");
                }
                booking.setStatus(next);
                recalculateRisk(booking);
                booking.setUpdatedBy(actor);
            }
            case ADD_TAG -> {
                Set<String> tags = new LinkedHashSet<>(booking.getTags());
                if (!tags.add(tag)) throw new BadRequestException("Тег «" + tag + "» уже есть");
                if (tags.size() > MAX_TAGS) throw new BadRequestException("Не больше " + MAX_TAGS + " тегов");
                booking.setTags(tags);
                booking.setUpdatedBy(actor);
            }
            case REMOVE_TAG -> {
                Set<String> tags = new LinkedHashSet<>(booking.getTags());
                if (!tags.remove(tag)) throw new BadRequestException("Тега «" + tag + "» нет");
                booking.setTags(tags);
                booking.setUpdatedBy(actor);
            }
        }
    }

    private static void archiveInternal(Booking booking, UserAccount actor) {
        if (booking.isArchived()) throw new BadRequestException("Бронь уже в архиве");
        booking.archive(actor);
    }

    private void restoreInternal(Booking booking, UserAccount actor) {
        if (!booking.isArchived()) throw new BadRequestException("Бронь не в архиве");
        // пока бронь лежала в архиве, номер на эти даты могли занять
        if (booking.getStatus().occupiesRoom() && bookings.hasOverlap(booking.getRoom().getId(),
                booking.getCheckIn(), booking.getCheckOut(), booking.getId())) {
            throw new ConflictException("Номер " + booking.getRoom().getRoomNumber() + " уже занят на эти даты другой бронью");
        }
        booking.restore(actor);
    }

    private void recalculateRisk(Booking booking) {
        booking.setRiskLevel(riskCalculator.calculate(booking.getStatus(), booking.isPrepaid(),
                booking.getNights(), booking.getTotalPrice()));
    }

    private String nextCode() {
        String code;
        do {
            code = String.format("BK-%06d", bookings.nextCodeNumber());
        } while (bookings.existsByCodeIgnoreCase(code)); // код мог быть занят вручную
        return code;
    }

    private Booking require(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Бронь не найдена"));
    }

    private static void verifyVersion(Long requested, long actual) {
        if (requested == null) throw new BadRequestException("Для изменения нужно поле version");
        if (requested != actual) {
            throw new ConflictException("Бронь уже изменил другой пользователь. Обновите данные и повторите");
        }
    }

    private static String normalizeCode(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        tags.stream().filter(Objects::nonNull).map(BookingService::normalizeTag).forEach(result::add);
        if (result.size() > MAX_TAGS) throw new BadRequestException("Не больше " + MAX_TAGS + " тегов");
        return result;
    }

    static String normalizeTag(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        if (normalized.isEmpty()) throw new BadRequestException("Пустой тег");
        if (normalized.length() > 50) throw new BadRequestException("Тег длиннее 50 символов");
        return normalized;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
