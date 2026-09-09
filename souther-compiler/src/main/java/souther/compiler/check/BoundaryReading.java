package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;

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
 * What a conjunction or a choice of these comes to is composed where both of the readings that
 * decide a branch's fate are held ({@link Confinement.Planned#either}), so a choice between two
 * bounds on one length settles to what the two of them leave together and a bound in a branch
 * nobody can be in settles to nothing. Read off the leaves alone, a bound written under a
 * {@code ||} would be a bound every value has to meet.
 *
 * <p><b>Only what an operation answers</b> ({@link DerivedNumber}). Where the values at a position
 * stop is the reading of ends' and is settled there; keyed alike, the two would be two mechanisms
 * over one order, and the line a report draws would be whichever of them was asked.
 *
 * <p><b>Which number a leaf is about is not decided here.</b> That is what the leaf states, and it
 * is answered where the arithmetic of the comparison is ({@link StatedLines}) — the same answer the
 * attribution of an end to a conjunct runs on. Asked again here, off which side is spelled as a
 * name, a rule whose coordinate is written inside an expression would be a rule this reading is not
 * about, and the choice above it would come back saying the model draws no line.
 *
 * <p>So this answers one question: whether a range can be read for the number the leaf states a
 * line on. A line it could not place leaves that number where it was and says so
 * ({@link Read.LeftOpen}), because the two are not the same fact — a number no rule spoke of
 * runs as far as it ever did, and one a rule stopped somewhere nobody worked out is a line this
 * compiler owes an answer for.
 */
final class BoundaryReading {

    private final Terms terms;
    /** Which number an expression is, for the numbers an operation answers of this value. */
    private final Map<FactSubject, DerivedNumber> byName;
    /** What each of those numbers is ordered on, under the number itself: a leaf is read for every
     *  clause of every value, so nothing here searches for one. */
    private final Map<DerivedNumber, Carrier> carriers;
    /** And what the clauses call each of them, for the same reason. */
    private final Map<DerivedNumber, FactSubject> subjects;

    private BoundaryReading(Terms terms, Map<FactSubject, DerivedNumber> byName,
                            Map<DerivedNumber, Carrier> carriers) {
        this.terms = terms;
        this.byName = byName;
        this.carriers = carriers;
        Map<DerivedNumber, FactSubject> named = new LinkedHashMap<>();
        byName.forEach((subject, number) -> named.put(number, subject));
        this.subjects = named;
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

    /**
     * What one leaf did to the number it states a line on.
     *
     * <p>Three answers and not a range beside a flag. A number no rule spoke of and one a rule
     * stopped somewhere nobody worked out are both absent from a range, and only the second is an
     * end this compiler owes an answer for — read off the range alone, the two are one.
     */
    sealed interface Read {

        /** The leaf states a line on none of the numbers this reading holds. */
        record NoLineStated() implements Read {}

        /** It stops {@code number} inside {@code range}. */
        record Bounded(DerivedNumber number, FactSubject subject, OrderedInterval range)
                implements Read {}

        /**
         * It stops {@code number} somewhere nothing here worked out.
         *
         * <p>The line is named ({@link OpenEnd}) because what a choice does to it is about this
         * line and not about the number: a rule stating two lines on one length may have one of
         * them settled by an alternative and the other written where no alternative reaches, and
         * told apart by the number the second would answer for the first.
         */
        record LeftOpen(OpenEnd end, FactSubject subject) implements Read {}
    }

    private static final Read NOTHING_STATED = new Read.NoLineStated();

    /** Where one leaf leaves the number it states a line on, or nothing where it states none. */
    Read leaf(StatedLines.Statement stated, Core e, boolean positive, Denotations at) {
        if (!(stated instanceof StatedLines.Statement.OnADerivedNumber said)) {
            return NOTHING_STATED;
        }
        FactSubject subject = subjectOf(said.number());
        if (subject == null || !(e instanceof Core.Binary bin)) {
            return NOTHING_STATED;
        }
        OrderedLeaf.Read<DerivedNumber> read = OrderedLeaf.of(bin, positive, at, terms,
                each -> byName.get(terms.subjectOf(each, at)), carriers::get);
        // The ends the rule states, held inside the order and with none of the order's own added.
        // Every count stops at the largest whole number whether or not a rule was written, and this
        // reading is read for the line an author drew — taken whole, every rule about a length
        // would put an end at a place no clause of it mentions.
        //
        // And the number the leaf was said to state a line on, which is not always one this reading
        // can find a side for: the arithmetic reaches a coordinate written inside an expression and
        // the walk over the two sides does not. Such a line is one this reading left open, and
        // saying so is what keeps a choice offering it from reading as a model that draws none.
        if (!(read.left() instanceof OrderedLeaf.Left.Leaves<DerivedNumber> it)
                || !it.number().equals(said.number())) {
            return new Read.LeftOpen(new OpenEnd(said.number()), subject);
        }
        OrderedInterval left = it.stated();
        // And a rule whose ends have crossed states no end of this number. What it says is that
        // nothing satisfies it there, which is a fact about whether a value exists — the question
        // this reading has no half of and the readings that decide it already answer. Put in the
        // envelope, it would be a line at a place the order does not reach, and every reader of the
        // envelope would have to know not to draw it.
        return Endpoint.someValueLiesBetween(left.low(), left.high())
                ? new Read.Bounded(it.number(), subject, left)
                : NOTHING_STATED;
    }

    /** What the clauses call {@code number}, which is the name every other reader files it
     *  under. Kept the other way round beside {@link #byName} rather than searched for: a leaf is
     *  read for every clause of every value. */
    private FactSubject subjectOf(DerivedNumber number) {
        return subjects.get(number);
    }
}
