package ru.mirea.hotel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.hotel.backend.domain.BookingNote;

import java.util.List;
import java.util.UUID;

/** Заметки к бронированиям: сначала закреплённые, затем новые. */
public interface BookingNoteRepository extends JpaRepository<BookingNote, UUID> {
    List<BookingNote> findByBookingIdOrderByPinnedDescCreatedAtDesc(UUID bookingId);
}
