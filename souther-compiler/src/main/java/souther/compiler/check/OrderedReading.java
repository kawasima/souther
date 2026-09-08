package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.types.Type;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The clauses reaching a value, read for where each of its positions stops.
 *
 * <p>Beside the reading that turns those same clauses into bounds for the report, and beside the one
 * that reads them for which values a position may hold — over the same list, at the same moment.
 * Which clauses reach a value is settled once, by the walk that gathers them; what each reading
 * makes of a clause is its own.
 *
 * <p><b>Leaves, and not the connectives over them.</b> What a conjunction or a choice of these
 * comes to is asked where both languages are held together and where the choices of a declaration
 * are decided ({@code StatedByClauses}), because a branch nobody can be in is settled by things
 * neither language holds alone. Answered here as well, that would be a second place deciding what
 * a choice does to the ranges, and a branch would be dropped only where the ranges were also what
 * could show it impossible — leaving {@code s < "" || (b == true && b == false)} a choice whose
 * every branch some language refused and neither refused alone.
 *
 * <p><b>Why it is not the interval algebra.</b> That one carries one number per position and relates
 * positions to each other by differences, which is worth having and is available only where a model
 * adds and subtracts — so it holds an {@code Int} and a {@code Decimal} and nothing else. This holds
 * every order there is and relates no two positions. Both may read one rule about an {@code Int},
 * and neither is the other's copy: what each can show is its own.
 *
 * <p><b>Why it is not the value sets.</b> Those name which values a position may take, as a finite
 * set or a finite exclusion, and an ordering names no finite set — there are as many dates below a
 * date as anyone likes. Pushing orderings into them would make one finite-set evaluator answer for
 * enum equality, enum ordering, numeric ordering and date ordering at once, which is four readings
 * wearing one name.
 *
 * <p><b>Every range this puts on a position is inside what the order holds.</b> Not an unbounded
 * range narrowed at the end: what is below the empty string is nothing, and a reading open below
 * would take {@code value < ""} for a rule leaving room underneath. The extent is the carrier's
 * ({@link Carrier#extent}) and it is applied where a rule becomes a range, which is the one place
 * a position is spoken about.
 *
 * <p>Applied there and not once around the whole reading, because whether an <em>alternative</em>
 * admits anything is asked of each branch. A branch left short of the order's own ends is a branch
 * whose emptiness nothing can see, and the choice between two such branches then turned on whether
 * they happened to be empty at the same position: {@code a < "" || a < ""} was refused and
 * {@code a < "" || b < ""} was not.
 *
 * <p><b>An equality places both ends and a disequality places none, and both are read.</b> An
 * equality states both at once, which is a range with one value in it and is exactly what this
 * holds; {@code /=} states neither, and the values a denial leaves are a set rather than a range —
 * which is the whole of what such a rule does to a range and is an answer, not a reading that
 * stopped. Under a denial the two swap places, which is the same rule read once.
 *
 * <p>Told apart from a rule this has no word for by {@link #gaveUpAt} and never by the ranges: both
 * leave every position where it was, and reading that back as "nothing was read here" is what made
 * every denial written beside a bound into a choice this reading declined to speak for.
 */
final class OrderedReading {

    private final Terms terms;
    /** What each position's values are ordered on, for the positions that are ordered at all. */
    private final Map<FactSubject, Carrier> carriers;
    /** The leaves this reading could not account for, written down as they are met. */
    private final Set<Core> gaveUp = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * The leaves among those that hold one of this reading's positions to another of them.
     *
     * <p>Beside {@link #gaveUp} and not taken out of it, because the two answer different readers.
     * What such a rule leaves the position is not a range and this reading has none for it, so it
     * is a rule this reading did not account for and everything asking that is right to hear so.
     * What it is not is a rule nobody read: it holds the position to another position, which is a
     * line somewhere else and no end here — and a reader asking whether the end at this position is
     * unknown is owed that answer rather than this reading's.
     */
    private final Set<Core> relatingTwoPositions =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private OrderedReading(Terms terms, Map<FactSubject, Carrier> carriers) {
        this.terms = terms;
        this.carriers = carriers;
    }

    /** The reading of one value's positions, for {@link StatedByClauses} to take the leaves of.
     *
     *  <p>No environment is held. Which environment a leaf is read at is where the leaf stands,
     *  which the fold hands down — kept here, a rule under a binding would be read at the names the
     *  clause began with. */
    static OrderedReading of(Terms terms, Map<FactSubject, Type> byName, Symbols symbols) {
        Map<FactSubject, Carrier> carriers = new LinkedHashMap<>();
        byName.forEach((name, type) -> {
            Carrier carrier = Carrier.ofValue(type, symbols);
            if (carrier != null) {
                carriers.put(name, carrier);
            }
        });
        return new OrderedReading(terms, carriers);
    }

    /**
     * What each position's values are ordered on, for a reader putting a range together with a set
     * of values.
     *
     * <p>The table this reading already worked out, handed on rather than built again. Which order a
     * position is counted by is settled where its type is read, and a second table would be a second
     * answer to that question.
     */
    Map<FactSubject, Carrier> carriers() {
        return carriers;
    }

    /**
     * A comparison places an end; nothing else here is read.
     *
     * <p>Which of them this reading could account for is written down as they are met, and
     * {@link #gaveUpAt} is where a reader asks. Every leaf that leaves the positions where they
     * were looks alike from the ranges, and they are not alike: a rule this reading understood and
     * that bounds nothing is one it read, and a rule it could not follow is one it did not.
     */
    OrderedIntervals<FactSubject> leaf(Core e, boolean positive, Denotations at) {
        // A rule of another shape. Whether it holds a value down anywhere is not something this
        // reading has a word for, so it is not a rule it can be said to have read.
        return e instanceof Core.Binary bin ? comparison(bin, positive, at) : gaveUp(e);
    }

    /**
     * Whether this reading could not account for what {@code e} does to the orders.
     *
     * <p>Asked at the leaf it was decided at, and answered out of what was written down when the
     * decision was made. False exactly where this reading followed the rule to the end: it named a
     * position it counts, and it either placed an end or found the rule places none. A disequality
     * is the second of those — it states neither end, that is the whole of what it does to a range,
     * and a reader taking it for a rule this reading could not follow sends an author to a clause
     * nothing failed at.
     *
     * <p>True everywhere else, and each of those is this reading losing the thread rather than
     * finding nothing: a rule of another shape, a comparison whose subject is a term this reading
     * cannot name ({@code Int.abs(n) >= 2}), one whose bound is not a literal of the order. What
     * such a rule holds a value down to is unknown here, so a choice offering one is a choice this
     * reading cannot speak for.
     */
    boolean gaveUpAt(Core e) {
        return gaveUp.contains(e);
    }

    /**
     * The positions of {@code named} whose ends {@code e} leaves unknown here.
     *
     * <p><b>The set and not a word about the leaf, because the set is what a caller wants and this
     * is what owns it.</b> Which positions the clause says the values stop somewhere on is the
     * clause's own answer and arrives as {@code named}; which of them have an end at all, and which
     * of those this reading worked out, are this reading's. Handed the flag instead, a caller made
     * the set out of every position the clause named — and a position whose values are not ordered,
     * which has no end for anything to be unknown about, came back as one whose end nobody could
     * work out.
     *
     * <p>Narrower than {@link #gaveUpAt} by the rules this reading followed to the end and has no
     * range for. A comparison holding one position it counts to another states where the values
     * part as surely as a bound does, and the line it draws runs between the two rather than at
     * either — so nothing about where this position stops is waiting on a reader. Asked
     * {@link #gaveUpAt}, such a rule is one nobody read, and a choice offering it comes back as one
     * whose end nothing could work out.
     *
     * <p>The two are one answer with two projections and not two records of one event: what is kept
     * is the leaf and which of the three things happened to it, and each caller asks for the half
     * it means. Kept as two flags a caller could ask for both and be told a rule was read and not
     * read.
     *
     * <p><b>And narrower than what this reading gave up on, by what it has no arithmetic for.</b> A
     * comparison whose positions cancel holds of every row, a call restricts which values may stand
     * somewhere and orders none of them, and a bound on how long a string is stops another number:
     * each of them is a leaf this reading lost the thread of and none of them leaves a position's
     * own order waiting on a reader. Which they are is decided where the arithmetic is
     * ({@link StatedLines}) and arrives in {@code named}.
     */
    Set<FactSubject> endsLeftUnknownAt(Core e, Set<FactSubject> named) {
        if (!gaveUp.contains(e) || relatingTwoPositions.contains(e)) {
            return Set.of();
        }
        Set<FactSubject> out = new LinkedHashSet<>();
        named.forEach(each -> {
            // The positions this reading counts, which is the whole of what it has ends for. A
            // position whose values are not ordered at all is not one it fell short at: there is
            // no end there to have been worked out, and the question this answers is not asked of
            // it.
            if (carriers.containsKey(each)) {
                out.add(each);
            }
        });
        return out;
    }

    /** A leaf this reading could not follow, which leaves every position where it was. */
    private OrderedIntervals<FactSubject> gaveUp(Core e) {
        gaveUp.add(e);
        return OrderedIntervals.top();
    }

    /**
     * Where one comparison leaves the position it names, or nothing where it names none.
     *
     * <p>What the comparison does to an order is read where both readings of one read it
     * ({@link OrderedLeaf}); what is here is this reading's own bookkeeping about the leaf, which
     * is what {@link #gaveUpAt} and {@link #endsLeftUnknownAt} answer out of.
     */
    private OrderedIntervals<FactSubject> comparison(Core.Binary bin, boolean positive,
                                                     Denotations at) {
        OrderedLeaf.Read<FactSubject> read = OrderedLeaf.of(bin, positive, at, terms,
                e -> positionIn(e, at), carriers::get);
        // Whether this reading then has a range for the rule is a separate question, and the two
        // are asked together by whoever wants the end ({@link #endsLeftUnknownAt}). So a rule that
        // does place an end is written down here as well where it happens to compare two positions,
        // and says nothing by being: there is nothing to be unknown about a rule this reading
        // followed.
        if (read.againstAnother()) {
            relatingTwoPositions.add(bin);
        }
        return switch (read.left()) {
            case OrderedLeaf.Left.NotFollowed<FactSubject> _ -> gaveUp(bin);
            // A rule read from end to end that stops the values nowhere — what a disequality
            // states. The values it leaves are a set rather than a range, which is the whole of
            // what it does to a range, so it is one this reading followed.
            case OrderedLeaf.Left.PlacesNoEnd<FactSubject> _ -> OrderedIntervals.top();
            // Inside what the order itself holds, which is what makes the carrier's own ends part
            // of every answer rather than something applied once around the whole reading: a branch
            // left short of them is a branch whose emptiness nothing can see.
            case OrderedLeaf.Left.Leaves<FactSubject> it ->
                    OrderedIntervals.at(it.number(), it.within());
        };
    }

    /** The position {@code e} is, or null where it is not one this is reading for. */
    private FactSubject positionIn(Core e, Denotations at) {
        FactSubject named = terms.subjectOf(e, at);
        return named != null && carriers.containsKey(named) ? named : null;
    }
}
