package ru.mirea.hotel.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.hotel.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkRequest;
import ru.mirea.hotel.backend.dto.BookingDtos.BulkResult;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportMode;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportPreview;
import ru.mirea.hotel.backend.dto.ImportDtos.ImportResult;
import ru.mirea.hotel.backend.dto.PageResponse;
import ru.mirea.hotel.backend.service.BookingService;
import ru.mirea.hotel.backend.service.CsvBookingService;
import ru.mirea.hotel.backend.web.PageableFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Бронирования - основная сущность («items» из задания).
 * Читать могут все роли, изменять - MANAGER и ADMIN, удалять окончательно - только ADMIN.
 */
@RestController
@RequestMapping("/api/bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private final BookingService bookings;
    private final CsvBookingService csv;
    private final PageableFactory pageableFactory;

    public BookingController(BookingService bookings, CsvBookingService csv, PageableFactory pageableFactory) {
        this.bookings = bookings;
        this.csv = csv;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    @Operation(summary = "Список с поиском, фильтрами, сортировкой и пагинацией",
            description = "q - умная строка: «Иванов номер:301 тип:люкс статус:подтверждено предоплата:нет "
                    + "риск:высокий тег:vip сумма>=20000 заезд>=2026-11-01 архив:нет|да|все». "
                    + "sort - поле,направление; поля: code, checkIn, checkOut, price, guests, status, risk, guest, room, created, updated.")
    public PageResponse<BookingResponse> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "1..100") int size,
            @RequestParam(defaultValue = "checkIn,desc") String sort) {
        return PageResponse.from(bookings.search(q, pageableFactory.bookings(page, size, sort)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER','MANAGER','ADMIN')")
    public BookingResponse get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(201).body(bookings.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Изменение", description = "Нужно передать version из последнего чтения, иначе 409")
    public BookingResponse update(@PathVariable UUID id, @Valid @RequestBody BookingRequest request) {
        return bookings.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Архивирование (вместо удаления)")
    public BookingResponse archive(@PathVariable UUID id) {
        return bookings.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public BookingResponse restore(@PathVariable UUID id) {
        return bookings.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Окончательное удаление архивной брони",
            description = "Только ADMIN. В заголовке X-Confirm-Delete повторить код брони, например BK-000004")
    public ResponseEntity<Void> permanentDelete(@PathVariable UUID id,
                                                @RequestHeader(name = "X-Confirm-Delete", required = false) String confirmation) {
        bookings.permanentDelete(id, confirmation);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Массовая операция: ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return bookings.bulk(request);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Шаг 1 импорта: проверка CSV без записи в базу")
    public ImportPreview previewImport(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Шаг 2 импорта: применение корректных строк")
    public ImportResult applyImport(@RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.apply(file, mode);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Operation(summary = "Экспорт в CSV с учётом фильтра q")
    public void export(@RequestParam(defaultValue = "") String q, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"bookings-" + LocalDate.now() + ".csv\"");
        csv.export(q, response.getOutputStream());
    }
}
