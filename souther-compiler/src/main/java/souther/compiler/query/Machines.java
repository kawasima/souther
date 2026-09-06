package souther.compiler.query;

import souther.compiler.check.ReadingPolicy;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Realization;
import souther.compiler.values.StringMachines;
import souther.compiler.values.TextExtent;
import souther.compiler.values.TextExtents;
import souther.compiler.values.ValueSet;

/**
 * The machines a reading of the rules needs, answered once for the whole compilation.
 *
 * <p>A pattern's language and where a set of strings stops are facts about the plan and the set,
 * and about nothing else: not the declaration whose rule named the pattern, not the question that
 * reached the declaration, and not which module was asking. So they are questions of the store
 * keyed by the plan and by the set, with no module and no source of their own — an edit reaches
 * them through nothing, because nothing they read can change, and a second declaration whose rules
 * come to the same strings is answered with the first one's machine.
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
            return Answer.of(policy.allowanceForAdmittedValues(of(db).lending()).realized(plan));
        }
    }

    /** Where the strings {@code set} holds stop on the order. */
    public record Extent(ValueSet set) implements Key<TextExtent> {

        @Override
        public Answer<TextExtent> compute(Db db) {
            return Answer.of(TextExtents.of(set));
        }
    }
}
