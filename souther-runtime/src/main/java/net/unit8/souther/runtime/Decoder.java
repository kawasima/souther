package net.unit8.souther.runtime;

/**
 * A Souther decoder: turns a {@link Raw} value into a validated {@code T}, or a non-empty
 * list of {@link DecodeError}s (spec section 10.1). This is the public type generated
 * {@code decoder()} methods return; the Raoh backend stays hidden behind it.
 *
 * @param <T> the decoded domain type
 */
@FunctionalInterface
public interface Decoder<T> {
    Result<T, NonEmptyList<DecodeError>> decode(Raw raw);
}
