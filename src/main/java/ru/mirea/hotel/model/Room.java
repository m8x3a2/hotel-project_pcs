package ru.mirea.hotel.model;

/**
 * Номер гостиницы (таблица rooms).
 */
public class Room {

    private int id;
    private int roomNumber;       // номер на двери, например 101
    private RoomType type;
    private int capacity;         // сколько человек вмещает
    private double pricePerNight; // цена за одну ночь, руб.

    /** Конструктор для нового номера (id выдаст база данных). */
    public Room(int roomNumber, RoomType type, int capacity, double pricePerNight) {
        this(0, roomNumber, type, capacity, pricePerNight);
    }

    /** Конструктор для номера, прочитанного из базы данных. */
    public Room(int id, int roomNumber, RoomType type, int capacity, double pricePerNight) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.type = type;
        this.capacity = capacity;
        this.pricePerNight = pricePerNight;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(int roomNumber) {
        this.roomNumber = roomNumber;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(double pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    @Override
    public String toString() {
        return "Номер " + roomNumber + " (ID " + id + "): " + type + ", мест: " + capacity
                + ", цена за ночь: " + String.format("%.2f", pricePerNight) + " руб.";
    }
}
