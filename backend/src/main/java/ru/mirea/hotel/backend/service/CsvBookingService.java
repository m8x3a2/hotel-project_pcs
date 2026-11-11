package ru.mirea.hotel.backend.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.hotel.backend.config.AppProperties;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.Guest;
import ru.mirea.hotel.backend.domain.Room;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportMode;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportPreview;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportResult;
import ru.mirea.hotel.backend.dto.ImportDtos.RowIssue;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.filter.BookingFilterParser;
import ru.mirea.hotel.backend.filter.BookingSpecifications;
import ru.mirea.hotel.backend.repository.BookingRepository;
import ru.mirea.hotel.backend.repository.GuestRepository;
import ru.mirea.hotel.backend.repository.RoomRepository;
import ru.mirea.hotel.backend.security.CurrentUserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Импорт и экспорт бронирований в CSV (UTF-8, разделитель запятая).
 *
 * <p>Импорт в два шага: {@code preview} проверяет файл и ничего не пишет в базу,
 * {@code apply} применяет только корректные строки. Обе операции используют одни и те же
 * правила {@link BookingService}, что и обычное создание брони.</p>
 */
@Service
public class CsvBookingService {
    /** Колонки, без которых файл не принимается. */
    static final List<String> REQUIRED_HEADERS = List.of(
            "code", "guestEmail", "roomNumber", "checkIn", "checkOut", "guestsCount", "status");
    /** Колонки экспорта. Файл экспорта можно сразу импортировать обратно (лишние колонки игнорируются). */
    static final String[] EXPORT_HEADERS = {
            "code", "guestName", "guestEmail", "roomNumber", "roomType", "checkIn", "checkOut", "nights",
            "guestsCount", "status", "prepaid", "totalPrice", "riskLevel", "archived", "specialRequests", "tags"
    };

    private final BookingRepository bookings;
    private final GuestRepository guests;
    private final RoomRepository rooms;
    private final BookingService bookingService;
    private final BookingMapper mapper;
    private final BookingFilterParser parser;
    private final CurrentUserService currentUser;
    private final Validator validator;
    private final AppProperties properties;
    private final AuditService audit;

    public CsvBookingService(BookingRepository bookings, GuestRepository guests, RoomRepository rooms,
                             BookingService bookingService, BookingMapper mapper, BookingFilterParser parser,
                             CurrentUserService currentUser, Validator validator, AppProperties properties,
                             AuditService audit) {
        this.bookings = bookings;
        this.guests = guests;
        this.rooms = rooms;
        this.bookingService = bookingService;
        this.mapper = mapper;
        this.parser = parser;
        this.currentUser = currentUser;
        this.validator = validator;
        this.properties = properties;
        this.audit = audit;
    }

