package ru.mirea.hotel.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Описание API для Swagger UI (/swagger-ui.html) и OpenAPI JSON (/v3/api-docs). */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI hotelOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Hotel Booking REST API").version("1.0.0")
                        .description("Бронирование номеров гостиницы. Один API для web-интерфейса, "
                                + "JavaFX и Android (КР4). Авторизация: Bearer JWT из POST /api/auth/login."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
