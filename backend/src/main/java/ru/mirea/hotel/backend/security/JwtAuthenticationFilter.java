package ru.mirea.hotel.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.hotel.backend.repository.UserAccountRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * Читает access JWT из заголовка {@code Authorization: Bearer ...} и заполняет SecurityContext.
 *
 * <p>Роли и признак активности берутся из БД при каждом запросе, а не из токена:
 * если администратор отключил пользователя, его старый токен сразу перестаёт работать.</p>
 *
 * <p>Для страниц Swagger браузер не умеет слать заголовок Authorization, поэтому там
 * дополнительно принимается HttpOnly-cookie {@value #SWAGGER_COOKIE} (выдаётся только ADMIN).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String SWAGGER_COOKIE = "hotel_swagger";
    private final JwtService jwtService;
    private final UserAccountRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = bearerToken(request);
            if (token == null && isSwaggerRequest(request)) {
                token = cookieValue(request);
            }
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private static boolean isSwaggerRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/swagger-ui.html") || uri.startsWith("/swagger-ui/")
                || uri.equals("/v3/api-docs") || uri.startsWith("/v3/api-docs/");
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (SWAGGER_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void authenticate(String token) {
        if (token == null || token.isBlank()) return;
        try {
            Claims claims = jwtService.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            users.findById(userId)
                    .filter(user -> user.isEnabled())
                    .ifPresent(user -> {
                        UserPrincipal principal = UserPrincipal.from(user);
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
                    });
        } catch (RuntimeException ignored) {
            // неверный или просроченный токен: контекст пустой, дальше Spring Security ответит 401
            SecurityContextHolder.clearContext();
        }
    }
}
