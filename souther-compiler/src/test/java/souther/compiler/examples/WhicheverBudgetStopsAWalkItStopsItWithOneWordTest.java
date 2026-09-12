package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.Membership;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.Limits;
import souther.compiler.observe.ObservedValue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whichever of an observation's budgets runs out, what the walk hands back is the same word.
 *
 * <p>There are four numbers and one thing they do. A walk that goes too deep, one that has held too
 * many nodes, one at a collection longer than it keeps and one at a text longer than it keeps all
 * stop, and what stands where the value would have been says that a limit stopped there — not which
 * limit, and nothing about the value. So a reading written for one of them is a reading for all
 * four, and this is what says so.
 *
 * <p><b>Each number with its two neighbours.</b> A budget tested with a value well past it passes
 * for a walk that stops one early or one late, and one early is a value the report says nothing can
 * be read of when it plainly can. So each of these is the largest value the budget keeps and the
 * smallest it does not.
 *
 * <p>Held here rather than by a model apiece. What a rule downstream does with the word is the
 * reading's own law and is held where the reading is; four models saying it again would be the same
 * law written four times, at the cost of a compilation each and with three of them measuring
 * whichever budget the model happened to cross first.
 */
class WhicheverBudgetStopsAWalkItStopsItWithOneWordTest {

    private static final Limits DEFAULT = Limits.DEFAULT;

    /** A subtree past the depth the walk goes to is stopped, and the one at it is not. */
    @Test
    void aWalkTooDeepStops() {
        assertEquals(new ObservedValue.Truncated(), deepest(nested(DEFAULT.maxDepth() + 1)),
                "one step past the depth is where the walk stops");
        assertNotEquals(new ObservedValue.Truncated(), deepest(nested(DEFAULT.maxDepth())),
                "and the value standing at the depth is read");
    }

    /**
     * A walk that has held as many nodes as it may stops at the next one.
     *
     * <p>Shaped so that no other budget is anywhere near it. A flat list of two thousand values is
     * a collection past its count long before it is a walk out of nodes, and would measure that
     * instead — so the nodes are spread over collections each well inside what one may hold.
     */
    @Test
    void aWalkOutOfNodesStops() {
        assertTrue(stopsSomewhere(observed(nodes(DEFAULT.maxNodes() + 1))),
                "the node after the last one the budget holds is where the walk stops");
        assertFalse(stopsSomewhere(observed(nodes(DEFAULT.maxNodes()))),
                "and a value of exactly the budget is read whole");
    }

    /** A collection longer than the walk keeps is stopped whole. */
    @Test
    void aCollectionPastItsCountStops() {
        assertEquals(new ObservedValue.Truncated(), observed(longs(DEFAULT.maxElements() + 1)),
                "one element past the count is a collection the walk does not keep");
        assertNotEquals(new ObservedValue.Truncated(), observed(longs(DEFAULT.maxElements())),
                "and one of exactly the count is kept");
    }

    /** And a text longer than the walk keeps, the same way. */
    @Test
    void aTextPastItsLengthStops() {
        assertEquals(new ObservedValue.Truncated(), observed("x".repeat(DEFAULT.maxText() + 1)),
                "one character past the length is a text the walk does not keep");
        assertNotEquals(new ObservedValue.Truncated(), observed("x".repeat(DEFAULT.maxText())),
                "and one of exactly the length is kept");
    }

    /**
     * And every one of them reads as the same reason, which is what a rule downstream is handed.
     *
     * <p>The word a walk hands back carries no budget, so nothing further on can tell the four
     * apart — which is the point. A reading that answered differently for one of them would be a
     * reading with a fifth case nothing produces.
     */
    @Test
    void allFourReadAsOneReason() {
        for (ObservedValue stopped : List.of(deepest(nested(DEFAULT.maxDepth() + 1)),
                stopped(observed(nodes(DEFAULT.maxNodes() + 1))),
                observed(longs(DEFAULT.maxElements() + 1)),
                observed("x".repeat(DEFAULT.maxText() + 1)))) {
            assertEquals(new Membership.Incomplete(Incompleteness.Code.VALUE_TRUNCATED),
                    Membership.unread(stopped),
                    "a value a budget stopped is one reason, whichever budget it was");
        }
    }

    /** A list nested {@code deep} times around a whole number. */
    private static Object nested(int deep) {
        Object at = 1L;
        for (int i = 0; i < deep; i++) {
            at = List.of(at);
        }
        return at;
    }

    /** What stands at the bottom of a nest of sequences. */
    private static ObservedValue deepest(Object live) {
        ObservedValue at = observed(live);
        while (at instanceof ObservedValue.Sequence held && !held.elements().isEmpty()) {
            at = held.elements().get(0);
        }
        return at;
    }

    /**
     * A live value of exactly {@code total} observed nodes, held under every other budget.
     *
     * <p>One list of lists. Each holds at most what a collection may hold, and the whole is two
     * deep, so the only number this can run out of is the one for how many nodes an observation
     * keeps.
     */
    private static Object nodes(int total) {
        List<Object> out = new ArrayList<>();
        int left = total - 1;   // the list itself is a node
        while (left > 0) {
            int here = Math.min(1 + DEFAULT.maxElements(), left);
            out.add(longs(here - 1));
            left -= here;
        }
        return out;
    }

    /** Whether the walk stopped anywhere inside {@code at}. */
    private static boolean stopsSomewhere(ObservedValue at) {
        return stopped(at) != null;
    }

    /** The first stopped value inside {@code at}, or null where the walk kept all of it. */
    private static ObservedValue stopped(ObservedValue at) {
        if (at instanceof ObservedValue.Truncated) {
            return at;
        }
        if (at instanceof ObservedValue.Sequence held) {
            for (ObservedValue each : held.elements()) {
                ObservedValue found = stopped(each);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ObservedValue observed(Object live) {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        // No module is being read, so nothing here declares a data whose fields could be asked for.
        return ObservedValues.of(live, symbols,
                new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                        ScopedDeclarations.kindsOf(symbols),
                        FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))), DEFAULT);
    }

    private static List<Object> longs(int count) {
        List<Object> out = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            out.add(i);
        }
        return out;
    }
}
