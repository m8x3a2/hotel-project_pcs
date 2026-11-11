package ru.mirea.hotel.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTO аналитической сводки. Все счётчики (кроме total и archived) - по активным (не архивным) броням. */
public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record NamedCount(String name, long count) {
    }

    public record AnalyticsResponse(
            long total,
            long active,
            long archived,
            long prepaid,
            long notPrepaid,
            BigDecimal averageTotalPrice,
            BigDecimal completedRevenue,
            List<NamedCount> byRoom,
            List<NamedCount> byRoomType,
            List<NamedCount> byStatus,
            List<NamedCount> byRisk) {
    }
}
