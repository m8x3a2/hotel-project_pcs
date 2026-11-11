package ru.mirea.hotel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.hotel.backend.domain.Room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Справочник номеров. */
public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByRoomNumber(int roomNumber);
    boolean existsByRoomNumber(int roomNumber);
    List<Room> findAllByOrderByRoomNumberAsc();
    List<Room> findByActiveTrueOrderByRoomNumberAsc();
}
