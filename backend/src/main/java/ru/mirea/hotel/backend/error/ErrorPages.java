package ru.mirea.hotel.backend.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

import java.util.Set;

/** Выбор между JSON и HTML-страницей ошибки (static/errors/*.html). */
final class ErrorPages {
    private static final Set<Integer> DEDICATED = Set.of(400, 401, 403, 404, 405, 409, 413, 415, 500);

    private ErrorPages() {
    }

    static boolean wantsHtml(HttpServletRequest request, String path) {
        if (path != null && path.startsWith("/api/")) return false;
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    static String pageFor(int status) {
        String name = DEDICATED.contains(status) ? String.valueOf(status) : status < 500 ? "4xx" : "5xx";
        return "static/errors/" + name + ".html";
    }
}
