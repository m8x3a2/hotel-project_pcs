package ru.mirea.hotel.backend.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.BookingNote;
import ru.mirea.hotel.backend.domain.Role;
import ru.mirea.hotel.backend.domain.UserAccount;
import ru.mirea.hotel.backend.dto.NoteDtos.NoteRequest;
import ru.mirea.hotel.backend.dto.NoteDtos.NoteResponse;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.error.NotFoundException;
import ru.mirea.hotel.backend.repository.BookingNoteRepository;
import ru.mirea.hotel.backend.repository.BookingRepository;
import ru.mirea.hotel.backend.security.CurrentUserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Заметки к бронированию. Хранят автора и время. Изменить или удалить заметку
 * может только её автор или администратор.
 */
@Service
public class NoteService {
    private final BookingNoteRepository notes;
    private final BookingRepository bookings;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public NoteService(BookingNoteRepository notes, BookingRepository bookings,
                       CurrentUserService currentUser, AuditService audit) {
        this.notes = notes;
        this.bookings = bookings;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID bookingId) {
        requireBooking(bookingId);
        return notes.findByBookingIdOrderByPinnedDescCreatedAtDesc(bookingId).stream().map(NoteService::toResponse).toList();
    }

    @Transactional
    public NoteResponse create(UUID bookingId, NoteRequest request) {
        Booking booking = requireBooking(bookingId);
        UserAccount actor = currentUser.requireEntity();
        BookingNote note = new BookingNote(UUID.randomUUID(), booking, actor, request.body().trim(), request.pinned());
        notes.saveAndFlush(note);
        audit.record("NOTE_CREATED", "BOOKING_NOTE", note.getId(),
                "Добавлена заметка к брони " + booking.getCode(), Map.of("bookingId", bookingId));
        return toResponse(note);
    }

    @Transactional
    public NoteResponse update(UUID bookingId, UUID noteId, NoteRequest request) {
        BookingNote note = requireNote(bookingId, noteId);
        assertOwnerOrAdmin(note, currentUser.requireEntity());
        if (request.version() == null) throw new BadRequestException("Для изменения нужно поле version");
        if (request.version() != note.getVersion()) throw new ConflictException("Заметка уже изменена. Обновите список");
        note.setBody(request.body().trim());
        note.setPinned(request.pinned());
        notes.flush(); // в ответе - уже новая version
        audit.record("NOTE_UPDATED", "BOOKING_NOTE", noteId,
                "Изменена заметка к брони " + note.getBooking().getCode(), Map.of("bookingId", bookingId));
        return toResponse(note);
    }

    @Transactional
    public void delete(UUID bookingId, UUID noteId) {
        BookingNote note = requireNote(bookingId, noteId);
        assertOwnerOrAdmin(note, currentUser.requireEntity());
        notes.delete(note);
        audit.record("NOTE_DELETED", "BOOKING_NOTE", noteId,
                "Удалена заметка к брони " + note.getBooking().getCode(), Map.of("bookingId", bookingId));
    }

    private Booking requireBooking(UUID id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Бронь не найдена"));
    }

    private BookingNote requireNote(UUID bookingId, UUID noteId) {
        BookingNote note = notes.findById(noteId).orElseThrow(() -> new NotFoundException("Заметка не найдена"));
        if (!note.getBooking().getId().equals(bookingId)) throw new NotFoundException("Заметка не найдена");
        return note;
    }

    private static void assertOwnerOrAdmin(BookingNote note, UserAccount actor) {
        if (!note.getAuthor().getId().equals(actor.getId()) && !actor.getRoles().contains(Role.ADMIN)) {
            throw new AccessDeniedException("Изменять заметку может только автор или администратор");
        }
    }

    private static NoteResponse toResponse(BookingNote note) {
        return new NoteResponse(note.getId(), note.getBooking().getId(), note.getAuthor().getId(),
                note.getAuthor().getDisplayName(), note.getBody(), note.isPinned(),
                note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }
}
