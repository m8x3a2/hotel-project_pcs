package hotel.exception;

/**
 * Выбрасывается, когда запись (гость или бронирование) с указанным ID не найдена в базе данных.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
