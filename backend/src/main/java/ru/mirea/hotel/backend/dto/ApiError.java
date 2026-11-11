package ru.mirea.hotel.backend.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Единый JSON-формат ошибки для всех клиентов (web, JavaFX, Android).
 * Stack trace, SQL и имена внутренних классов сюда не попадают.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> validationErrors) {
}
