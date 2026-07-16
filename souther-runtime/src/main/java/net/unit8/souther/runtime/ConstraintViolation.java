package net.unit8.souther.runtime;

/**
 * A domain invariant broken inside the domain — the abort of spec 7.3 / 9.4 / 19.7. When a
 * behavior constructs invariant-bearing data whose invariant fails, the computation aborts by
 * throwing this instead of returning a business arm: an invariant violation is a model bug, not
 * a business result, so it has no place in the output sum (spec 2.6).
 *
 * <p>Souther code cannot catch it (there is no catch syntax), so it never drives business flow
 * (spec 3). A boundary (e.g. HTTP) may catch it and map it to a 500 — distinct from a business
 * failure, which arrives as an output arm and maps to a 400 (spec 19.7). Decode-side violations
 * are different: they are carried by Raoh's {@code Result} failure, not by this (spec 10.5).
 */
public final class ConstraintViolation extends RuntimeException {

    public ConstraintViolation(String message) {
        super(message);
    }

    /**
     * Turns a construction result into a plain value or an abort: the constructed value when the
     * invariants held, or a thrown {@link ConstraintViolation} carrying the message when they did
     * not. Generated behavior bodies call this so a construction never surfaces a {@code Result}
     * on a public API and a violation never rides an output arm.
     */
    public static Object orThrow(Result<?, ?> r) {
        if (r instanceof Result.Ok<?, ?> ok) {
            return ok.value();
        }
        throw new ConstraintViolation(String.valueOf(((Result.Err<?, ?>) r).error()));
    }
}
