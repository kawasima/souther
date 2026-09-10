package souther.compiler.partition;

import souther.compiler.check.Carrier;
import souther.compiler.check.StatedComparison;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.TermOrders;
import souther.compiler.numeric.Count;


/**
 * The quantity a comparison over two positions cuts: how far the two stand apart.
 *
 * <p>{@link ComparedLine}'s sibling, and asked where that one came to nothing. A rule relating two
 * positions is not a partition of either (spec §what-a-position-admits), and that is an answer about
 * the classes rather than about the line.
 *
 * <p><b>The shape and not what the operator states about it.</b> Whether the rule orders the values
 * around the place the two meet or singles that place out is the claim's, and both are quantities
 * over the same pair. Told apart here, an equality over two positions had no realization at all
 * wherever the form's own reader wanted counting orders — so {@code s == t} over two strings, whose
 * meeting place is exactly what the pair's order has a number for, came back as a comparison this
 * compiler could not read.
 *
 * <p>Read the same way whoever wrote the comparison. A {@code guard} and a comparison in an
 * {@code ensures} draw the same line between the same two positions; what differs is what meeting it
 * takes, and that is the origin's to answer rather than this one's.
 *
 * <p>Asked of the orders and not of the types. A type is not what makes a line measurable: an
 * enumeration's case is comparable on its sum's order while carrying no places of its own, and two
 * newtypes of one base are two types whose values are ordered alike. What both positions can be read
 * as is the order, so that is what is asked about — and what is asked of the pair is that their
 * counts are one arithmetic, which is narrower than their being one order and wider than their being
 * one type.
 *
 * <p><b>The positions' orders, and never the operands'.</b> The two agree wherever a rule names its
 * positions itself, and part company the moment an operation stands between them: the operands of
 * {@code Date.daysBetween(a, b) > 10} are whole numbers and its positions hold dates. Read off the
 * comparison, both positions were written back and read off a row as whole numbers, and the border
 * could be met by no row and composed for by none (#1018). Nothing here reads an operand's type, and
 * the guard that used to — the two operands being of one order — was about neither position.
 *
 * @param stepsApart     how far apart the rule holds them, as a number on the carrier's counts.
 *                       Zero where the rule cuts where they meet, which is every comparison written
 *                       as one position against another. A number and not a count of steps: an order
 *                       with no smallest step still holds its values a distance apart
 */
record ComparedTerms(TermOrders on, TermOrders against, Count stepsApart) {

    ComparedTerms {
        // The two ends are the orders, and each says which position it is of. Held as a pair of
        // positions beside a map from position to orders, the two structures were what a reader
        // had to keep agreeing: the map's keys could be the right pair and its values the other
        // way round, and every check either structure made would pass.
        if (onTerm(on) == null || onTerm(against) == null) {
            throw new IllegalArgumentException(
                    "a distance runs between two positions: " + on + " against " + against);
        }
    }

    /** The position at one end, or null where those orders are of no single position. */
    private static NumericTerm.FromOnePosition onTerm(TermOrders orders) {
        return orders == null ? null : orders.term().atOnePosition();
    }

    /** The position the line is named by, which is the one the author wrote on the left. */
    NumericTerm.FromOnePosition onPosition() {
        return onTerm(on);
    }

    /** The position it is held apart from. */
    NumericTerm.FromOnePosition againstPosition() {
        return onTerm(against);
    }

    /**
     * The two positions {@code comparison} names, or null where it names no such pair.
     *
     * <p>Only where the comparison orders its two sides. This reading is reached where the
     * arithmetic stopped, and an equality between two things it could not read is a comparison
     * nothing here has taken apart at all — the canonical form is what says a pair is a pair, and
     * it had no answer.
     */
    static ComparedTerms asWritten(StatedComparison comparison,
                                   InputReading read, InputReads reads) {
        // A distance is what an order between the two sides states. A rule that names a value
        // orders nothing, and what tells the two apart is the claim the comparison carries — an
        // operator list of this reading's own would be a second answer to that.
        if (comparison.claim() instanceof ComparisonClaim.Cut) {
            GuardThresholds.Named on =
                    GuardThresholds.namedBy(comparison.left(), read, reads);
            GuardThresholds.Named against =
                    GuardThresholds.namedBy(comparison.right(), read, reads);
            if (on != null && against != null && aDistanceRuns(on.orders(), against.orders())) {
                // The subject is the one the author wrote on the left, which the canonical form
                // keeps too. Which of the two a line is named by is not something to derive where
                // the source settles it: `charge > ceiling` is a line about the charge.
                return new ComparedTerms(on.orders(), against.orders(), Count.ZERO);
            }
        }
        return null;
    }

