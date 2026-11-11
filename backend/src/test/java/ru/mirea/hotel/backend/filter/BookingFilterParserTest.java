package ru.mirea.hotel.backend.filter;

import org.junit.jupiter.api.Test;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.CancellationRisk;
import ru.mirea.hotel.backend.domain.RoomType;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.filter.BookingFilter.Operator;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Разбор умной строки поиска: русские и английские команды, сравнения, ошибки. */
class BookingFilterParserTest {
    private final BookingFilterParser parser = new BookingFilterParser();

    @Test
    void emptyQueryMeansActiveBookingsOnly() {
        BookingFilter filter = parser.parse("  ");
        assertThat(filter.archived()).isFalse();
        assertThat(filter.freeText()).isNull();
    }

    @Test
    void parsesMixedRussianQuery() {
        BookingFilter filter = parser.parse(
                "Иванов номер:301 тип:люкс статус:подтверждено предоплата:нет риск:высокий тег:VIP сумма>=20000 заезд<2026-12-01");
        assertThat(filter.freeText()).isEqualTo("Иванов");
        assertThat(filter.roomNumber()).isEqualTo(301);
        assertThat(filter.roomType()).isEqualTo(RoomType.SUITE);
        assertThat(filter.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(filter.prepaid()).isFalse();
        assertThat(filter.risk()).isEqualTo(CancellationRisk.HIGH);
        assertThat(filter.tag()).isEqualTo("vip");
        assertThat(filter.price()).containsExactly(new BookingFilter.Comparison<>(Operator.GTE, new BigDecimal("20000")));
        assertThat(filter.checkIn()).containsExactly(new BookingFilter.Comparison<>(Operator.LT, LocalDate.of(2026, 12, 1)));
    }

    @Test
    void supportsEnglishKeysQuotedTextAndAllArchive() {
        BookingFilter filter = parser.parse("\"Петрова Мария\" status:cancelled archived:all price<5000,50");
        assertThat(filter.freeText()).isEqualTo("Петрова Мария");
        assertThat(filter.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(filter.archived()).isNull();
        assertThat(filter.price().get(0).value()).isEqualByComparingTo("5000.50");
    }

    @Test
    void rejectsUnknownValues() {
        assertThatThrownBy(() -> parser.parse("статус:потеряно")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> parser.parse("номер:abc")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> parser.parse("x".repeat(501))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void sqlLikeTextStaysPlainFreeText() {
        BookingFilter filter = parser.parse("'; drop table bookings; --");
        assertThat(filter.freeText()).isEqualTo("'; drop table bookings; --");
        assertThat(BookingSpecifications.escapeLike("100%_a")).isEqualTo("100\\%\\_a");
    }
}
