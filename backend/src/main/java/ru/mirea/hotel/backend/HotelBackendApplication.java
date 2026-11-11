package ru.mirea.hotel.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа backend КР3 (Spring Boot REST API гостиницы).
 *
 * <p>Здесь только запуск Spring: контроллеры, сервисы, репозитории и настройки
 * безопасности Spring находит сам по аннотациям в подпакетах.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HotelBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelBackendApplication.class, args);
    }
}
