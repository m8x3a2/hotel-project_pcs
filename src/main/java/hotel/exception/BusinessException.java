package hotel.exception;

/**
 * Выбрасывается при нарушении бизнес-правил системы
 * (некорректные данные, запрещённый переход статуса, пересечение дат брони и т.д.).
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
