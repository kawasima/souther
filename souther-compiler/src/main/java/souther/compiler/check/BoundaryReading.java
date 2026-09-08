package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.OrderedIntervals;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The clauses reaching a value, read for where the numbers its operations answer stop.
 *
 * <p>The third reading of one clause tree, beside which values may stand at a position and where
 * the position's own order stops. A rule about how long the string at a place is says nothing about
 * which strings stand there and nothing about where they stop: it is a line on a whole number, and
 * that number is the one it is a line on.
 *
 * <p><b>Leaves, and not the connectives over them</b>, for the reason {@link OrderedReading} gives.
 * What a conjunction or a choice of these comes to is composed where the branches have their fate
 * ({@link StatedByClauses.Part}), so a choice between two bounds on one length settles to what the
 * two of them leave together and a bound in a branch nobody can be in settles to nothing. Read off
 * the leaves alone, a bound written under a {@code ||} would be a bound every value has to meet.
 *
 * <p><b>Only what an operation answers</b> ({@link DerivedNumber}). Where the values at a position
 * stop is the reading of ends' and is settled there; keyed alike, the two would be two mechanisms
 * over one order, and the line a report draws would be whichever of them was asked.
 *
 * <p>What this reading could not follow is not written down here. A line it states and could not
 * place leaves that number where it was, which is what {@link OrderedIntervals#top} says, and the
 * clause is answered for by the reading that says what a leaf states at all ({@link StatedLines}) —
 * a second record of it here would be one more thing to keep in step with that answer.
 */
final class BoundaryReading {

    private final Terms terms;
    /** Which number an expression is, for the numbers an operation answers of this value. */
    private final Map<FactSubject, DerivedNumber> byName;
    /** What each of those numbers is ordered on, under the number itself: a leaf is read for every
     *  clause of every value, so nothing here searches for one. */
    private final Map<DerivedNumber, Carrier> carriers;

    private BoundaryReading(Terms terms, Map<FactSubject, DerivedNumber> byName,
                            Map<DerivedNumber, Carrier> carriers) {
        this.terms = terms;
        this.byName = byName;
        this.carriers = carriers;
    }

    /**
     * A reading with nothing to read, for a value none of whose places is measured.
     *
     * <p>Made once. Most values have no operation answering a number of them, and a leaf of every
     * clause of every one of them is read through this.
     */
    private static final BoundaryReading NOTHING =
            new BoundaryReading(null, Map.of(), Map.of());

    /**
     * The reading of the numbers {@code numbers} names, whose orders {@code carriers} gives.
     *
     * <p>No environment is held, as the readings beside it hold none: which environment a leaf is
     * read at is where the leaf stands, and the fold hands that down.
     */
    static BoundaryReading of(Terms terms, Map<FactSubject, DerivedNumber> numbers,
                              Map<DerivedNumber, Carrier> carriers) {
        return numbers.isEmpty() ? NOTHING
                : new BoundaryReading(terms, new LinkedHashMap<>(numbers),
                        new LinkedHashMap<>(carriers));
    }

    /** Where one leaf leaves the numbers this reading holds, which is everywhere it found none. */
    OrderedIntervals<DerivedNumber> leaf(Core e, boolean positive, Denotations at) {
        if (byName.isEmpty() || !(e instanceof Core.Binary bin)) {
            return OrderedIntervals.top();
        }
        OrderedLeaf.Read<DerivedNumber> read = OrderedLeaf.of(bin, positive, at, terms,
                each -> byName.get(terms.subjectOf(each, at)), carriers::get);
        // The ends the rule states, held inside the order and with none of the order's own added.
        // Every count stops at the largest whole number whether or not a rule was written, and this
        // reading is read for the line an author drew — taken whole, every rule about a length
        // would put an end at a place no clause of it mentions.
        return read.left() instanceof OrderedLeaf.Left.Leaves<DerivedNumber> it
                ? OrderedIntervals.at(it.number(), it.stated())
                : OrderedIntervals.top();
    }
}
