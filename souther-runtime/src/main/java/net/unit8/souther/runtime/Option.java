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

    default boolean isPresent() {
        return this instanceof Some<T>;
    }
}
