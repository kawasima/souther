package souther.compiler.inputs;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Some of one kind of thing, in the order the author wrote what raised them.
 *
 * <p>The claim, made where it can be made. Nothing in a list says where its order came from — the
 * walk that found the members, or the person who wrote them — and between those two lies the whole
 * of what a reader may do with it: one is a fact about the model and the other is a fact about a
 * traversal. So the claim is stated by making one of these, and it is stated where the order is
 * still known.
 *
 * <p><b>Which is why this is here and not beside the document.</b>
 * {@link souther.compiler.publish.SourceOrdered} is the same claim at the crossing into a report,
 * and it used to be made there — by a projection handed a bare list, two packages from anything
 * that had seen the source. It was true while every member came from one producer and stopped being
 * true when a second arrived, with nothing in a position to notice. A projection maps one of these
 * to the words a document writes and states nothing about the order, because it has nothing to
 * state it from.
 *
 * <p>Each member once, keeping the first place it was written at. Two parts one limit stopped are
 * one thing to lift and one entry, and the entry is where the first of them stands.
 *
 * @param <T> what is held in the author's order
 */
public final class AuthoredOrder<T> {

    private final List<T> written;

    private AuthoredOrder(List<T> written) {
        this.written = written;
    }

    /**
     * The members as the author wrote them, each once.
     *
     * <p>The caller says by calling this that the order it hands over is the model's own. What is
     * held to that is not a type — no type can be — but that the places allowed to say it are
     * counted, and each of them has the source's order in hand where it says it.
     */
    public static <T> AuthoredOrder<T> asWritten(List<? extends T> members) {
        return new AuthoredOrder<>(List.copyOf(new LinkedHashSet<T>(members)));
    }

    /** The members that are held, in the order they were written. */
    public List<T> written() {
        return written;
    }

    public boolean isEmpty() {
        return written.isEmpty();
    }

    /**
     * The same order over what {@code word} makes of each member, each said once.
     *
     * <p>How the order crosses into anything else. What comes out is the same claim about the same
     * model, so nothing on the way has to state it again — which is the whole point of it being a
     * value: a caller that re-stated it would be stating what it cannot see.
     */
    public <U> AuthoredOrder<U> map(Function<? super T, U> word) {
        Set<U> out = new LinkedHashSet<>();
        for (T each : written) {
            out.add(word.apply(each));
        }
        return new AuthoredOrder<>(List.copyOf(out));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthoredOrder<?> it && written.equals(it.written);
    }

    @Override
    public int hashCode() {
        return written.hashCode();
    }

    @Override
    public String toString() {
        return written.toString();
    }
}
