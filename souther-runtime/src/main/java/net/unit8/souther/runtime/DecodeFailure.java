package net.unit8.souther.runtime;

/**
 * The built-in decode-failure arm — the Souther type {@code 復号失敗}. It is the failure side
 * of a decoder's output {@code T | 復号失敗} (spec sections 10.5, 15) and carries the
 * accumulated {@link DecodeError}s. It mirrors {@code 制約違反} ({@link Violation}) on the
 * behavior side.
 */
public final class DecodeFailure {

    private final NonEmptyList<DecodeError> errors;

    public DecodeFailure(NonEmptyList<DecodeError> errors) {
        this.errors = errors;
    }

    public NonEmptyList<DecodeError> errors() {
        return errors;
    }
}
