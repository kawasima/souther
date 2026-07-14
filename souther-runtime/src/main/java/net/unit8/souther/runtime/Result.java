package net.unit8.souther.runtime;

import java.util.function.Function;

/**
 * The Souther result type: either {@link Ok} (a success value of type {@code T}) or
 * {@link Err} (a failure value of type {@code E}). Corresponds to spec section 7.3.
 *
 * <p>Unlike Raoh's {@code Result}, the failure side is a typed value {@code E}, not an
 * untyped issue bag. Raoh's issue types never leak into this public type.
 *
 * @param <T> the success type
 * @param <E> the failure type
 */
public sealed interface Result<T, E> permits Result.Ok, Result.Err {

    /** A successful result carrying a value. */
    record Ok<T, E>(T value) implements Result<T, E> {}

    /** A failed result carrying an error value. */
    record Err<T, E>(E error) implements Result<T, E> {}

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    default boolean isOk() {
        return this instanceof Ok<T, E>;
    }

    default boolean isErr() {
        return this instanceof Err<T, E>;
    }

    default <U> Result<U, E> map(Function<? super T, ? extends U> f) {
        return switch (this) {
            case Ok<T, E> ok -> Result.ok(f.apply(ok.value()));
            case Err<T, E> err -> Result.err(err.error());
        };
    }

    default <U> Result<U, E> flatMap(Function<? super T, ? extends Result<U, E>> f) {
        return switch (this) {
            case Ok<T, E> ok -> f.apply(ok.value());
            case Err<T, E> err -> Result.err(err.error());
        };
    }
}
