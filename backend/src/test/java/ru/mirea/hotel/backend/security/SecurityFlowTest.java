package ru.mirea.hotel.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий безопасности через HTTP:
 * 401 без токена -> вход -> 403 пока не сменён временный пароль -> смена -> доступ,
 * затем пользователь VIEWER читает, но не может создавать (403), и refresh-токен одноразовый.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class SecurityFlowTest {
    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_TEMP_PASSWORD = "Start-Password-2026!";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void fullSecurityScenario() throws Exception {
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().exists("X-Correlation-ID"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"wrong-Password-1\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode login = login(ADMIN_EMAIL, ADMIN_TEMP_PASSWORD);
        String adminToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();
        org.assertj.core.api.Assertions.assertThat(login.at("/user/passwordChangeRequired").asBoolean()).isTrue();

        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mvc.perform(post("/api/auth/change-initial-password").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Hotel-Permanent-2026!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        // refresh-токен одноразовый: второй обмен того же токена отклоняется
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // администратор создаёт пользователя VIEWER, сервер выдаёт временный пароль
        String created = mvc.perform(post("/api/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"viewer@test.local\",\"displayName\":\"Viewer\",\"roles\":[\"VIEWER\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String viewerTemp = json.readTree(created).get("temporaryPassword").asText();

        String viewerToken = login("viewer@test.local", viewerTemp).get("accessToken").asText();
        mvc.perform(post("/api/auth/change-initial-password").header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Read-Only-Access-2026#\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
        String booking = "{\"guestId\":\"" + java.util.UUID.randomUUID() + "\",\"roomId\":\"" + java.util.UUID.randomUUID()
                + "\",\"checkIn\":\"2030-01-10\",\"checkOut\":\"2030-01-12\",\"guestsCount\":1,\"status\":\"CREATED\"}";
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/admin/audit").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // в журнале аудита есть входы, смена пароля и создание пользователя
        mvc.perform(get("/api/admin/audit?q=LOGIN_FAILED").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        versionFromResponseIsUsableForUpdate(adminToken);
    }

    /** Регрессия: version из ответа на создание (с тегами) должна подходить для следующего PUT. */
    private void versionFromResponseIsUsableForUpdate(String token) throws Exception {
        String auth = "Bearer " + token;
        String room = mvc.perform(post("/api/rooms").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomNumber\":501,\"roomType\":\"DELUXE\",\"capacity\":2,\"pricePerNight\":4000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String guest = mvc.perform(post("/api/guests").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Тестов Тест\",\"phone\":\"+7 900 000-00-00\",\"email\":\"test@guest.ru\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String body = "{\"code\":\"T-100\",\"guestId\":\"" + json.readTree(guest).get("id").asText()
                + "\",\"roomId\":\"" + json.readTree(room).get("id").asText()
                + "\",\"checkIn\":\"2030-03-01\",\"checkOut\":\"2030-03-04\",\"guestsCount\":2,\"status\":\"CREATED\","
                + "\"tags\":[\"VIP\",\"family\"]";
        String created = mvc.perform(post("/api/bookings").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(12000.0))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        JsonNode booking = json.readTree(created);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/bookings/" + booking.get("id").asText())
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("CREATED", "CONFIRMED") + ",\"prepaid\":true,\"version\":" + booking.get("version").asLong() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.version").value(booking.get("version").asLong() + 1));
    }

    private JsonNode login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
