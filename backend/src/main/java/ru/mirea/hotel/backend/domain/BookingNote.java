package ru.mirea.hotel.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Заметка сотрудника к бронированию: договорённости с гостем, звонки, особые случаи. */
@Entity
@Table(name = "booking_notes")
public class BookingNote extends AuditedVersionedEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false, length = 4000)
    private String body;

    /** Закреплённая заметка показывается первой. */
    @Column(nullable = false)
    private boolean pinned;

    protected BookingNote() {
    }

    public BookingNote(UUID id, Booking booking, UserAccount author, String body, boolean pinned) {
        this.id = id;
        this.booking = booking;
        this.author = author;
        this.body = body;
        this.pinned = pinned;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public UserAccount getAuthor() { return author; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
