package souther.compiler.reading;

import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.flow.ComparisonWays;
import souther.compiler.inputs.ComparedNumber;
import souther.compiler.inputs.ComparedNumbers;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.Quantities;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Towards;

import java.util.function.Function;

/**
 * Which ways a comparison has a value behind it, answered from the number it is about.
 *
 * <p>The reading a body's comparisons are admitted by. A comparison names a number of the input, the
 * rules leave that number a run of values, and a way stands where some value of that run falls on
 * the side the way needs. Nothing here reads how the comparison was written: which side the number
 * was on, whether it was a name or something taken of one, and what kind of node stands there are
 * all settled before this is asked ({@link ComparedNumber}).
 *
 * <p><b>Why that is the question.</b> Read off the shape of the operands, a comparison over a number
 * taken of a location is a call against a literal and nothing about it says a value varies — so the
 * way was never held, the decision was never named, and no row was ever steered into the arm behind
 * it. The number is what varies, and the number is what is asked about here.
 *
 * <p>The same reading the decision is named from ({@link ComparedNumbers}). Admitting a way and
 * saying which decision it settles are two questions about one comparison, and answering them from
 * two readings is how a way came to be held for a number the naming could not find.
 *
 * <p>Where the comparison draws no line — a number over a run of values, one position against
 * another, a value no order writes — the body's own text answers as it did before. That is not a
 * second answer to this question: it is the answer for the comparisons this one has nothing to say
 * about.
 */
final class NumberWays implements ComparisonWays {

    private final ComparedNumbers numbers;
    private final Quantities quantities;
    // Where this reader stands, which is its own and not the naming's. The two walk the same body
    // and meet each comparison at the same node, and the reading they share says so rather than
    // taking it on trust ({@link ComparedNumbers#of}).
    private final InputReads reads;
    private final Symbols symbols;

    /** Which declarations wear one value, which is what says whether reading a field reaches
     *  somewhere else ({@link souther.compiler.check.Location#isStep}). */
    private final DeclarationNewtypes newtypes;

    NumberWays(ComparedNumbers numbers, Quantities quantities, InputReads reads, Symbols symbols,
               DeclarationNewtypes newtypes) {
        this.numbers = numbers;
        this.quantities = quantities;
        this.reads = reads;
        this.symbols = symbols;
        this.newtypes = newtypes;
    }

    @Override
    public ComparisonWays under(Core.Binder binder, Core value) {
        return new NumberWays(numbers, quantities, reads.and(binder, value), symbols, newtypes);
    }

    @Override
    public ComparisonWays insideArm(Core.Match match, Core.Case arm) {
        return new NumberWays(numbers, quantities, reads.insideArm(match, arm, symbols, newtypes),
                symbols, newtypes);
    }

    @Override
    public boolean comesOut(Core e, boolean want, Function<Core.Read, Core> settledBy) {
        ComparedNumber said =
                e instanceof Core.Binary binary ? numbers.of(binary, reads) : null;
        ComparedNumber.DrawnLine drawn = said == null ? null : said.line();
        return drawn == null
                ? ComparisonWays.OF_THE_TREE.comesOut(e, want, settledBy)
                : leaves(drawn, want);
    }

    /** Whether the values the rules leave the number include one the comparison comes out
     *  {@code want} at. */
    private boolean leaves(ComparedNumber.DrawnLine drawn, boolean want) {
        NumericDomain.Bounds runs = quantities.runsBetween(drawn.term());
        return switch (drawn.claim()) {
            case ComparisonClaim.Cut cut -> {
                // Which side the way needs, which is the side the comparison is true on.
                boolean upIsTrue = cut.satisfyingSide() == Towards.ABOVE;
                yield anythingBeyond(runs, drawn.at(), upIsTrue == want,
                        cut.holdsAtTheValue() == want);
            }
            // The value itself where the way is the one the comparison holds at, and everything else
            // where it is the other. What is left over is empty only where the run is that one value
            // and nothing else.
            case ComparisonClaim.Singled singled -> singled.holdsAtTheValue() == want
                    ? holds(runs, drawn.at()) : notOnlyOneValue(runs, drawn.at());
        };
    }

    /**
     * Whether the run holds a value on the {@code up} side of {@code at}, taking {@code at} itself
     * where the side {@code inclusive} reaches it.
     *
     * <p>Only the end the side runs towards can close it: everything above a line is still above it
     * however far the run's low end is raised, so a side that reaches past the far end is a side
     * with a value on it.
     *
     * <p>Where the end falls exactly on the line, what is beyond it is the line itself and nothing
     * else, so a side that takes the line has a value there where the run does, and a side that does
     * not has none. That is the one place this closes a way on the run's word: strictly past an end
     * the run stops at, no value stands. Everywhere short of the end this answers that a value
     * stands, whether the run steps or fills there, because that much the end says on its own.
     */
    private static boolean anythingBeyond(NumericDomain.Bounds runs, Place at, boolean up,
                                          boolean inclusive) {
        Endpoint end = runs == null ? null : (up ? runs.max() : runs.min());
        if (end == null || end.at() == null) {
            return true;
        }
        int against = end.at().compareTo(at);
        if (up ? against > 0 : against < 0) {
            return true;
        }
        return against == 0 && inclusive && end.inclusive();
    }

    /** Whether the run holds the value the comparison named. */
    private static boolean holds(NumericDomain.Bounds runs, Place at) {
        return anythingBeyond(runs, at, true, true) && anythingBeyond(runs, at, false, true);
    }

    /** Whether the run holds anything but the value the comparison named. */
    private static boolean notOnlyOneValue(NumericDomain.Bounds runs, Place at) {
        return anythingBeyond(runs, at, true, false) || anythingBeyond(runs, at, false, false);
    }
}
