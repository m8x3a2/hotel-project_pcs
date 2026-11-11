package ru.mirea.hotel.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.config.AppProperties;
import ru.mirea.hotel.backend.domain.Role;
import ru.mirea.hotel.backend.dto.AuthDtos.AuthResponse;
import ru.mirea.hotel.backend.dto.AuthDtos.ChangePasswordRequest;
import ru.mirea.hotel.backend.dto.AuthDtos.ClientType;
import ru.mirea.hotel.backend.dto.AuthDtos.CurrentUser;
import ru.mirea.hotel.backend.dto.AuthDtos.InitialPasswordChangeRequest;
import ru.mirea.hotel.backend.dto.AuthDtos.LoginRequest;
import ru.mirea.hotel.backend.dto.AuthDtos.RefreshRequest;
import ru.mirea.hotel.backend.security.JwtAuthenticationFilter;
import ru.mirea.hotel.backend.service.AuthService;

import java.time.Duration;
import java.time.Instant;

/**
 * Авторизация.
 *
 * <p>Android/JavaFX (clientType=NATIVE, по умолчанию) получают refresh-токен в JSON и
 * присылают его в теле /refresh и /logout. Браузер (clientType=WEB) получает refresh-токен
 * только в HttpOnly-cookie: JavaScript страницы его не видит, украсть через XSS нельзя.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "hotel_refresh";
    private final AuthService auth;
    private final AppProperties properties;

    public AuthController(AuthService auth, AppProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse issued = auth.login(request);
        if (request.effectiveClientType() == ClientType.WEB) return forWeb(issued, response);
        return issued;
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody(required = false) RefreshRequest request,
                                @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                HttpServletResponse response) {
        boolean web = cookie != null && !cookie.isBlank();
        AuthResponse issued = auth.refresh(web ? cookie : request == null ? null : request.refreshToken());
        return web ? forWeb(issued, response) : issued;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                       HttpServletResponse response) {
        auth.logout(cookie != null ? cookie : request == null ? null : request.refreshToken());
        setCookie(response, REFRESH_COOKIE, "", "/api/auth", Duration.ZERO);
        setCookie(response, JwtAuthenticationFilter.SWAGGER_COOKIE, "", "/", Duration.ZERO);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return auth.me();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-initial-password")
    public ResponseEntity<Void> changeInitialPassword(@Valid @RequestBody InitialPasswordChangeRequest request) {
        auth.changeInitialPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Refresh-токен уходит в cookie и вырезается из JSON. Администратору - ещё cookie для Swagger UI. */
    private AuthResponse forWeb(AuthResponse issued, HttpServletResponse response) {
        setCookie(response, REFRESH_COOKIE, issued.refreshToken(), "/api/auth", until(issued.refreshExpiresAt()));
        boolean swagger = issued.user().roles().contains(Role.ADMIN) && !issued.user().passwordChangeRequired();
        setCookie(response, JwtAuthenticationFilter.SWAGGER_COOKIE, swagger ? issued.accessToken() : "", "/",
                swagger ? until(issued.accessExpiresAt()) : Duration.ZERO);
        return new AuthResponse(issued.accessToken(), issued.accessExpiresAt(), null,
                issued.refreshExpiresAt(), issued.user());
    }

    private void setCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static Duration until(Instant moment) {
        Duration duration = Duration.between(Instant.now(), moment);
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
