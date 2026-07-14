package net.unit8.souther.runtime;

/**
 * A Souther encoder: turns an internal {@code T} into its {@link Raw} representation.
 * Total function (spec section 11.1).
 *
 * @param <T> the internal domain type
 */
@FunctionalInterface
public interface Encoder<T> {
    Raw encode(T value);
}
