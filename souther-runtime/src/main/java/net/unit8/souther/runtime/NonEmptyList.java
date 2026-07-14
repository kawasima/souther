package net.unit8.souther.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * A list guaranteed to hold at least one element. Used as the failure type of decoders
 * so that at least one {@link DecodeError} is always present (spec section 7.3, 10.1).
 *
 * @param head the first element
 * @param tail the remaining elements (possibly empty)
 * @param <T>  the element type
 */
public record NonEmptyList<T>(T head, List<T> tail) {

    public NonEmptyList {
        tail = List.copyOf(tail);
    }

    public static <T> NonEmptyList<T> of(T head) {
        return new NonEmptyList<>(head, List.of());
    }

    @SafeVarargs
    public static <T> NonEmptyList<T> of(T head, T... rest) {
        return new NonEmptyList<>(head, List.of(rest));
    }

    /** Returns all elements as a plain list, head first. */
    public List<T> toList() {
        var all = new ArrayList<T>(tail.size() + 1);
        all.add(head);
        all.addAll(tail);
        return List.copyOf(all);
    }

    public int size() {
        return 1 + tail.size();
    }
}
