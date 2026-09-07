package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * How a lack about an alternative's blocks was reached, by however many readings reached it.
 *
 * <p>Beside a lack and never part of it. What a lack says is that some blocks are left nothing;
 * what this says is which rules were read to show it. So this is what a report reads to send an
 * author somewhere, and a reader asking whether two arguments showed the same thing asks that of
 * the lack.
 *
 * <p><b>Several routes and not one.</b> Two readings can leave one block no value by taking its
 * values through different blocks, and a choice between them leaves that block no value whichever
 * of them is read — so an author has both lots of rules to answer for. Held as one route, the only
 * sound thing to do with two of them is drop both, and what is left is a report naming a block
 * whose own rules are fine with what they leave it.
 *
 * <p><b>And routes are never run together.</b> Each is the removals of one narrowing, filed under
 * that narrowing's rounds, and a round of one reading is neither earlier nor later than a round of
 * another. Merged into one set of removals, a block of the first would answer for a removal of the
 * second and the walk back through the rounds would follow a path neither reading took.
 *
 * <p>Only narrowing has a route. Reading the rule, counting and looking for an assignment each show
 * their lack of the blocks the lack itself names, so what is carried for them is no route at all.
 *
 * @param <A> what a position is called
 * @param routes each of them the removals of one narrowing, in whatever order they arrived
 */
public record RelationalEvidence<A>(List<Provenance<A>> routes) {

    public RelationalEvidence {
        routes = List.copyOf(routes);
    }

    /** Nothing was read beyond the blocks the lack names. */
    public static <A> RelationalEvidence<A> none() {
        return new RelationalEvidence<>(List.of());
    }

    /** The one narrowing that reached it. */
    public static <A> RelationalEvidence<A> of(Provenance<A> route) {
        return new RelationalEvidence<>(List.of(route));
    }

    /** Every route either of these has, each of them once. */
    public RelationalEvidence<A> and(RelationalEvidence<A> other) {
        List<Provenance<A>> out = new ArrayList<>(routes);
        other.routes.forEach(route -> {
            if (!out.contains(route)) {
                out.add(route);
            }
        });
        return new RelationalEvidence<>(out);
    }

    /** The blocks a report may send an author to read beside {@code blocks}, by any route that
     *  reached them. */
    public Set<Sameness.Block<A>> restingOn(Set<Sameness.Block<A>> blocks) {
        Set<Sameness.Block<A>> out = new LinkedHashSet<>();
        routes.forEach(route -> blocks.forEach(block -> out.addAll(route.restingOn(block))));
        return Collections.unmodifiableSet(out);
    }

    /** The same routes about the blocks {@code naming} calls these. */
    public <B> RelationalEvidence<B> renamed(Function<A, B> naming) {
        List<Provenance<B>> out = new ArrayList<>();
        routes.forEach(route -> out.add(route.renamed(naming)));
        return new RelationalEvidence<>(out);
    }

    /** The same routes whichever order they arrived in. */
    @Override
    public boolean equals(Object said) {
        return said instanceof RelationalEvidence<?> it && routes.size() == it.routes.size()
                && routes.containsAll(it.routes);
    }

    @Override
    public int hashCode() {
        int out = 0;
        for (Provenance<A> route : routes) {
            out += route.hashCode();
        }
        return out;
    }
}
