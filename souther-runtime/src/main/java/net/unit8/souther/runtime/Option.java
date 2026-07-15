package net.unit8.souther.runtime;

/**
 * The optional type from spec section 7.3: {@code Some(T)} or {@code None}.
 *
 * @param <T> the contained type
 */
public sealed interface Option<T> permits Option.Some, Option.None {

    record Some<T>(T value) implements Option<T> {}

    record None<T>() implements Option<T> {}

    static <T> Option<T> some(T value) {
        return new Some<>(value);
    }

    static <T> Option<T> none() {
        return new None<>();
    }

    /** Bridges a {@code java.util.Optional} (as produced by a Raoh optional-field decoder) to
     * the Souther {@code Option}. Called by generated decode bodies for {@code ?} fields. */
    static <T> Option<T> ofOptional(java.util.Optional<T> o) {
        return o.isPresent() ? new Some<>(o.get()) : new None<>();
    }

    default boolean isPresent() {
        return this instanceof Some<T>;
    }
}
