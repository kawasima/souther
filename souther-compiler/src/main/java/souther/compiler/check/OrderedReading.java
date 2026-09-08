package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.Place;
import souther.compiler.types.Type;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
 * <p><b>Equalities are read and disequalities are not.</b> An equality states both ends at once,
 * which is a range with one value in it and is exactly what this holds; {@code /=} states neither
 * end, and the values a denial leaves are a set rather than a range. Under a denial the two swap
 * places, which is the same rule read once.
 */
final class OrderedReading {

    private final Terms terms;
    /** What each position's values are ordered on, for the positions that are ordered at all. */
    private final Map<FactSubject, Carrier> carriers;
    /** The leaves this reading could not account for, written down as they are met. */
    private final Set<Core> gaveUp = Collections.newSetFromMap(new IdentityHashMap<>());

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

    /** A leaf this reading could not follow, which leaves every position where it was. */
    private OrderedIntervals<FactSubject> gaveUp(Core e) {
        gaveUp.add(e);
        return OrderedIntervals.top();
    }

    /** Where one comparison leaves the position it names, or nothing where it names none. */
    private OrderedIntervals<FactSubject> comparison(Core.Binary bin, boolean positive,
                                                     Denotations at) {
        Comparison read = Comparison.of(bin).orElse(null);
        if (read == null) {
            // Written with an operator and not a comparison. The same as a rule of another shape.
            return gaveUp(bin);
        }
        // The position-bearing side read as the left one, as `0 <= value` says what `value >= 0`
        // says.
        FactSubject position = positionIn(bin.left(), at);
        Core bound = bin.right();
        ComparisonClaim claim = read.claim();
        if (position == null) {
            position = positionIn(bin.right(), at);
            bound = bin.left();
            claim = claim.turned();
        }
        Carrier carrier = position == null ? null : carriers.get(position);
        if (carrier == null) {
            // Neither side is a position this counts. The rule may still be about one — a length,
            // an absolute value, a reversal — and what it holds that position to is then something
            // this reading cannot follow rather than something it found to be nothing.
            return gaveUp(bin);
        }
        Hir.Expr written = Terms.asWrittenValue(bound, at);
        // Denied, a comparison is the one that leaves what it leaves out. `!(value /= x)` is an
        // equality and is read; `!(value == x)` is a disequality and is not, which is the same
        // answer the disequality gets when it is written directly.
        ComparisonClaim said = positive ? claim : claim.denied();
        return switch (said) {
            // The value the rule is met at, which is a range with one value in it. What a denial
            // leaves is every other value, and that is a set rather than a range — which is the
            // whole of what the rule does to a range, so it is one this reading followed.
            case ComparisonClaim.Singled singled -> singled.holdsAtTheValue()
                    ? onlyTheValue(bin, position, carrier, written)
                    : OrderedIntervals.top();
            case ComparisonClaim.Cut cut -> ends(bin, position, carrier,
                    InvariantBound.at(cut, written, carrier));
        };
    }

    /** The range of one value, or nothing where the rule names none this order reads. */
    private OrderedIntervals<FactSubject> onlyTheValue(Core e, FactSubject position,
                                                       Carrier carrier, Hir.Expr written) {
        Place only = written == null ? null : carrier.literalOf(written);
        // The rule meets the position at something this order has no literal for, so where it
        // leaves the position is not something this reading found to be nothing.
        return only == null ? gaveUp(e)
                : leaves(position, carrier, new OrderedInterval(
                        Endpoint.inclusive(only), Endpoint.inclusive(only)));
    }

    /** What the end an ordering placed leaves the position. */
    private OrderedIntervals<FactSubject> ends(Core e, FactSubject position, Carrier carrier,
                                               InvariantBound.Read read) {
        return switch (read) {
            case InvariantBound.Read.AnEnd it -> leaves(position, carrier, it.bound().lower()
                    ? new OrderedInterval(it.bound().end(), null)
                    : new OrderedInterval(null, it.bound().end()));
            // The rule names an end the order does not reach, so the position holds nothing. Said as
            // a range of this order with no value in it, which is the same kind of answer two rules
            // whose ends cross come to.
            case InvariantBound.Read.PastWhereTheOrderStops _ ->
                    leaves(position, carrier, carrier.nothing());
            // A cut on a position this counts, against something the order has no literal for. The
            // other reasons NoEnd stands for are answered before this call, so what arrives is
            // always this one.
            case InvariantBound.Read.NoEnd _ -> gaveUp(e);
        };
    }

    /**
     * What one rule leaves a position, inside what the order itself holds.
     *
     * <p>The one place a position is spoken about, which is what makes the order's own ends part of
     * every answer rather than something applied once at the end. A reader adding a second such
     * place has to remember the extent; this one cannot forget it.
     */
    private static OrderedIntervals<FactSubject> leaves(FactSubject position, Carrier carrier,
                                                 OrderedInterval range) {
        return OrderedIntervals.at(position, carrier.extent().meet(range));
    }

    /** The position {@code e} is, or null where it is not one this is reading for. */
    private FactSubject positionIn(Core e, Denotations at) {
        FactSubject named = terms.subjectOf(e, at);
        return named != null && carriers.containsKey(named) ? named : null;
    }
}
