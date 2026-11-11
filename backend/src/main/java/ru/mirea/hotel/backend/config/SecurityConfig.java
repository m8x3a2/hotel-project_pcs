package ru.mirea.hotel.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.mirea.hotel.backend.dto.ApiError;
import ru.mirea.hotel.backend.security.JwtAuthenticationFilter;
import ru.mirea.hotel.backend.security.PasswordChangeRequiredFilter;
import ru.mirea.hotel.backend.web.CorrelationIdFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Настройки Spring Security.
 *
 * <ul>
 *   <li>Сессий на сервере нет (STATELESS): каждый запрос несёт access JWT в заголовке Authorization.</li>
 *   <li>Открыты только статика web-интерфейса, вход/refresh/выход и health.</li>
 *   <li>Swagger - только ADMIN. Права на отдельные методы - аннотации {@code @PreAuthorize} в контроллерах.</li>
 *   <li>401/403 возвращаются в едином JSON-формате {@link ApiError}.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost 12: ~0.2-0.3 c на проверку - незаметно для человека, дорого для перебора
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   PasswordChangeRequiredFilter passwordChangeFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                // CSRF не нужен: API не использует cookie для авторизации запросов,
                // а refresh-cookie имеет SameSite=Strict и путь /api/auth.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/favicon.svg",
                                "/error", "/errors/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // неизвестный web-адрес (не /api) уходит в MVC и получает обычный 404
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(request, response, objectMapper, 401, "UNAUTHORIZED", "Требуется авторизация"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(request, response, objectMapper, 403, "ACCESS_DENIED", "Недостаточно прав для операции")))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                        + "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(passwordChangeFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /** API получает JSON, браузер при обычной навигации - HTML-страницу ошибки. */
    private static void writeError(HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper,
                                   int status, String code, String message) throws IOException, ServletException {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        boolean html = !uri.startsWith("/api/") && accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
        response.setStatus(status);
        if (html) {
            request.getRequestDispatcher("/errors/" + status + ".html").forward(request, response);
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status, code, message,
                uri, response.getHeader(CorrelationIdFilter.HEADER), Map.of()));
    }

    /*
     * Фильтры JWT объявлены как @Component, и Spring Boot по умолчанию зарегистрировал бы их
     * ещё раз как обычные servlet-фильтры. Они должны работать только внутри цепочки Security.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PasswordChangeRequiredFilter> passwordFilterRegistration(PasswordChangeRequiredFilter filter) {
        FilterRegistrationBean<PasswordChangeRequiredFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().asList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER, "X-Confirm-Delete"));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
