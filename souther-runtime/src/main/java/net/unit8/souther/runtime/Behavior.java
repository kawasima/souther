package net.unit8.souther.runtime;

/**
 * A behavior: a single-input transformation from {@code I} to a {@link Result} of {@code O}
 * (spec section 12). Pure behaviors always succeed; failing behaviors return a domain error.
 * {@code >>} composes behaviors as a Railway Oriented pipeline that short-circuits on the
 * first failure (spec section 14).
 *
 * @param <I> the input type
 * @param <O> the success output type
 */
@FunctionalInterface
public interface Behavior<I, O> {

    Result<O, ?> apply(I input);

    /** Railway composition: {@code this >> next}. On failure the rest of the pipeline is skipped. */
    default <P> Behavior<I, P> then(Behavior<? super O, P> next) {
        return input -> {
            Result<O, ?> r = apply(input);
            if (r instanceof Result.Ok<O, ?> ok) {
                return next.apply(ok.value());
            }
            return Result.err(((Result.Err<O, ?>) r).error());
        };
    }
}