    /**
     * The two positions on the orders they are read and written on, or null where they are not two
     * positions a distance runs between.
     *
     * <p>Three things make them one, and each of them is a classification rather than a complaint:
     * a pair refused here is read as whatever it is instead, and an arithmetic form is what it is
     * instead. One position is not two, and {@code a > a} names one twice. A position this reading
     * has no order for is one nothing can be written at. And two orders that share no counts have no
     * difference — a whole number of days and a whole number is not a number of anything — so such a
     * pair is left to the form it is, which carries the conversion in its coefficients.
     *
     * <p>Whether two orders make such a pair is {@link Carrier#standsAgainst}'s to say, and it is
     * what refuses a distance built out of a pair that is not one. Classifying and refusing are two
     * jobs and one rule, so the rule is in one place and neither writes it out again.
     *
     * <p>Which is why the answer is a carrier apiece and not one for the pair. A distance between
     * two positions written back differently — a decimal against a whole number is the pair that
     * exists — is still one distance, and it is only the writing that differs.
     */
    private static boolean aDistanceRuns(TermOrders here, TermOrders there) {
        return here != null && there != null
                && onTerm(here) != null && onTerm(there) != null
                && here.answered() != null && there.answered() != null
                && !here.term().equals(there.term())
                && here.answered().standsAgainst(there.answered());
    }

    /**
     * The distance the canonical form holds two positions apart, where it holds two apart.
     *
     * <p>Coefficients of one and minus one and nothing else, because that is what makes the quantity
     * a distance: {@code 2a - b} is not how far two positions stand apart, it is an arithmetic form
     * over both of them, and its border is a border of that form.
     *
     * <p>The threshold need not be zero. {@code a < b - 1} holds the two at least two apart, and
     * where they meet is not where that rule cuts — read as a line at zero it would ask for a pair
     * that proves nothing about it.
     */
    static ComparedTerms fromTheForm(AffineReading read,
                                     Quantities quantities) {
        if (read == null) {
            return null;
        }
        NumericTerm[] pair = read.twoCoordinates();
        if (pair == null) {
            return null;
        }
        // Both of them numbers a position answers, since a distance is between two positions.
        NumericTerm.FromOnePosition[] two = {
            pair[0].atOnePosition(), pair[1].atOnePosition(),
        };
        if (two[0] == null || two[1] == null) {
            return null;
        }
        // The orders come from the reading of the declarations, the way they do above. A term the
        // arithmetic produced has no expression naming it, so there is not even a type here to take
        // one off — which is the whole of what went wrong when one was taken off the comparison.
        TermOrders hereOn = quantities.ordersOf(two[0]);
        TermOrders thereOn = quantities.ordersOf(two[1]);
        Carrier here = hereOn.answered();
        // Whether the order has a place at the number the rule wrote is the order's own answer and
        // is asked where the quantity is built ({@link LevelSpace#canCutAt}). Asked here as "do
        // these counts count", a property of the values stood in for a property of the places: two
        // strings stand no measurable distance apart and are still one above the other, so the
        // place they meet is a line — and this refused it, leaving the spelling to draw one.
        if (here == null || !aDistanceRuns(hereOn, thereOn)) {
            return null;
        }
        // The distance as the number it is. Held as a count of the carrier's steps, a threshold
        // that is not a whole number of them — which two decimals a rule holds half apart give —
        // was an exception thrown out of the measure.
        return new ComparedTerms(hereOn, thereOn, new Count(read.cut()));
    }

}
