package net.unit8.souther.runtime;

/**
 * The built-in "constraint violation" arm — the Souther type {@code 制約違反}. Produced when a
 * behavior constructs invariant-bearing data whose invariant fails (spec 9.4). On the decoder
 * side, decode failures are carried by Raoh's {@code Result} instead of a domain arm (spec 10).
 * Every behavior that constructs invariant-bearing data must include this arm in its output sum
 * so a violation has somewhere to go (spec 22.3).
 */
public final class Violation {

    private final String message;

    public Violation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    /**
     * Turns a construction result into a plain output value: the constructed value when the
     * invariants held, or a {@link Violation} carrying the message when they did not. Used by
     * generated behavior bodies so a construction never surfaces a Result on a public API.
     */
    public static Object orValue(Result<?, ?> r) {
        if (r instanceof Result.Ok<?, ?> ok) {
            return ok.value();
        }
        return new Violation(String.valueOf(((Result.Err<?, ?>) r).error()));
    }
}
