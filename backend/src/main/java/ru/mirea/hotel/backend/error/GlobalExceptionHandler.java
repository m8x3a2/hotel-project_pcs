package ru.mirea.hotel.backend.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.mirea.hotel.backend.dto.ApiError;
import ru.mirea.hotel.backend.web.CorrelationIdFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единая обработка ошибок. Любое исключение превращается в {@link ApiError} с нужным HTTP-кодом:
 * 400 - неверные данные, 401 - нет входа, 403 - нет прав, 404 - нет записи,
 * 409 - конфликт версий или уникальности, 500 - непредвиденная ошибка (stack trace только в лог сервера).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте заполнение полей", request, fields);
    }

    @ExceptionHandler({BadRequestException.class, ConstraintViolationException.class})
    public ResponseEntity<?> badRequest(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            MultipartException.class
    })
    public ResponseEntity<?> malformed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Некорректный запрос: проверьте формат JSON, параметры и типы значений", request, Map.of());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<?> authentication(AuthenticationFailedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<?> accessDenied(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof AccessDeniedException && ex.getMessage() != null
                && !ex.getMessage().startsWith("Access Denied") ? ex.getMessage() : "Недостаточно прав для операции";
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, request, Map.of());
    }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<?> notFound(Exception ex, HttpServletRequest request) {
        String message = ex instanceof NotFoundException ? ex.getMessage() : "Ресурс не найден";
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message, request, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> methodNotAllowed(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP-метод не поддерживается", request, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> unsupportedMediaType(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Неподдерживаемый Content-Type", request, Map.of());
    }

    @ExceptionHandler({ConflictException.class, OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<?> conflict(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof ConflictException ? ex.getMessage()
                : "Запись уже изменил другой пользователь. Обновите данные и повторите";
        return build(HttpStatus.CONFLICT, "CONFLICT", message, request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // имя ограничения БД клиенту не показываем - пишем в лог
        log.warn("Data integrity violation, correlationId={}: {}", MDC.get(CorrelationIdFilter.MDC_KEY),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Данные конфликтуют с уже существующими (дубликат или занятый номер)", request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Файл больше 5 МБ", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error, correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Внутренняя ошибка сервера. Сообщите администратору correlation ID", request, Map.of());
    }

    /** /api/** всегда получает JSON; браузер, открывший обычную страницу, - HTML-страницу ошибки. */
    private static ResponseEntity<?> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request, Map<String, String> fields) {
        if (ErrorPages.wantsHtml(request, request.getRequestURI())) {
            return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                    .body(new ClassPathResource(ErrorPages.pageFor(status.value())));
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI(),
                        MDC.get(CorrelationIdFilter.MDC_KEY), fields));
    }
}
