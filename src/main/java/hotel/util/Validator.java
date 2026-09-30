package hotel.util;

import hotel.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Проверка и нормализация входных данных (ФИО, телефон, email, паспорт, номер комнаты, даты).
 * Каждый метод validateXxx либо возвращает "очищенное" значение в едином формате,
 * либо выбрасывает BusinessException с понятным описанием ошибки.
 * Используется и в сервисном слое (обязательная проверка), и в консольном меню
 * (чтобы сразу попросить пользователя повторить ввод конкретного поля).
 */
public final class Validator {

    // ===== Ограничения (совпадают с размерами столбцов в sql/init.sql) =====
    public static final int FULL_NAME_MAX_LENGTH = 150;
    public static final int EMAIL_MAX_LENGTH = 150;

    public static final int MIN_ROOM_NUMBER = 101;
    public static final int MAX_ROOM_NUMBER = 999;

    public static final int MAX_NIGHTS = 30;             // максимальная длительность проживания
    public static final int MAX_DAYS_IN_ADVANCE = 365;   // бронировать можно не дальше чем на год вперёд

    // Слово ФИО: только буквы (русские или латинские), внутри допускаются дефис и апостроф
    // (Салтыков-Щедрин, O'Neil). Слов должно быть от 2 до 4 (фамилия, имя, отчество...).
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile(
            "^[A-Za-zА-Яа-яЁё]+(?:['-][A-Za-zА-Яа-яЁё]+)*(?: [A-Za-zА-Яа-яЁё]+(?:['-][A-Za-zА-Яа-яЁё]+)*){1,3}$");

    // Российский мобильный/городской номер: +7XXXXXXXXXX или 8XXXXXXXXXX (после удаления пробелов, скобок, дефисов)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(?:\\+7|8)(\\d{10})$");

    // Локальная часть: буквы, цифры и . _ % + - ; домен: части из букв/цифр/дефисов через точку; зона - минимум 2 буквы
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");

    // Паспорт РФ: серия (4 цифры) + номер (6 цифр)
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^\\d{10}$");

    private Validator() {
        // утилитный класс - объекты не создаются
    }

    // ===================== ГОСТЬ =====================

    /** Проверяет ФИО и возвращает его без лишних пробелов. */
    public static String validateFullName(String fullName) {
        String value = normalizeSpaces(requireNotBlank(fullName, "ФИО"));
        if (value.length() > FULL_NAME_MAX_LENGTH) {
            throw new BusinessException("ФИО слишком длинное (максимум " + FULL_NAME_MAX_LENGTH + " символов).");
        }
        if (!FULL_NAME_PATTERN.matcher(value).matches()) {
            throw new BusinessException("Некорректное ФИО: \"" + value + "\". Укажите минимум фамилию и имя " +
                    "через пробел, только буквы (допускаются дефис и апостроф). Пример: Иванов Иван Иванович");
        }
        return value;
    }

    /**
     * Проверяет телефон и приводит его к единому виду "+7 900 111-11-11".
     * Принимает варианты: +79001111111, 89001111111, +7 (900) 111-11-11, 8-900-111-11-11.
     */
    public static String validatePhone(String phone) {
        String value = requireNotBlank(phone, "телефон");
        if (!value.matches("[0-9+()\\-\\s]+")) {
            throw new BusinessException("Телефон может содержать только цифры, пробелы, скобки, дефисы и знак '+'.");
        }
        String compact = value.replaceAll("[\\s()\\-]", "");
        var matcher = PHONE_PATTERN.matcher(compact);
        if (!matcher.matches()) {
            throw new BusinessException("Некорректный телефон: \"" + value + "\". Нужен российский номер " +
                    "из 11 цифр, начинающийся с +7 или 8. Пример: +7 900 123-45-67");
        }
        String d = matcher.group(1); // 10 цифр после кода страны
        return "+7 " + d.substring(0, 3) + " " + d.substring(3, 6) + "-" + d.substring(6, 8) + "-" + d.substring(8);
    }

