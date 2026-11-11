package ru.mirea.hotel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Бронирования. Поиск с фильтрами идёт через {@link JpaSpecificationExecutor}
 * (Criteria API, значения передаются параметрами), сводка - через JPQL-запросы ниже.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    /** Следующее значение для кода брони BK-000NNN. */
    @Query(value = "select nextval('booking_code_seq')", nativeQuery = true)
    long nextCodeNumber();

    /**
     * Есть ли другая активная бронь этого номера на пересекающиеся даты.
     * Периоды [a1, a2) и [b1, b2) пересекаются, если a1 < b2 и a2 > b1.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.status <> ru.mirea.hotel.backend.domain.BookingStatus.CANCELLED
              and b.archived = false
              and b.id <> :excludeId
              and b.checkIn < :checkOut and b.checkOut > :checkIn
            """)
    boolean hasOverlap(UUID roomId, LocalDate checkIn, LocalDate checkOut, UUID excludeId);

    // ---------------- аналитика ----------------

    long countByArchivedFalse();
    long countByArchivedTrue();
    long countByArchivedFalseAndPrepaidTrue();
    long countByArchivedFalseAndPrepaidFalse();

    @Query("select avg(b.totalPrice) from Booking b where b.archived = false")
    Double averageTotalPriceForActive();

    @Query("select coalesce(sum(b.totalPrice), 0) from Booking b where b.archived = false and b.status = :status")
    BigDecimal sumTotalPriceByStatus(BookingStatus status);

    @Query("select r.roomNumber, count(b) from Booking b join b.room r " +
           "where b.archived = false group by r.roomNumber order by r.roomNumber")
    List<Object[]> countActiveByRoom();

    @Query("select r.roomType, count(b) from Booking b join b.room r " +
           "where b.archived = false group by r.roomType order by count(b) desc")
    List<Object[]> countActiveByRoomType();

    @Query("select b.status, count(b) from Booking b where b.archived = false group by b.status order by count(b) desc")
    List<Object[]> countActiveByStatus();

    @Query("select b.riskLevel, count(b) from Booking b where b.archived = false group by b.riskLevel order by count(b) desc")
    List<Object[]> countActiveByRisk();
}
