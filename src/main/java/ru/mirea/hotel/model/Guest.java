package ru.mirea.hotel.model;

/**
 * Гость гостиницы (таблица guests).
 * Все поля private - это инкапсуляция: менять и читать их можно только через методы (геттеры/сеттеры).
 */
public class Guest {

    private int id;
    private String fullName;
    private String phone;
    private String email;

    /** Конструктор для нового гостя: id пока нет, его выдаст база данных. */
    public Guest(String fullName, String phone, String email) {
        this(0, fullName, phone, email); // вызываем второй конструктор
    }

    /** Конструктор для гостя, прочитанного из базы данных (id уже известен). */
    public Guest(int id, String fullName, String phone, String email) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /** toString() вызывается автоматически, когда объект печатают: System.out.println(guest). */
    @Override
    public String toString() {
        return "Гость #" + id + ": " + fullName + ", тел. " + phone + ", email " + email;
    }
}
