package ru.mirea.hotel.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.hotel.backend.dto.PageResponse;
import ru.mirea.hotel.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.hotel.backend.dto.UserDtos.TemporaryPasswordResponse;
import ru.mirea.hotel.backend.dto.UserDtos.UpdateUserRequest;
import ru.mirea.hotel.backend.dto.UserDtos.UserResponse;
import ru.mirea.hotel.backend.service.UserAdminService;
import ru.mirea.hotel.backend.web.PageableFactory;

import java.util.UUID;

/** Управление пользователями - только ADMIN (аннотация на классе действует на все методы). */
@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserAdminService users;
    private final PageableFactory pageableFactory;

    public AdminUserController(UserAdminService users, PageableFactory pageableFactory) {
        this.users = users;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "email,asc") String sort) {
        return PageResponse.from(users.list(q, pageableFactory.users(page, size, sort)));
    }

    @PostMapping
    @Operation(summary = "Создание пользователя", description = "Временный пароль генерирует сервер и возвращает один раз")
    public ResponseEntity<TemporaryPasswordResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(users.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменение имени, логина, ролей и активности")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Сброс пароля", description = "Создаёт новый временный пароль, отзывает все сессии пользователя")
    public TemporaryPasswordResponse resetPassword(@PathVariable UUID id) {
        return users.resetPassword(id);
    }
}