    /** Проверяет email и возвращает его в нижнем регистре. */
    public static String validateEmail(String email) {
        String value = requireNotBlank(email, "email").toLowerCase();
        if (value.length() > EMAIL_MAX_LENGTH) {
            throw new BusinessException("Email слишком длинный (максимум " + EMAIL_MAX_LENGTH + " символов).");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()
                || value.startsWith(".")
                || value.contains("..")
                || value.contains(".@")) {
            throw new BusinessException("Некорректный email: \"" + value + "\". Пример: ivanov@mail.ru");
        }
        return value;
    }

    /**
     * Проверяет номер паспорта РФ (серия 4 цифры + номер 6 цифр) и возвращает 10 цифр подряд.
     * Принимает варианты: 4500111111, 4500 111111, 45 00 111111.
     */
    public static String validatePassport(String passportNumber) {
        String value = requireNotBlank(passportNumber, "номер паспорта").replaceAll("\\s", "");
        if (!PASSPORT_PATTERN.matcher(value).matches()) {
            throw new BusinessException("Некорректный номер паспорта: \"" + passportNumber.trim() + "\". " +
                    "Нужно 10 цифр: серия (4) и номер (6). Пример: 4500 123456");
        }
        if (value.startsWith("0000") || value.endsWith("000000")) {
            throw new BusinessException("Серия или номер паспорта не могут состоять только из нулей.");
        }
        return value;
    }

    // ===================== БРОНИРОВАНИЕ =====================

    public static void validateId(int id, String what) {
        if (id <= 0) {
            throw new BusinessException("ID " + what + " должен быть положительным числом.");
        }
    }

    /**
     * Номер комнаты - трёхзначный: первая цифра - этаж (1-9), две последние - номер на этаже (01-99).
     * Например 101, 205, 310. Номера вида 100, 200 не существуют.
     */
    public static void validateRoomNumber(int roomNumber) {
        if (roomNumber < MIN_ROOM_NUMBER || roomNumber > MAX_ROOM_NUMBER || roomNumber % 100 == 0) {
            throw new BusinessException("Некорректный номер комнаты: " + roomNumber + ". Номер должен быть " +
                    "трёхзначным (этаж + номер на этаже), от " + MIN_ROOM_NUMBER + " до " + MAX_ROOM_NUMBER +
                    ", например 101 или 205.");
        }
    }

    /** Общие проверки дат: обе заданы, выезд позже заезда, срок проживания не больше MAX_NIGHTS. */
    public static void validateStayDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("Даты заезда и выезда обязательны.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessException("Дата выезда должна быть позже даты заезда.");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new BusinessException("Слишком долгое проживание (количество ночей: " + nights + "). " +
                    "Максимум - " + MAX_NIGHTS + " ночей в одном бронировании.");
        }
    }

    /** Дата заезда нового бронирования: не в прошлом и не дальше чем на MAX_DAYS_IN_ADVANCE дней вперёд. */
    public static void validateCheckInForNewBooking(LocalDate checkIn, LocalDate today) {
        if (checkIn == null) {
            throw new BusinessException("Дата заезда обязательна.");
        }
        if (checkIn.isBefore(today)) {
            throw new BusinessException("Дата заезда " + checkIn + " уже прошла (сегодня " + today + ").");
        }
        if (checkIn.isAfter(today.plusDays(MAX_DAYS_IN_ADVANCE))) {
            throw new BusinessException("Бронировать можно не более чем на " + MAX_DAYS_IN_ADVANCE +
                    " дней вперёд (до " + today.plusDays(MAX_DAYS_IN_ADVANCE) + ").");
        }
    }

    // ===================== ВСПОМОГАТЕЛЬНЫЕ =====================

    private static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Поле '" + fieldName + "' не может быть пустым.");
        }
        return value.trim();
    }

    /** Заменяет несколько пробелов подряд одним. */
    private static String normalizeSpaces(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
