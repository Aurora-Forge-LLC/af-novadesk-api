package com.af.novadesk.api.common.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.function.Supplier;

public final class SpecUtils {

    private SpecUtils() {}

    /** Case-insensitive LIKE on a direct field of a Root or Join. */
    public static Predicate likeLower(
            CriteriaBuilder cb, Path<?> path, String field, String value) {
        return cb.like(cb.lower(path.get(field)), "%" + value.toLowerCase() + "%");
    }

    /** Case-insensitive LIKE on a direct field (Root overload for backward compatibility). */
    public static <T> Predicate likeLower(
            CriteriaBuilder cb, Root<T> root, String field, String value) {
        return likeLower(cb, (Path<?>) root, field, value);
    }

    /** Case-insensitive LIKE on a joined entity field (LEFT join). */
    public static <T> Predicate likeJoined(
            CriteriaBuilder cb, Root<T> root, String join, String field, String value) {
        return cb.like(
                cb.lower(root.join(join, JoinType.LEFT).get(field)),
                "%" + value.toLowerCase() + "%"
        );
    }

    /** Adds a predicate only when value is non-null. */
    public static void addIfPresent(List<Predicate> list, Object value, Supplier<Predicate> pred) {
        if (value != null) list.add(pred.get());
    }

    /** Adds a LIKE predicate only when the string is non-blank. */
    public static void addLikeIfPresent(List<Predicate> list, String value, Supplier<Predicate> pred) {
        if (value != null && !value.isBlank()) list.add(pred.get());
    }
}
