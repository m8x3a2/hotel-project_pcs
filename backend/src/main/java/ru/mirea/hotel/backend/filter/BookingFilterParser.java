package ru.mirea.hotel.backend.filter;

import org.springframework.stereotype.Component;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;
import ru.mirea.hotel.backend.domain.RoomType;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.filter.BookingFilter.Comparison;
import ru.mirea.hotel.backend.filter.BookingFilter.Operator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Умная» строка поиска бронирований -> типизированный {@link BookingFilter}.
 *
 * <p>Пример: {@code Иванов номер:301 статус:подтверждено предоплата:нет риск:высокий тег:vip сумма>=20000 заезд>=2026-11-01 архив:все}.
 * Всё, что не похоже на команду, считается свободным текстом.</p>
 *
 * <p>Безопасность: строка пользователя никогда не склеивается с SQL/JPQL. Парсер знает только
 * фиксированный набор команд, а значения дальше идут в Criteria API как параметры запроса.</p>
 */
@Component
public class BookingFilterParser {
    private static final int MAX_LENGTH = 500;
    /** Токен - либо текст в кавычках, либо последовательность без пробелов. */
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
    private static final Pattern PRICE = Pattern.compile("^(?:сумма|цена|price)(<=|>=|=|<|>)(\\d{1,9}(?:[.,]\\d{1,2})?)$");
    private static final Pattern CHECK_IN = Pattern.compile("^(?:заезд|checkin)(<=|>=|=|<|>)(\\d{4}-\\d{2}-\\d{2})$");

    public BookingFilter parse(String source) {
        if (source == null || source.isBlank()) return BookingFilter.activeOnly();
        if (source.length() > MAX_LENGTH) throw new BadRequestException("Строка поиска длиннее " + MAX_LENGTH + " символов");

        List<String> free = new ArrayList<>();
        Integer roomNumber = null;
        RoomType roomType = null;
        BookingStatus status = null;
        Boolean prepaid = null;
        CancellationRisk risk = null;
        String tag = null;
        Boolean archived = false;
        List<Comparison<BigDecimal>> prices = new ArrayList<>();
        List<Comparison<LocalDate>> checkIns = new ArrayList<>();

        Matcher matcher = TOKEN.matcher(source.trim());
        while (matcher.find()) {
            String token = (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)).trim();
            if (token.isEmpty()) continue;
            String lower = token.toLowerCase(Locale.ROOT);

            Matcher price = PRICE.matcher(lower);
            if (price.matches()) {
                prices.add(new Comparison<>(operator(price.group(1)), new BigDecimal(price.group(2).replace(',', '.'))));
                continue;
            }
            Matcher checkIn = CHECK_IN.matcher(lower);
            if (checkIn.matches()) {
                checkIns.add(new Comparison<>(operator(checkIn.group(1)), date(checkIn.group(2))));
                continue;
            }

            int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1) {
                free.add(token);
                continue;
            }
            String key = lower.substring(0, colon);
            String value = token.substring(colon + 1).trim();
            String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
            switch (key) {
                case "номер", "room" -> roomNumber = roomNumber(value);
                case "тип", "type" -> roomType = roomType(normalized);
                case "статус", "status" -> status = status(normalized);
                case "предоплата", "prepaid" -> prepaid = yesNo(normalized, "предоплата");
                case "риск", "risk" -> risk = risk(normalized);
                case "тег", "tag" -> tag = value.toLowerCase(Locale.ROOT);
                case "архив", "archived" -> archived = archived(normalized);
                default -> free.add(token);
            }
        }
        String freeText = String.join(" ", free).trim();
        return new BookingFilter(freeText.isEmpty() ? null : freeText, roomNumber, roomType, status, prepaid, risk,
                tag, archived, List.copyOf(prices), List.copyOf(checkIns));
    }

    private static Operator operator(String value) {
        return switch (value) {
            case "<" -> Operator.LT;
            case "<=" -> Operator.LTE;
            case "=" -> Operator.EQ;
            case ">=" -> Operator.GTE;
            case ">" -> Operator.GT;
            default -> throw new BadRequestException("Неизвестный оператор сравнения: " + value);
        };
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Дата заезда должна быть в формате ГГГГ-ММ-ДД");
        }
    }

    private static Integer roomNumber(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Номер комнаты должен быть положительным числом");
        }
    }

    private static RoomType roomType(String value) {
        return switch (value) {
            case "стандарт", "standard" -> RoomType.STANDARD;
            case "делюкс", "deluxe" -> RoomType.DELUXE;
            case "люкс", "suite" -> RoomType.SUITE;
            default -> throw new BadRequestException("Неизвестный тип номера: " + value + " (стандарт, делюкс, люкс)");
        };
    }

    private static BookingStatus status(String value) {
        return switch (value) {
            case "создано", "создана", "новая", "created" -> BookingStatus.CREATED;
            case "подтверждено", "подтверждена", "confirmed" -> BookingStatus.CONFIRMED;
            case "завершено", "завершена", "completed" -> BookingStatus.COMPLETED;
            case "отменено", "отменена", "cancelled", "canceled" -> BookingStatus.CANCELLED;
            default -> throw new BadRequestException("Неизвестный статус: " + value);
        };
    }

    private static CancellationRisk risk(String value) {
        return switch (value) {
            case "нет", "не_применяется", "n/a", "not_applicable" -> CancellationRisk.NOT_APPLICABLE;
            case "низкий", "low" -> CancellationRisk.LOW;
            case "средний", "medium" -> CancellationRisk.MEDIUM;
            case "высокий", "high" -> CancellationRisk.HIGH;
            case "критический", "critical" -> CancellationRisk.CRITICAL;
            default -> throw new BadRequestException("Неизвестный уровень риска: " + value);
        };
    }

    private static Boolean yesNo(String value, String field) {
        return switch (value) {
            case "да", "yes", "true", "1" -> true;
            case "нет", "no", "false", "0" -> false;
            default -> throw new BadRequestException("Условие «" + field + "» принимает да/нет");
        };
    }

    /** архив:нет - активные, архив:да - только архивные, архив:все - без условия. */
    private static Boolean archived(String value) {
        if (value.equals("все") || value.equals("all")) return null;
        return yesNo(value, "архив");
    }
}
