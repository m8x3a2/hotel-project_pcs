package ru.mirea.hotel.backend.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.mirea.hotel.backend.domain.Booking;
import ru.mirea.hotel.backend.domain.Guest;
import ru.mirea.hotel.backend.domain.Room;
import ru.mirea.hotel.backend.filter.BookingFilter.Comparison;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Превращает {@link BookingFilter} в JPA Specification (Criteria API).
 * Все значения уходят в SQL как параметры запроса, а не как текст.
 */
public final class BookingSpecifications {
    private BookingSpecifications() {
    }

    public static Specification<Booking> from(BookingFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Booking, Guest> guest = root.join("guest", JoinType.INNER);
            Join<Booking, Room> room = root.join("room", JoinType.INNER);

            if (filter.archived() != null) predicates.add(cb.equal(root.get("archived"), filter.archived()));
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.prepaid() != null) predicates.add(cb.equal(root.get("prepaid"), filter.prepaid()));
            if (filter.risk() != null) predicates.add(cb.equal(root.get("riskLevel"), filter.risk()));
            if (filter.roomNumber() != null) predicates.add(cb.equal(room.get("roomNumber"), filter.roomNumber()));
            if (filter.roomType() != null) predicates.add(cb.equal(room.get("roomType"), filter.roomType()));
            if (filter.tag() != null) {
                // подзапрос (member of), а не join: join по тегам размножил бы строки
                predicates.add(cb.isMember(filter.tag(), root.<java.util.Collection<String>>get("tags")));
            }
            filter.price().forEach(c -> predicates.add(compare(cb, root.get("totalPrice"), c)));
            filter.checkIn().forEach(c -> predicates.add(compare(cb, root.get("checkIn"), c)));

            if (filter.freeText() != null) {
                String like = "%" + escapeLike(filter.freeText()) + "%";
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("code")), like, '\\'),
                        cb.like(cb.lower(root.get("specialRequests")), like, '\\'),
                        cb.like(cb.lower(guest.get("fullName")), like, '\\'),
                        cb.like(cb.lower(guest.get("email")), like, '\\'),
                        cb.like(guest.get("phone"), like, '\\')));
                if (filter.freeText().matches("\\d{1,4}")) {
                    any.add(cb.equal(room.get("roomNumber"), Integer.parseInt(filter.freeText())));
                }
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static <T extends Comparable<? super T>> Predicate compare(CriteriaBuilder cb, Expression<T> field,
                                                                       Comparison<T> c) {
        return switch (c.operator()) {
            case LT -> cb.lessThan(field, c.value());
            case LTE -> cb.lessThanOrEqualTo(field, c.value());
            case EQ -> cb.equal(field, c.value());
            case GTE -> cb.greaterThanOrEqualTo(field, c.value());
            case GT -> cb.greaterThan(field, c.value());
        };
    }

    /** Экранирует спецсимволы LIKE, чтобы «%» или «_» в поиске искались буквально. */
    static String escapeLike(String value) {
        return value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
