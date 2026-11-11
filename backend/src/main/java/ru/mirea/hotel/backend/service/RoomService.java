package ru.mirea.hotel.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.hotel.backend.domain.Room;
import ru.mirea.hotel.backend.dto.RoomDtos.RoomRequest;
import ru.mirea.hotel.backend.dto.RoomDtos.RoomResponse;
import ru.mirea.hotel.backend.error.BadRequestException;
import ru.mirea.hotel.backend.error.ConflictException;
import ru.mirea.hotel.backend.error.NotFoundException;
import ru.mirea.hotel.backend.repository.RoomRepository;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Справочник номеров. Номер не удаляется физически - только включается/отключается. */
@Service
public class RoomService {
    private final RoomRepository rooms;
    private final AuditService audit;

    public RoomService(RoomRepository rooms, AuditService audit) {
        this.rooms = rooms;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list(boolean activeOnly) {
        return (activeOnly ? rooms.findByActiveTrueOrderByRoomNumberAsc() : rooms.findAllByOrderByRoomNumberAsc())
                .stream().map(RoomService::toResponse).toList();
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (rooms.existsByRoomNumber(request.roomNumber())) {
            throw new ConflictException("Номер " + request.roomNumber() + " уже есть в справочнике");
        }
        Room room = new Room(UUID.randomUUID(), request.roomNumber(), request.roomType(), request.capacity(),
                request.pricePerNight().setScale(2, RoundingMode.HALF_UP));
        if (request.active() != null) room.setActive(request.active());
        rooms.saveAndFlush(room);
        audit.record("ROOM_CREATED", "ROOM", room.getId(), "Добавлен номер " + room.getRoomNumber(), Map.of());
        return toResponse(room);
    }

    @Transactional
    public RoomResponse update(UUID id, RoomRequest request) {
        Room room = require(id);
        verifyVersion(request.version(), room.getVersion());
        rooms.findByRoomNumber(request.roomNumber()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Номер " + request.roomNumber() + " уже есть в справочнике");
        });
        room.setRoomNumber(request.roomNumber());
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setPricePerNight(request.pricePerNight().setScale(2, RoundingMode.HALF_UP));
        if (request.active() != null) room.setActive(request.active());
        rooms.flush(); // в ответе - уже новая version
        audit.record("ROOM_UPDATED", "ROOM", id, "Изменён номер " + room.getRoomNumber(), Map.of());
        return toResponse(room);
    }

    @Transactional
    public RoomResponse setActive(UUID id, boolean active, long version) {
        Room room = require(id);
        verifyVersion(version, room.getVersion());
        if (room.isActive() == active) {
            throw new BadRequestException("Номер уже " + (active ? "включён" : "отключён"));
        }
        room.setActive(active);
        rooms.flush();
        audit.record(active ? "ROOM_ENABLED" : "ROOM_DISABLED", "ROOM", id,
                (active ? "Включён" : "Отключён") + " номер " + room.getRoomNumber(), Map.of());
        return toResponse(room);
    }

    private Room require(UUID id) {
        return rooms.findById(id).orElseThrow(() -> new NotFoundException("Номер не найден"));
    }

    private static void verifyVersion(Long requested, long actual) {
        if (requested == null) throw new BadRequestException("Для изменения нужно поле version");
        if (requested != actual) throw new ConflictException("Номер уже изменён. Обновите справочник");
    }

    static RoomResponse toResponse(Room room) {
        return new RoomResponse(room.getId(), room.getRoomNumber(), room.getRoomType(), room.getCapacity(),
                room.getPricePerNight(), room.isActive(), room.getCreatedAt(), room.getUpdatedAt(), room.getVersion());
    }
}
