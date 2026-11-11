package ru.mirea.hotel.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import ru.mirea.hotel.backend.controller.BookingController;
import ru.mirea.hotel.backend.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke-тест: весь Spring-контекст поднимается (на H2), бины связаны, первый администратор создан. */
@SpringBootTest
@ActiveProfiles("test")
class ContextSmokeTest {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private UserAccountRepository users;

    @Test
    void contextLoadsAndBootstrapAdminExists() {
        assertThat(context.getBean(BookingController.class)).isNotNull();
        assertThat(users.findByEmailIgnoreCase("admin@test.local")).isPresent();
    }
}
