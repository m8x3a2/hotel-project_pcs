package ru.mirea.hotel.model;

/**
 * Тип номера. enum - это перечисление: переменная такого типа может принимать
 * только одно из значений, перечисленных ниже (никаких "опечаток" вроде "STANDART").
 */
public enum RoomType {
    STANDARD("Стандарт"),
    DELUXE("Делюкс"),
    SUITE("Люкс");

    // У каждого значения enum есть своё поле - русское название для вывода на экран
    private final String title;

    RoomType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** При выводе на экран показываем русское название (в базе хранится name(): "STANDARD"). */
    @Override
    public String toString() {
        return title;
    }
}
