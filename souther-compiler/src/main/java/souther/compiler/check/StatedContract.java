package souther.compiler.check;

import souther.compiler.core.Contract.Param;
import souther.compiler.core.Contract.Guard;
import souther.compiler.check.BehaviorContract.Rule;
import souther.compiler.check.BehaviorContract.RuleId;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a behavior states about its answer, read into the representation the analysis has rules
 * about and typed there.
 *
 * <p>Two readers want this and they want the same thing of it. The editor asks how much of each rule
 * the check can read; the check at a call asks what of it may be taken as holding. Both are questions
 * about the rule as a term — which case it is about, which names it reads, what it comes to — so it
 * is read once, here, and each of them takes what it needs. Read twice, the answer shown to an author
 * and the answer a caller is given would be two readings that have to be kept agreeing.
 *
 * <p>Every conjunct arrives placed and typed together ({@link Conjunct}). The rest of this package
 * has been got wrong twice by carrying those apart.
 */
public record StatedContract(ValueName.Behavior behavior, List<Param> params, Type output,
                             List<StatedRule> rules) {

    public StatedContract {
        params = List.copyOf(params);
        rules = List.copyOf(rules);
    }

    /**
     * One rule: when it applies, what {@code value} is where it does, and what it states.
     *
     * <p>The clause's name travels with it because a violation is reported by the clause, and a
     * reader of one rule has no way back to the clause it was written under.
     *
     * <p>Which rule of the model this is is reached through its parts and is not held beside them.
     * Every part is a part of this rule and says so, so a rule kept here as well would be a second
     * way to one rule — two values that can be built about two rules, and a reader writing both
     * files an entry under one and describes the other.
     *
     * @param clause what the author wrote as the clause's name, where they wrote one. The syntax and
     *               not the name a report uses: {@link #ref} carries that, and a reader that built
     *               one from this would be spelling a rule a second way
     */
    public record StatedRule(Guard guard, BindingId value,
                             Optional<String> clause, List<Conjunct> conjuncts) {

        public StatedRule {
            conjuncts = List.copyOf(conjuncts);
            if (conjuncts.isEmpty()) {
                throw new IllegalArgumentException(
                        "a rule states something, so it is written in at least one part");
            }
            // And every part is a part of this rule. Said here rather than left to hold: what this
            // rule is is read off one of them, so parts naming two rules would be one state
            // answering with whichever came first.
            RuleRef.Ensures first = conjuncts.get(0).part().rule();
            for (Conjunct each : conjuncts) {
                if (!each.part().rule().equals(first)) {
                    throw new IllegalArgumentException("the parts of one rule are parts of one"
                            + " rule: " + first + " and " + each.part().rule());
                }
            }
        }

        /**
         * Which rule of the model this is, as everything filing a question about it says.
         *
         * <p>Read off the parts, which is where it arrived. The words in it are chosen from the
         * rule and the clause it was written under together ({@link BehaviorContract#refOf}), so a
         * reader assembling them here would be choosing them from whichever of the two it had.
         */
        public RuleRef.Ensures ref() {
            return conjuncts.get(0).part().rule();
        }

        /** Where in the declaration this rule is written. */
        public RuleId id() {
            return ref().rule();
        }
    }

    /**
     * One conjunct the author wrote: which part of the clause it is, where they wrote it, and what
     * it types to for the analysis.
     *
     * @param part   which of the clause's parts this is, as the split that made them issued it. A
     *               reader below draws lines from it and says which part drew each; counting the
     *               conjuncts again to get that back is a second answer to which parts there are
     * @param stated what it types to, or that typing it did not finish ({@link TypedClause})
     */
    public record Conjunct(PartId<RuleRef.Ensures> part, SourcePos at, TypedClause stated) {}

    /**
     * What a caller of this behavior may take as holding of its answer ({@link AssumedContract}).
     *
     * <p>A caller substitutes its own arguments into the terms and reads what they say. It does not
     * read where they were written, nor the ordinals the module numbered the constructs in them
     * with, nor which clause of this declaration each rule was written under. All of those are here
     * because a report is written from them, and all of them move when the declaration is edited
     * above the rule — so a caller depending on this reading whole is a caller an unrelated edit
     * reaches.
     *
     * <p>Which part of the clause a conjunct is goes for a different reason. It is not a place — it
     * says where the conjunct stands among the parts its author wrote, which is what the author
     * wrote and not where they wrote it, and it is what a reader below draws lines from. It is left
     * out because no caller reads it: what a caller may assume is what the parts come to, and which
     * of them said it is how a report names them.
     */
    public AssumedContract assumptions() {
        List<AssumedContract.AssumedRule> out = new ArrayList<>();
        for (StatedRule rule : rules) {
            List<AssumedContract.Conjunct> conjuncts = new ArrayList<>();
            for (Conjunct each : rule.conjuncts()) {
                Core form = each.stated().orNull();
                conjuncts.add(new AssumedContract.Conjunct(
                        Optional.ofNullable(form).map(TermMeaning::of)));
            }
            out.add(new AssumedContract.AssumedRule(rule.guard(), rule.value(), conjuncts));
        }
        return new AssumedContract(behavior, params, output, out);
    }

    /**
     * {@code contract} read as the analysis reads it.
     *
     * <p>Types each conjunct over the behavior's own names: the parameters as the contract says they
     * are bound, and {@code value} as what the case the rule is about holds. Which is what a caller
     * substitutes into — the names are the declaration's, and the values are the call's.
     *
     * @param declaring the module's clauses as the analysis reads them, which is what turns a
     *                  conjunct into the tree that has rules about it
     * @param helpers the signatures a rule may reach without a binding
     */
    public static StatedContract of(BehaviorContract contract, ClausesForDischarge declaring,
                                    Symbols symbols, Map<String, Type> helpers) {
        CheckContext ctx = CheckContext.of(symbols).forDischarge();
        List<StatedRule> rules = new ArrayList<>();
        for (BehaviorContract.Clause clause : contract.clauses()) {
            for (Rule rule : clause.rules()) {
                Scope scope = BehaviorChecker.scopeOf(contract, rule).reaching(helpers);
                RuleRef.Ensures ref = contract.refOf(rule);
                List<Conjunct> conjuncts = new ArrayList<>();
                for (ClausesForDischarge.ClauseReading written : declaring.conjunctsOf(
                        rule.statement(), BehaviorContract.ownerOf(contract.behavior()))) {
                    conjuncts.add(new Conjunct(written.part().idFor(ref), written.at(),
                            SecondaryClauseReading.of(written.asExpanded(),
                                    () -> new SecondaryClauseReading.Over(scope, ctx),
                                    "typing " + contract.behavior().name())));
                }
                rules.add(new StatedRule(rule.guard(), rule.value(), clause.name(), conjuncts));
            }
        }
        return new StatedContract(contract.behavior(), contract.params(), contract.output(), rules);
    }

    /** Where a rule states nothing this can read, so a reader asking what to assume has nothing to
     * take from it. */
    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
