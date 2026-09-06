package souther.compiler.query;

import souther.compiler.check.ReadingPolicy;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.Emptiness;
import souther.compiler.values.Realization;
import souther.compiler.values.StringMachines;
import souther.compiler.values.TextExtent;
import souther.compiler.values.TextExtents;
import souther.compiler.values.ValueSet;

/**
 * The machines a reading of the rules needs, answered once for the whole compilation.
 *
 * <p>A pattern's language, where a set of strings stops, and whether a language has a string
 * inside a stretch of the order are facts about the plan, the set and the pair, and about nothing
 * else: not the declaration whose rule named the pattern, not the question that reached the
 * declaration, and not which module was asking. So they are questions of the store keyed by those,
 * with no module and no source of their own — an edit reaches them through nothing, because
 * nothing they read can change, and a second declaration whose rules come to the same strings is
 * answered with the first one's machine.
 *
 * <p>What a reading may spend is not what it is lent. A machine made here is made under the
 * allowance a position is given ({@link ReadingPolicy#allowanceForAdmittedValues}), out of a purse
 * of its own for each plan, and a reading that borrows it spends nothing — as one question of a
 * position already borrows from another ({@link souther.compiler.values.Allowance#besides}). A
 * plan this could not build under that allowance is answered as such and lent to nobody; the reading
 * that asked then builds it, or fails to, under its own.
 */
public final class Machines {

    private Machines() {}

    /** Where a reading gets a machine, asking this store. */
    public static StringMachines of(Db db) {
        return new AnsweredBy(db);
    }

    /**
     * The lender that asks the store. A record over the store so that two readings of one
     * compilation hold the same one, the way a compilation's other handles are held.
     */
    private record AnsweredBy(Db db) implements StringMachines {

        @Override
        public ValueSet lent(AdmittedPlan plan) {
            // Only a plan that builds something is worth a question: everything, nothing and a set
            // the rules wrote out are answered by the plan itself, and asking the store for those
            // would file a row for every literal a rule names.
            return switch (plan) {
                case AdmittedPlan.Everything _, AdmittedPlan.Nothing _, AdmittedPlan.Of _ -> null;
                case AdmittedPlan.Pattern _, AdmittedPlan.Both _, AdmittedPlan.Either _ ->
                        db.ask(new Realized(plan)).value() instanceof Realization.Exact it
                                ? it.set() : null;
            };
        }

        @Override
        public TextExtent extentOf(ValueSet set) {
            return db.ask(new Extent(set)).value();
        }

        @Override
        public Emptiness inside(Language language, OrderedInterval held, Meter meter) {
            // The asker's meter is not spent: the answer is made once under an allowance of the
            // pair's own, and what it came to is a fact about the pair whoever asks.
            return db.ask(new Inside(language, held)).value();
        }
    }

    /**
     * What a plan admits, built under an allowance of the plan's own.
     *
     * <p>Built out of its parts through this same question, so a part somebody has already asked
     * for is not made again here — and a plan that is over what one machine may be is answered as
     * that, which is a fact about the plan and is kept as one.
     */
    public record Realized(AdmittedPlan plan) implements Key<Realization> {

        @Override
        public Answer<Realization> compute(Db db) {
            ReadingPolicy policy = db.ask(new Front.Reading()).value();
            // The parts are borrowed and the plan itself is built: a realizer asks its lender for
            // the plan before building it, and the lender for this plan is this question.
            StringMachines parts = of(db);
            Allowance<Object> allowance = policy.allowanceForAdmittedValues(
                    (_, asked) -> asked.equals(plan) ? null : parts.lent(asked));
            return Answer.of(allowance.realized(plan));
        }
    }

    /** Where the strings {@code set} holds stop on the order. */
    public record Extent(ValueSet set) implements Key<TextExtent> {

        @Override
        public Answer<TextExtent> compute(Db db) {
            return Answer.of(TextExtents.of(set));
        }
    }

    /**
     * Whether any string {@code language} admits lies inside {@code held}, a stretch of the order
     * whose ends are strings.
     *
     * <p>Under an allowance of the pair's own, the one deciding whether a set and a range share a
     * value is given ({@link PatternPlan.Budget#OF_WHAT_A_SET_AND_A_RANGE_SHARE}): what a
     * declaration is read to hold cannot turn on what the readings before it built, and it does not
     * here either, since the pair is answered the same wherever it is first met.
     */
    public record Inside(Language language, OrderedInterval held) implements Key<Emptiness> {

        @Override
        public Answer<Emptiness> compute(Db db) {
            return Answer.of(TextExtents.inside(language, held,
                    PatternPlan.Budget.OF_WHAT_A_SET_AND_A_RANGE_SHARE.meter()));
        }
    }
}
