package hotel.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Бронирование номера в гостинице — основная сущность предметной области.
 * Связана с гостем (User) по полю userId (внешний ключ).
 */
public class Booking {

    private int id;
    private int userId;
    private int roomNumber;
    private RoomType roomType;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private BookingStatus status;
    private double totalPrice;
    private LocalDateTime createdAt;

    /**
     * Конструктор для нового бронирования (id и createdAt заполнит база данных).
     */
    public Booking(int userId, int roomNumber, RoomType roomType,
                    LocalDate checkInDate, LocalDate checkOutDate,
                    BookingStatus status, double totalPrice) {
        this.userId = userId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    /**
     * Конструктор для бронирования, загруженного из базы данных (все поля известны).
     */
    public Booking(int id, int userId, int roomNumber, RoomType roomType,
                    LocalDate checkInDate, LocalDate checkOutDate,
                    BookingStatus status, double totalPrice, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = status;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
    }

    /** Количество ночей проживания (удобный вычисляемый метод). */
    public long getNightsCount() {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking)) return false;
        Booking booking = (Booking) o;
        return id == booking.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Бронирование #" + id +
                " | Гость ID: " + userId +
                " | Номер: " + roomNumber +
                " | Тип: " + roomType +
                " | Заезд: " + checkInDate +
                " | Выезд: " + checkOutDate +
                " | Статус: " + status +
                " | Стоимость: " + totalPrice + " руб.";
    }
}
