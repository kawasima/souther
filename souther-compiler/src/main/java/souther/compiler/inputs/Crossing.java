package souther.compiler.inputs;

import souther.compiler.check.Carrier;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.ValueSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the rules reaching a position leave of the distinctions its type states.
 *
 * <p>Two facts crossed, and they are separate facts. That a type states a distinction is read off
 * the declaration; that a value of it can stand at <em>this</em> position is what the rules on the
 * position leave. A {@code data StageI = Stage invariant value >= Qualified} states three cases and
 * holds two of them: {@code StageI(Prospecting)} is refused at construction (E1903) by the same
 * rule, so a row for it is one nobody can write.
 *
 * <p>Against both readings of what the position can hold, since a rule reaches whichever of them
 * has a word for it. {@code value >= Qualified} is an interval and {@code value == Qualified} is a
 * set of values, and the two say the same thing about the same position — read against the
 * intervals alone, the first took two cases away and the second left all three, so which cases a
 * report asked for turned on how the author spelled one rule.
 *
 * <p><b>Sound whether or not the reading ran to the end of the rules.</b> What the rules leave is an
 * upper bound in either case, so a distinction they leave nothing is one this position cannot hold.
 * What the completeness beside it decides is what may be said about the ones that are left, which is
 * carried out of here rather than acted on: an admission needs every rule read, and a refusal does
 * not.
 */
final class Crossing {

    /**
     * The reading of {@code declared} against the rules.
     *
     * @param within   what the rules leave the position's numbers, or null where nothing bounds them
     * @param admitted which values the position may hold, and how much of its rules was read
     * @param stopped  a rule about this position that this compiler got partway through, or null
     *                 where there is none. A rule that went unread can refuse a distinction as
     *                 readily as one that was read, so it is what keeps an admission from being
     *                 claimed here — and a rule read from end to end cannot be that, which is why
     *                 the type takes only the stops. Handed a rule that placed no line because it
     *                 relates two positions, this called the reading partial over a position
     *                 nothing had been short of
     */
    static ReadingResult of(List<Case> declared, TypeView view, NumericDomain.Bounds within,
                            AdmissibleSet admitted, Symbols symbols,
                            DeclarationKinds kinds, PublishedDeclarations published,
                            BlockReason.RuleReadingStopped stopped) {
        List<Case> kept = admits(
                constructibleWithin(declared, view, within, symbols, kinds, published), admitted);
        List<Case> refused = new ArrayList<>(declared);
        refused.removeAll(kept);
        BlockReason.ReadingStopReason why =
                admitted.whyPartial() != null ? stopped(admitted.whyPartial()) : stopped;
        if (why != null) {
            // A rule left standing is said ahead of what the reading could not hold together, and
            // both may be true of one position. What a caller does about them is the same — the
            // values are an upper bound this cannot show is what the rules leave — so the one that
            // names a limit an author can go and look at is the one worth carrying here. What the
            // reading could not hold together is said of the position on its own, where it is not
            // competing with anything.
            return new ReadingResult.Partial(kept, refused, why);
        }
        return admitted.alternativesNotSeparated()
                ? new ReadingResult.NotSeparated(kept, refused)
                : new ReadingResult.Complete(kept, refused);
    }

    /**
     * What stopped the values reading, in the vocabulary a report is projected from.
     *
     * <p>A stop, and the type says so. Every arm of {@link souther.compiler.values.UnreadReason}
     * leaves the values here an upper bound, including the one for a rule the reading of ends took
     * in whole — {@code a < b} places no line and is no shortfall there, and here it is a rule this
     * could not turn into a set of one position's values at all. What each of them would take to
     * lift is different work, which is why they stay apart rather than becoming one word.
     */
    static BlockReason.ReadingStopReason stopped(souther.compiler.values.UnreadReason why) {
        return BlockReason.of(why);
    }

    /**
     * The distinctions left by the values the rules admit.
     *
     * <p>One rule: a distinction is dropped where the rules leave it nothing. Each is asked, since
     * knowing what it holds is what settles it — a finite set proves one empty by holding no value
     * of it, and a set written as a denial proves it by excluding every value it has, which a
     * distinction that is one value can say and one holding a record cannot
     * ({@link Case#leftAnythingBy}).
     *
     * <p>Only where the distinctions and the set are about the same values. A value none of them
     * holds is the two readings disagreeing about what stands at this position, and the
     * distinctions are the position's own: taking them away on the strength of a set that does not
     * fit them would leave a position with none because two readings of it did not line up.
     */
    private static List<Case> admits(List<Case> declared, AdmissibleSet admitted) {
        if (declared.isEmpty() || admitted.approximation().isAny()) {
            return declared;
        }
        if (admitted.approximation() instanceof ValueSet.Finite finite
                && !finite.values().isEmpty() && finite.values().stream()
                        .anyMatch(value -> declared.stream().noneMatch(each -> each.holds(value)))) {
            return declared;   // the two readings are not about the same values
        }
        return declared.stream()
                .filter(each -> each.leftAnythingBy(admitted.approximation()))
                .toList();
    }

    /** The same, against what the intervals leave. A position with no order has no value for a rule
     *  to name a place on, so nothing is taken away. */
    private static List<Case> constructibleWithin(List<Case> declared, TypeView view,
                                                  NumericDomain.Bounds within, Symbols symbols,
                                                  DeclarationKinds kinds,
                                                  PublishedDeclarations published) {
        if (within == null || declared.isEmpty()
                || !(Carrier.ofValue(view.declared(), symbols, kinds, published)
                        instanceof Carrier.Ordinal order)) {
            return declared;
        }
        Set<TypeSymbol> refused = new LinkedHashSet<>();
        for (TypeSymbol each : order.cases()) {
            Place at = order.at(each);
            if (at != null && !within.admits(at)) {
                refused.add(each);
            }
        }
        return refused.isEmpty() ? declared : declared.stream()
                .filter(each -> !(each instanceof Case.SumCase sum && refused.contains(sum.leaf())))
                .toList();
    }

    private Crossing() {}
}
