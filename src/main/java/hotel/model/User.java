package hotel.model;

import java.util.Objects;

/**
 * Гость гостиницы (пользователь системы).
 * Все поля приватные (инкапсуляция), доступ только через геттеры/сеттеры.
 */
public class User {

    private int id;
    private String fullName;
    private String phone;
    private String email;
    private String passportNumber;

    /**
     * Конструктор для нового гостя (id ещё не присвоен базой данных).
     */
    public User(String fullName, String phone, String email, String passportNumber) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.passportNumber = passportNumber;
    }

    /**
     * Конструктор для гостя, уже загруженного из базы данных (id известен).
     */
    public User(int id, String fullName, String phone, String email, String passportNumber) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.passportNumber = passportNumber;
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

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Гость #" + id +
                " | ФИО: " + fullName +
                " | Телефон: " + phone +
                " | Email: " + email +
                " | Паспорт: " + passportNumber;
    }
}
