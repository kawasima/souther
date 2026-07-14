package net.unit8.souther.runtime;

/**
 * A behavior: a single-input transformation from {@code I} to one of its output arms
 * {@code O} (spec section 12). The output is a plain domain value (an arm of the output
 * sum), never a Result wrapper. Whether an arm leaves the Railway main line is decided at
 * composition by {@code >>} (spec section 14): the generated pipeline routes each value to
 * the next stage when that stage's input type accepts it, and carries it through unchanged
 * otherwise. Composition is emitted as bytecode by the backend, so this interface only
 * needs {@code apply}.
 *
 * @param <I> the input type
 * @param <O> the output type (one of the behavior's output arms)
 */
@FunctionalInterface
public interface Behavior<I, O> {
    O apply(I input);
}
