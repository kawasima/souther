package souther.compiler.inputs;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.regex.CodePoints;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternSyntax;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.Type;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;

/**
 * The values a rule about a number taken of a position leaves, said as a plan for the values
 * themselves.
 *
 * <p>A position is read in two vocabularies — the values that stand there, and the numbers taken of
 * them — and a rule lands in one of them. Read apart, the values a rule about a number leaves are
 * not among what the position is said to admit, and every reader picking a value out of what it
 * admits picks one the rule refuses.
 *
 * <p><b>A plan and never a language.</b> What it costs to answer exactly is a question about the
 * whole of what a reading needs, not about any one pattern in it, so nothing is compiled here: this
 * says which values the rule leaves and the allowance that owns the position puts it together with
 * everything else it is met with ({@link souther.compiler.values.Allowance}).
 *
 * <p><b>One way and not the other.</b> What is answered is which values a band on the number
 * leaves, never which numbers a set of values comes to — the second is the projection itself, and
 * it is answered where the number is taken.
 *
 * <p>Not on {@link TakenAs}. What that says is which number an operation takes, which is asked by
 * readers with no vocabulary for values at all; writing the inverse there would put the spelling of
 * sets of values under a name whose whole job is to say which number is meant.
 */
final class ProjectionPreimage {

    private ProjectionPreimage() {
    }

    /**
     * What {@code band} on the number {@code by} takes of the values at a position leaves of them,
     * or null where nothing here narrows them.
     *
     * <p>Null for three things a caller has no different answer for: a band that leaves the number
     * alone, a number whose values this has no way of writing down, and a rule on a number taken of
     * a shape that is not one of them. What follows from each is the same — the values stand as
     * they were — and a caller that told them apart would be acting on which of them it was.
     *
     * @param valuesAt what stands at the position, through whatever names it is written under
     * @param by       the operation whose number the band is on
     * @param band     where the declarations leave that number
     */
    static AdmittedPlan of(Type valuesAt, ValueName by, NumericDomain.Bounds band) {
        if (band == null || (band.min() == null && band.max() == null) || by == null) {
            return null;
        }
        if (valuesAt != Type.STRING
                || !(by instanceof ValueName.Stdlib named)
                || !(DefaultBoundOperationFacts.get().takenAs(named)
                        instanceof TakenAs.HowManyItHolds)) {
            return null;
        }
        Integer least = atLeast(band.min());
        Integer most = atMost(band.max());
        if (least == null || (band.max() != null && most == null)) {
            return null;
        }
        // Every symbol, repeated within the run. What the rule is about is how many code points
        // stand there, which is what a repetition counts — a plan over what a string is stored as
        // would admit a character written as a pair where the rule asks for one.
        return new AdmittedPlan.Pattern(PatternPlan.of(new PatternSyntax.Repeated(
                new PatternSyntax.Symbols(CodePoints.EVERYTHING),
                least, most == null ? PatternSyntax.Repeated.NO_CEILING : most)));
    }

    /** The least the low end admits, or null where it is not a count this can read. */
    private static Integer atLeast(Endpoint low) {
        if (low == null) {
            return Integer.valueOf(0);
        }
        BigDecimal at = countAt(low.at());
        if (at == null) {
            return null;
        }
        // How many a value holds is a whole number, so an end between two of them admits the one
        // above it whichever way it is written. Read as the number it names, an exclusive end at a
        // whole number would admit that number too.
        BigDecimal least = low.inclusive() ? at.setScale(0, java.math.RoundingMode.CEILING)
                : at.setScale(0, java.math.RoundingMode.FLOOR).add(BigDecimal.ONE);
        return least.signum() < 0 ? Integer.valueOf(0) : asALength(least);
    }

    /** The greatest the high end admits, or null where there is no end or none this can read. */
    private static Integer atMost(Endpoint high) {
        if (high == null) {
            return null;
        }
        BigDecimal at = countAt(high.at());
        if (at == null) {
            return null;
        }
        BigDecimal most = high.inclusive() ? at.setScale(0, java.math.RoundingMode.FLOOR)
                : at.setScale(0, java.math.RoundingMode.CEILING).subtract(BigDecimal.ONE);
        return most.signum() < 0 ? null : asALength(most);
    }

    /** {@code at} as a length, or null where it is further out than a length is counted. */
    private static Integer asALength(BigDecimal at) {
        try {
            return at.intValueExact();
        } catch (ArithmeticException past) {
            return null;
        }
    }

    private static BigDecimal countAt(Place place) {
        return place instanceof Count count ? count.at() : null;
    }
}
