package souther.compiler.coverage;

import souther.compiler.core.Core;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Whether an expression answers a value, for a reader that asks about more than one of them.
 *
 * <p>{@link NormalReturn} is rooted: it answers about the nodes of one tree and refuses a node from
 * anywhere else, because reading such a node as a body of its own has every name in it free and a
 * name bound to something that aborts is what makes the difference. Which tree a reader's questions
 * are about is the reader's own fact, and this is where the reader says it.
 *
 * <p>Two answers to that, and a reader has one or the other. A reading of a body asks about the
 * nodes of that body, whichever of them the walk reaches. A reading of a declaration's clause has
 * no body — a clause is checked whenever a value is built and stands on the way to nothing — so
 * what it is handed is a tree with nothing above it, and each is a root of its own.
 */
public interface Arrivals {

    /** Whether {@code e} answers a value. */
    boolean at(Core e);

    /**
     * The nodes of one body, read once.
     *
     * <p>Built when something first asks, most expressions holding no fork and the question being
     * asked only about the arms of one.
     */
    static Arrivals inTheBody(Core body) {
        NormalReturn answering = NormalReturn.lazilyWhereTheOperationsStand(body);
        return answering::at;
    }

    /**
     * Every tree it is asked about, each read as the root it is.
     *
     * <p>For a reader whose expressions stand in no body. What this cannot see is a name bound
     * above what it is handed, there being nothing above it to bind one — and what it must not do
     * is answer about some other tree, which is why the reading is per tree rather than one reading
     * asked about nodes it was not rooted at.
     */
    static Arrivals whereNothingStandsAbove() {
        Map<Core, NormalReturn> read = new IdentityHashMap<>();
        return e -> read.computeIfAbsent(e, NormalReturn::lazilyWhereTheOperationsStand).at(e);
    }
}
