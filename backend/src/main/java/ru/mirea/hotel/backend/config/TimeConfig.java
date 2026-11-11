package ru.mirea.hotel.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Часы приложения. «Сегодня» для гостиницы считается в её часовом поясе (Europe/Moscow),
 * а не в UTC контейнера. В тестах Clock можно подменить фиксированным.
 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timeZone()));
    }
}
