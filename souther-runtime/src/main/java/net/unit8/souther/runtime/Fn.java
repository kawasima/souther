package net.unit8.souther.runtime;

/**
 * A first-class function value — a lambda that has escaped its lexical scope and so cannot be
 * expanded inline (spec §blocks). The backend generates one implementing class per such lambda,
 * with the captured free variables held in final fields and the body compiled into {@link #apply}.
 *
 * <p>Souther functions are multi-argument (not curried), so {@code apply} takes the arguments as an
 * array; a call site of known arity boxes its arguments into it. A function that does not escape is
 * still expanded inline and never becomes an {@code Fn}.
 */
@FunctionalInterface
public interface Fn {
    Object apply(Object[] args);
}
