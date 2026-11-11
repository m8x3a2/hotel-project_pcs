package ru.mirea.hotel.backend.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.domain.Guest;
import ru.mirea.hotel.backend.domain.Room;
import ru.mirea.hotel.backend.domain.RoomType;
import ru.mirea.hotel.backend.repository.BookingRepository;
import ru.mirea.hotel.backend.repository.GuestRepository;
import ru.mirea.hotel.backend.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Фильтр «по-настоящему»: строка -> Specification -> SQL-запрос к базе (H2) -> найденные коды. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingSearchIntegrationTest {
    @Autowired private BookingRepository bookings;
    @Autowired private GuestRepository guests;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingFilterParser parser;

    @BeforeEach
    void seed() {
        Guest ivanov = guests.save(new Guest(UUID.randomUUID(), "Иванов Иван", "+7 900 111-11-11", "ivanov@test.ru"));
        Guest petrova = guests.save(new Guest(UUID.randomUUID(), "Петрова Мария", "+7 900 222-22-22", "petrova@test.ru"));
        Room standard = rooms.save(new Room(UUID.randomUUID(), 101, RoomType.STANDARD, 2, new BigDecimal("2500")));
        Room suite = rooms.save(new Room(UUID.randomUUID(), 301, RoomType.SUITE, 4, new BigDecimal("7000")));
        save("T-1", ivanov, standard, BookingStatus.CONFIRMED, true, new BigDecimal("7500"), Set.of("vip"), false);
        save("T-2", petrova, suite, BookingStatus.CREATED, false, new BigDecimal("35000"), Set.of("family"), false);
        save("T-3", ivanov, suite, BookingStatus.CANCELLED, false, new BigDecimal("14000"), Set.of(), true);
    }

    @Test
    void defaultSearchHidesArchive() {
        assertThat(codes("")).containsExactly("T-1", "T-2");
        assertThat(codes("архив:да")).containsExactly("T-3");
        assertThat(codes("архив:все")).containsExactly("T-1", "T-2", "T-3");
    }

    @Test
    void filtersByReferenceStatusFlagTagAndNumber() {
        assertThat(codes("иванов архив:все")).containsExactly("T-1", "T-3");
        assertThat(codes("номер:301")).containsExactly("T-2");
        assertThat(codes("тип:стандарт")).containsExactly("T-1");
        assertThat(codes("статус:создано")).containsExactly("T-2");
        assertThat(codes("предоплата:да")).containsExactly("T-1");
        assertThat(codes("тег:family")).containsExactly("T-2");
        assertThat(codes("сумма>10000 архив:все")).containsExactly("T-2", "T-3");
        assertThat(codes("сумма<=7500")).containsExactly("T-1");
    }

    private List<String> codes(String query) {
        return bookings.findAll(BookingSpecifications.from(parser.parse(query)), PageRequest.of(0, 50, Sort.by("code")))
                .map(Booking::getCode).getContent();
    }

    private void save(String code, Guest guest, Room room, BookingStatus status, boolean prepaid,
                      BigDecimal price, Set<String> tags, boolean archived) {
        Booking booking = new Booking(UUID.randomUUID(), code, null);
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setCheckIn(LocalDate.of(2026, 12, 1));
        booking.setCheckOut(LocalDate.of(2026, 12, 3));
        booking.setGuestsCount(2);
        booking.setStatus(status);
        booking.setPrepaid(prepaid);
        booking.setTotalPrice(price);
        booking.setTags(tags);
        if (archived) booking.archive(null);
        bookings.save(booking);
    }
}
