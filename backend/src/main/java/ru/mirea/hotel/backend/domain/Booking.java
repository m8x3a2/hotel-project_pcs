package ru.mirea.hotel.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OptimisticLock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Бронирование номера - основная сущность системы.
 *
 * <p>Вместо обычного удаления бронь архивируется ({@code archived = true}): запись,
 * заметки и история остаются, её можно восстановить. Окончательно удалить может только ADMIN.</p>
 */
@Entity
@Table(name = "bookings")
public class Booking extends AuditedVersionedEntity {
    @Id
    private UUID id;

    /** Внешний идентификатор (код брони), например BK-000014. По нему работает CSV-импорт. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "guests_count", nullable = false)
    private int guestsCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CREATED;

    /** Числовой показатель: стоимость = ночи * цена номера. Считается сервером. */
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /** Логический признак: внесена ли предоплата. */
    @Column(nullable = false)
    private boolean prepaid;

    @Column(name = "special_requests", length = 1000)
    private String specialRequests;

    /** Вычисляемый показатель - риск отмены. */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private CancellationRisk riskLevel = CancellationRisk.LOW;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by")
    private UserAccount archivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserAccount updatedBy;

    /*
     * excluded = true: Hibernate сам не поднимает version при изменении тегов. Иначе он делает отдельный
     * "update ... set version" и не обновляет поле version в объекте - клиент получил бы устаревшую
     * версию и следующий PUT упал бы с 409. Вместо этого setTags() помечает бронь изменённой (touch),
     * и version увеличивается обычным UPDATE.
     */
    @OptimisticLock(excluded = true)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "booking_tags", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags = new LinkedHashSet<>();

    protected Booking() {
    }

    public Booking(UUID id, String code, UserAccount creator) {
        this.id = id;
        this.code = code;
        this.createdBy = creator;
        this.updatedBy = creator;
    }

    /** Количество ночей (день выезда не считается). */
    public long getNights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Guest getGuest() { return guest; }
    public void setGuest(Guest guest) { this.guest = guest; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }
    public int getGuestsCount() { return guestsCount; }
    public void setGuestsCount(int guestsCount) { this.guestsCount = guestsCount; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public boolean isPrepaid() { return prepaid; }
    public void setPrepaid(boolean prepaid) { this.prepaid = prepaid; }
    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }
    public CancellationRisk getRiskLevel() { return riskLevel; }
    public void setRiskLevel(CancellationRisk riskLevel) { this.riskLevel = riskLevel; }
    public boolean isArchived() { return archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public UserAccount getArchivedBy() { return archivedBy; }
    public UserAccount getCreatedBy() { return createdBy; }
    public UserAccount getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UserAccount updatedBy) { this.updatedBy = updatedBy; }
    public Set<String> getTags() { return tags; }
    /** Меняет теги на месте и только при реальной разнице; изменение тегов - тоже изменение брони (version + 1). */
    public void setTags(Set<String> tags) {
        if (this.tags.equals(tags)) return;
        this.tags.clear();
        this.tags.addAll(tags);
        touch();
    }

    /** Перемещает бронь в архив. */
    public void archive(UserAccount actor) {
        archived = true;
        archivedAt = Instant.now();
        archivedBy = actor;
        updatedBy = actor;
    }

    /** Возвращает бронь из архива. */
    public void restore(UserAccount actor) {
        archived = false;
        archivedAt = null;
        archivedBy = null;
        updatedBy = actor;
    }
}