    /** Шаг 1: проверка файла без записи в базу. */
    @Transactional(readOnly = true)
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        ParsedFile parsed = parse(file, mode);
        List<RowIssue> issues = new ArrayList<>(parsed.issues());
        int toCreate = 0;
        int toUpdate = 0;
        for (ParsedRow row : parsed.rows()) {
            try {
                bookingService.checkImport(row.existing(), row.request());
                if (row.existing() == null) toCreate++; else toUpdate++;
            } catch (BadRequestException | ConflictException ex) {
                issues.add(new RowIssue(row.number(), "row", ex.getMessage()));
            }
        }
        int invalidRows = (int) issues.stream().map(RowIssue::row).distinct().count();
        return new ImportPreview(parsed.totalRows(), toCreate + toUpdate, invalidRows, toCreate, toUpdate, sorted(issues));
    }

    /** Шаг 2: применение. Строки с ошибками пропускаются, остальные сохраняются. */
    @Transactional
    public ImportResult apply(MultipartFile file, ImportMode mode) {
        ParsedFile parsed = parse(file, mode);
        UserAccount actor = currentUser.requireEntity();
        List<RowIssue> issues = new ArrayList<>(parsed.issues());
        int created = 0;
        int updated = 0;
        for (ParsedRow row : parsed.rows()) {
            try {
                bookingService.upsertFromImport(row.existing(), row.request(), actor);
                if (row.existing() == null) created++; else updated++;
            } catch (BadRequestException | ConflictException ex) {
                issues.add(new RowIssue(row.number(), "row", ex.getMessage()));
            }
        }
        int skipped = parsed.totalRows() - created - updated;
        audit.record("BOOKING_CSV_IMPORTED", "BOOKING", null,
                "Импорт CSV: создано " + created + ", обновлено " + updated + ", пропущено " + skipped,
                Map.of("mode", mode.name(), "created", created, "updated", updated, "skipped", skipped));
        return new ImportResult(parsed.totalRows(), created, updated, skipped, sorted(issues));
    }

    /** Экспорт брони по текущему фильтру. Пишется порциями по 500 строк, а не всё сразу в память. */
    @Transactional(readOnly = true)
    public void export(String query, OutputStream output) throws IOException {
        var spec = BookingSpecifications.from(parser.parse(query));
        output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM - чтобы Excel понял UTF-8
        int exported = 0;
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(EXPORT_HEADERS).get())) {
            int page = 0;
            while (true) {
                var result = bookings.findAll(spec, PageRequest.of(page, 500, Sort.by("checkIn", "code")));
                for (Booking booking : result.getContent()) {
                    var dto = mapper.toResponse(booking);
                    printer.printRecord(safeCell(dto.code()), safeCell(dto.guestName()), safeCell(dto.guestEmail()),
                            dto.roomNumber(), dto.roomType(), dto.checkIn(), dto.checkOut(), dto.nights(),
                            dto.guestsCount(), dto.status(), dto.prepaid(), dto.totalPrice(), dto.riskLevel(),
                            dto.archived(), safeCell(dto.specialRequests()),
                            safeCell(String.join("|", dto.tags().stream().sorted().toList())));
                    exported++;
                }
                if (result.isLast()) break;
                page++;
            }
        }
        audit.record("BOOKING_CSV_EXPORTED", "BOOKING", null, "Экспорт CSV: " + exported + " броней",
                Map.of("query", query == null ? "" : query, "rows", exported));
    }

    // ---------------- разбор файла ----------------

    private ParsedFile parse(MultipartFile file, ImportMode mode) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Выберите непустой CSV-файл");
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true)
                .get();
        List<ParsedRow> rows = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        int total = 0;
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csv = CSVParser.parse(reader, format)) {
            Set<String> headers = new HashSet<>();
            csv.getHeaderNames().forEach(h -> headers.add(h.replace("﻿", "").trim()));
            for (String required : REQUIRED_HEADERS) {
                if (!headers.contains(required)) throw new BadRequestException("В файле нет колонки " + required);
            }
            for (CSVRecord record : csv) {
                total++;
                if (total > properties.importLimits().maxRows()) {
                    throw new BadRequestException("В файле больше " + properties.importLimits().maxRows() + " строк");
                }
                long rowNumber = record.getRecordNumber() + 1; // +1: первая строка файла - заголовок
                try {
                    BookingRequest request = toRequest(record);
                    Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);
                    if (!violations.isEmpty()) {
                        violations.forEach(v -> issues.add(new RowIssue(rowNumber, v.getPropertyPath().toString(), v.getMessage())));
                        continue;
                    }
                    Booking existing = null;
                    if (request.code() != null) {
                        String key = request.code().toUpperCase(Locale.ROOT);
                        if (!codesInFile.add(key)) {
                            issues.add(new RowIssue(rowNumber, "code", "Код " + key + " повторяется в файле"));
                            continue;
                        }
                        existing = bookings.findByCodeIgnoreCase(key).orElse(null);
                    } else if (mode == ImportMode.UPSERT_BY_CODE) {
                        issues.add(new RowIssue(rowNumber, "code", "В режиме обновления код брони обязателен"));
                        continue;
                    }
                    if (existing != null && mode == ImportMode.CREATE_ONLY) {
                        issues.add(new RowIssue(rowNumber, "code", "Бронь " + existing.getCode() + " уже существует"));
                        continue;
                    }
                    if (existing != null && existing.isArchived()) {
                        issues.add(new RowIssue(rowNumber, "code", "Бронь " + existing.getCode() + " в архиве"));
                        continue;
                    }
                    rows.add(new ParsedRow(rowNumber, request, existing));
                } catch (BadRequestException ex) {
                    issues.add(new RowIssue(rowNumber, "row", ex.getMessage()));
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException ex) {
            throw new BadRequestException("Не удалось прочитать CSV (нужен UTF-8, разделитель запятая)");
        }
        return new ParsedFile(total, rows, issues);
    }

    private BookingRequest toRequest(CSVRecord record) {
        String email = required(record, "guestEmail").toLowerCase(Locale.ROOT);
        Guest guest = guests.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("Гость с email " + email + " не найден"));
        int roomNumber = parseInt(required(record, "roomNumber"), "roomNumber");
        Room room = rooms.findByRoomNumber(roomNumber)
                .orElseThrow(() -> new BadRequestException("Номер " + roomNumber + " не найден"));
        return new BookingRequest(
                optional(record, "code"),
                guest.getId(),
                room.getId(),
                parseDate(required(record, "checkIn"), "checkIn"),
                parseDate(required(record, "checkOut"), "checkOut"),
                parseInt(required(record, "guestsCount"), "guestsCount"),
                parseStatus(required(record, "status")),
                parseBoolean(optional(record, "prepaid")),
                optional(record, "specialRequests"),
                parseTags(optional(record, "tags")),
                null);
    }

    private static String required(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) throw new BadRequestException("Не заполнена колонка " + column);
        return value;
    }

    private static String optional(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) return null;
        String value = record.get(column);
        if (value == null) return null;
        value = value.trim();
        if (value.startsWith("'")) value = value.substring(1); // снимаем защиту от формул из нашего же экспорта
        return value.isEmpty() ? null : value;
    }

    private static int parseInt(String value, String column) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(column + ": нужно целое число");
        }
    }

    private static LocalDate parseDate(String value, String column) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(column + ": дата в формате ГГГГ-ММ-ДД");
        }
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "да" -> true;
            case "false", "0", "no", "нет" -> false;
            default -> throw new BadRequestException("prepaid: true/false или да/нет");
        };
    }

    private static BookingStatus parseStatus(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "created", "создано" -> BookingStatus.CREATED;
            case "confirmed", "подтверждено" -> BookingStatus.CONFIRMED;
            case "completed", "завершено" -> BookingStatus.COMPLETED;
            case "cancelled", "canceled", "отменено" -> BookingStatus.CANCELLED;
            default -> throw new BadRequestException("status: CREATED, CONFIRMED, COMPLETED или CANCELLED");
        };
    }

    private static Set<String> parseTags(String value) {
        if (value == null) return Set.of();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isEmpty()).forEach(tags::add);
        return BookingService.normalizeTags(tags);
    }

    /** Защита от CSV-инъекции: значение, начинающееся с = + - @, Excel принял бы за формулу. */
    static String safeCell(String value) {
        if (value == null) return "";
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    private static List<RowIssue> sorted(List<RowIssue> issues) {
        return issues.stream().sorted((a, b) -> Long.compare(a.row(), b.row())).toList();
    }

    private record ParsedRow(long number, BookingRequest request, Booking existing) { }
    private record ParsedFile(int totalRows, List<ParsedRow> rows, List<RowIssue> issues) { }
}
