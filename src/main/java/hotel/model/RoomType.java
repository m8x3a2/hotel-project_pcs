package hotel.model;

/**
 * Тип номера гостиницы. У каждого типа своя цена за одну ночь проживания.
 * Цена используется сервисом бронирований для автоматического расчёта стоимости.
 */
public enum RoomType {
    STANDARD(2000.0),
    DELUXE(3500.0),
    SUITE(5000.0),
    FAMILY(4500.0);

    private final double pricePerNight;

    RoomType(double pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public double getPricePerNight() {
        return pricePerNight;
    }
}
