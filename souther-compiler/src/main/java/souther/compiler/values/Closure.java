package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What narrowing a relation against what its blocks are left comes to.
 *
 * <p>Two answers and not a set of values beside a flag. Narrowing either reaches a reading nothing
 * more can be taken from, which is what the arguments after it are asked of, or it reaches a block
 * left no value, which is an answer about the relation and nothing anything after it is asked. Held
 * as one, a reader would be handed a reading whose blocks it has to check for emptiness before it
 * may count them, and the reader that forgot would count a block that holds nothing as a block.
 *
 * @param <A> what a position is called
 */
public sealed interface Closure<A> {

    /** Nothing more can be taken from any block: every value each block is left is one some
     *  neighbour leaves room for. */
    record Stable<A>(Domains<A> domains) implements Closure<A> {}

    /**
     * A round left blocks with no value at all, and what took the values.
     *
     * <p>Every block the round emptied and not one of them. A relation whose blocks can be swapped
     * for each other is the same relation swapped, so a rule naming one of several blocks emptied
     * together would answer one way for it and another for its image — and what there is to choose
     * between them by is how the denials were written or how the positions are spelled, neither of
     * which the relation says anything about.
     *
     * @param leftNothing the blocks the first round to empty anything emptied
     * @param provenance every removal up to and including that round
     */
    record Contradicted<A>(Set<Sameness.Block<A>> leftNothing,
                           Provenance<A> provenance) implements Closure<A> {

        public Contradicted {
            leftNothing = Collections.unmodifiableSet(new LinkedHashSet<>(leftNothing));
            if (leftNothing.isEmpty()) {
                throw new IllegalArgumentException(
                        "a narrowing that emptied no block is one nothing was taken from, which is"
                                + " Stable");
            }
        }

        /** The blocks written in one order — see {@link InOneOrder}. */
        @Override
        public String toString() {
            return "Contradicted" + InOneOrder.of(leftNothing) + provenance;
        }
    }
}
