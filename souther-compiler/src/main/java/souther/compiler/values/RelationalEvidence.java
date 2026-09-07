package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * How a lack about an alternative's blocks was reached.
 *
 * <p>Beside a lack and never part of it. What a lack says is that some blocks are left nothing;
 * what this says is which rules were read to show it, and two readings that show one lack along
 * different routes have shown one thing. So this is what a report reads to send an author
 * somewhere, and never what two lacks are compared by.
 *
 * <p>Only narrowing has a route. Reading the rule, counting and looking for an assignment each show
 * their lack of the blocks the lack itself names, so what is carried for them is nothing — which is
 * a route of no removals rather than a case somebody has to remember to ask about.
 *
 * @param <A> what a position is called
 * @param takenAway which values a narrowing took from which blocks, and what left them nowhere else
 *                  to go
 */
public record RelationalEvidence<A>(Provenance<A> takenAway) {

    /** Nothing was read beyond the blocks the lack names. */
    public static <A> RelationalEvidence<A> none() {
        return new RelationalEvidence<>(Provenance.nothing());
    }

    /**
     * The blocks a report may send an author to read beside the ones {@code lacks} name.
     *
     * <p>None where nothing was taken away, which is what every argument but narrowing shows its
     * lack without.
     */
    public Set<Sameness.Block<A>> restingOn(Set<RelationalLack<A>> lacks) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        lacks.forEach(lack -> lack.blocks().forEach(block -> out.addAll(takenAway.restingOn(block))));
        return Collections.unmodifiableSet(out);
    }

    /** The same route about the blocks {@code naming} calls these. */
    public <B> RelationalEvidence<B> renamed(Function<A, B> naming) {
        return new RelationalEvidence<>(takenAway.renamed(naming));
    }
}
