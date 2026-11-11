package ru.mirea.hotel.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.BookingStatus;
import ru.mirea.hotel.backend.dto.AnalyticsDtos.AnalyticsResponse;
import ru.mirea.hotel.backend.dto.AnalyticsDtos.NamedCount;
import ru.mirea.hotel.backend.repository.BookingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Сводка для аналитической панели. Всё считает PostgreSQL (count/avg/sum/group by), а не Java. */
@Service
public class AnalyticsService {
    private final BookingRepository bookings;

    public AnalyticsService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        Double average = bookings.averageTotalPriceForActive();
        return new AnalyticsResponse(
                bookings.count(),
                bookings.countByArchivedFalse(),
                bookings.countByArchivedTrue(),
                bookings.countByArchivedFalseAndPrepaidTrue(),
                bookings.countByArchivedFalseAndPrepaidFalse(),
                average == null ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                bookings.sumTotalPriceByStatus(BookingStatus.COMPLETED).setScale(2, RoundingMode.HALF_UP),
                map(bookings.countActiveByRoom()),
                map(bookings.countActiveByRoomType()),
                map(bookings.countActiveByStatus()),
                map(bookings.countActiveByRisk()));
    }

    private static List<NamedCount> map(List<Object[]> rows) {
        return rows.stream().map(row -> new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }
}
