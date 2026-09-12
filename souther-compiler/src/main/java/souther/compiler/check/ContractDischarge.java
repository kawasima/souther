package souther.compiler.check;

import souther.compiler.core.Contract.Param;
import souther.compiler.core.Contract.Guard;
import souther.compiler.types.BindingId;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How much of what a behavior declares the check can read, rule by rule, and which of the answer's
 * cases nothing is said about.
 *
 * <p>The same classification a data's clause gets ({@link ClauseDischarge}), asked of the other kind
 * of clause. It is the same question: what the clause owes, and how much of it this compiler could
 * carry into a form a guard can be held against ({@link StaticReading}). What follows from the answer
 * differs — an invariant's classification says what discharges a construction, a rule's says how much
 * of the relation is there to be read at a call — but what is being asked of the expression is one
 * thing, and asking it twice would be two answers to keep agreeing.
 *
 * <p>The unit is the rule, and under it the conjunct. A rule is already specialized to one case, so
 * {@code ensures Todo | NotFound -> value.id == id} is classified once for {@code Todo} and once for
 * {@code NotFound} and the two may differ — what {@code value} is differs between them, and what the
 * check can make of a statement about it follows. A flat list per behavior would put the two under
 * one entry and leave a reader to work out which case it was looking at.
 */
public record ContractDischarge(List<RuleDischarge> rules,
                                List<TypeSymbol> casesNothingIsSaidAbout) {

    public ContractDischarge {
        rules = List.copyOf(rules);
        casesNothingIsSaidAbout = List.copyOf(casesNothingIsSaidAbout);
    }

    /** One conjunct of one rule, and what the check can make of it. */
    public record RuleDischarge(BehaviorContract.RuleId rule, ClauseDischarge capability) {}

    /**
     * What the check can read of what {@code stated} states, rule by rule.
     *
     * <p>Handed the rules as the analysis holds them rather than the declaration they came from.
     * Which case a rule is about, what {@code value} is there, where each conjunct was written and
     * what it types to are all settled by the one reading ({@link StatedContract}), and this asks one
     * question of it — the same reading the check at a call takes what it may assume from, so what an
     * author is shown and what a caller is given cannot drift apart.
     *
     */
    public static ContractDischarge of(StatedContract stated, RuleReadingSource source,
                                       ReadingPolicy policy) {
        List<RuleDischarge> classified = new ArrayList<>();
        for (StatedContract.StatedRule rule : stated.rules()) {
            classified.addAll(of(stated, rule, source, policy));
        }
        return new ContractDischarge(classified, unstatedCases(stated, source.published()));
    }

    /**
     * One rule, conjunct by conjunct.
     *
     * <p>Each conjunct on its own, as a data's are: what discharges one half of {@code a && b} is not
     * what discharges the other, and an author acts on the half. The name the clause was declared
     * with goes on each of them, because a violation is reported by the clause however many conjuncts
     * it was written as.
     */
    private static List<RuleDischarge> of(StatedContract contract, StatedContract.StatedRule rule,
                                          RuleReadingSource source, ReadingPolicy policy) {
        // The parameters and `value` stand for themselves. A caller hands one value per parameter and
        // the behavior answers one value, so a rule naming either names something wherever it is read
        // — entered as locations, and nothing is seeded of them, since what the rule states is the
        // question.
        Set<BindingId> named = new LinkedHashSet<>();
        for (Param param : contract.params()) {
            named.add(param.binding());
        }
        named.add(rule.value());
        // Its own reading, over the clauses as the analysis reads them. A rule may reach a
        // declaration through the values it names, and the representation those are read in is the
        // one every other reader of this module's rules uses.
        Terms naming = new Terms(Terms.Of.THE_DISCHARGE_TREE, policy, new Clauses(source));
        Denotations locations =
                Denotations.none().locations(named, naming::placeSubject, naming::placeTerm);

        List<RuleDischarge> out = new ArrayList<>();
        for (StatedContract.Conjunct conjunct : rule.conjuncts()) {
            out.add(new RuleDischarge(rule.id(), InvariantChecker
                    .capabilityOf(conjunct, locations, source, policy,
                            contract.behavior().name())
                    .named(rule.clause())));
        }
        return out;
    }

    /**
     * What the answer can be that no rule reaches.
     *
     * <p>Reaches and not names. A rule written about a case that has cases of its own states
     * something about every one of them, so counting by the name each rule spells would report a
     * leaf as unstated while a rule about the case above it stands (#966).
     *
     * <p>Nothing is stated about what is left, which is what the declaration says and not a mistake
     * in it: a rule holds where its guard does and there is nothing that holds everywhere, so an
     * answer no rule reaches is one the behavior promises nothing of. Answered here rather than
     * reported, because a correct model may say nothing about most of what it answers, and a reader
     * wanting to know asks.
     */
    private static List<TypeSymbol> unstatedCases(StatedContract contract,
                                                  PublishedDeclarations published) {
        // Over what the answer can be and not over what it declared. A rule may be written about a
        // case that has cases of its own, and it states something about each of them; counted by
        // name, every leaf under it would come back as unstated (#966).
        Set<TypeSymbol> stated = new LinkedHashSet<>();
        for (StatedContract.StatedRule rule : contract.rules()) {
            if (rule.guard() instanceof Guard.Case(ResolvedCase selected)) {
                stated.addAll(selected.atoms());
            }
        }
        List<TypeSymbol> unstated = new ArrayList<>();
        for (TypeSymbol atom : AtomSpace.subjectAtoms(contract.output(), published)) {
            if (!stated.contains(atom)) {
                unstated.add(atom);
            }
        }
        return unstated;
    }
}
