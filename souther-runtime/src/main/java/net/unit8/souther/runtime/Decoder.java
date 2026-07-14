package net.unit8.souther.runtime;

/**
 * A Souther decoder: turns a {@link Raw} value into a validated {@code T}, or a
 * {@link DecodeFailure} carrying the accumulated errors (spec section 10). The output is one
 * of the two arms {@code T | 復号失敗} — a plain value, never a Result wrapper. Error
 * accumulation across independent fields still uses {@link Result} internally (see
 * {@link Decoders}), but that stays hidden behind this boundary type.
 *
 * @param <T> the decoded domain type
 */
@FunctionalInterface
public interface Decoder<T> {
    /** Decodes {@code raw} into a {@code T} on success, or a {@link DecodeFailure} on failure. */
    Object decode(Raw raw);
}
