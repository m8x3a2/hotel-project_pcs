package ru.mirea.hotel.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Бронирование номера (таблица bookings) - основная сущность системы.
 * Связано с гостем (guestId -> guests.id) и номером (roomId -> rooms.id).
 */
public class Booking {

    private int id;
    private int guestId;          // внешний ключ на гостя
    private int roomId;           // внешний ключ на номер
    private LocalDate checkIn;    // дата заезда
    private LocalDate checkOut;   // дата выезда
    private int guestsCount;      // сколько человек будет жить
    private BookingStatus status;
    private double totalPrice;    // итоговая стоимость (считает сервис)

    // Поля только для удобного вывода на экран. В таблице bookings их нет -
    // репозиторий заполняет их из таблиц guests и rooms через JOIN.
    private String guestName;
    private int roomNumber;
    private RoomType roomType;

    /** Конструктор для нового бронирования (id выдаст база, статус и цену задаст сервис). */
    public Booking(int guestId, int roomId, LocalDate checkIn, LocalDate checkOut, int guestsCount) {
        this(0, guestId, roomId, checkIn, checkOut, guestsCount, BookingStatus.CREATED, 0);
    }

    /** Конструктор для бронирования, прочитанного из базы данных. */
    public Booking(int id, int guestId, int roomId, LocalDate checkIn, LocalDate checkOut,
                   int guestsCount, BookingStatus status, double totalPrice) {
        this.id = id;
        this.guestId = guestId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guestsCount = guestsCount;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    /** Количество ночей проживания (разница между датами в днях). */
    public long getNights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getGuestId() {
        return guestId;
    }

    public void setGuestId(int guestId) {
        this.guestId = guestId;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDate checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate checkOut) {
        this.checkOut = checkOut;
    }

    public int getGuestsCount() {
        return guestsCount;
    }

    public void setGuestsCount(int guestsCount) {
        this.guestsCount = guestsCount;
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

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
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

    @Override
    public String toString() {
        return "Бронирование #" + id
                + "\n  Гость:   " + guestName + " (ID " + guestId + ")"
                + "\n  Номер:   " + roomNumber + " (" + roomType + ", ID " + roomId + ")"
                + "\n  Даты:    " + checkIn + " - " + checkOut + " (ночей: " + getNights() + ")"
                + "\n  Гостей:  " + guestsCount
                + "\n  Статус:  " + status
                + "\n  Сумма:   " + String.format("%.2f", totalPrice) + " руб.";
    }
}
