package ru.mirea.hotel.backend.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.dto.ApiError;
import ru.mirea.hotel.backend.web.CorrelationIdFilter;

import java.time.Instant;
import java.util.Map;

/**
 * Ошибки, которые произошли вне контроллеров (до Spring MVC) и попали на /error.
 * Заменяет стандартную страницу Spring Boot «Whitelabel Error Page» и её JSON с лишними полями.
 */
@RestController
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> error(HttpServletRequest request) {
        int status = resolveStatus(request);
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = original == null ? request.getRequestURI() : String.valueOf(original);
        if (ErrorPages.wantsHtml(request, path)) {
            return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
                    .body(new ClassPathResource(ErrorPages.pageFor(status)));
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        String reason = httpStatus == null ? "Ошибка" : httpStatus.getReasonPhrase();
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new ApiError(Instant.now(), status, status >= 500 ? "INTERNAL_ERROR" : "CLIENT_ERROR",
                        reason, path, MDC.get(CorrelationIdFilter.MDC_KEY), Map.of()));
    }

    private static int resolveStatus(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (value instanceof Integer code) return code;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 500;
        }
    }
}
