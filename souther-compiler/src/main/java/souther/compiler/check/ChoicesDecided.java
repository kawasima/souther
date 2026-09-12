package souther.compiler.check;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every choice of every rule of one declaration came to.
 *
 * <p>A table of rules and not a fact about one, because which branches anybody can be in is settled
 * over every clause of the declaration together: a branch live on its own clause can be impossible
 * under a clause written elsewhere, and dead is only dead everywhere.
 *
 * <p><b>The one place the two dimensions meet.</b> A choice is named by the rule whose clause it is
 * written in and by where in that clause it stands, and neither half names one alone — two clauses
 * each write a choice at their own occurrence nought. Everything that reads a fate takes
 * {@link ChoicesOfRule}, which is this table already scoped to a rule, so the only code that can
 * pair a rule with an occurrence is here and the only place one can be paired with the wrong rule is
 * the call that scopes it.
 */
final class ChoicesDecided {

    private final Map<RuleRef.Invariant, Map<ClauseOccurrence, Settlement.OfAChoice>> byRule;

    ChoicesDecided() {
        this.byRule = new LinkedHashMap<>();
    }

    /** The same again, so that what a projection settled is not added to by what follows it. */
    ChoicesDecided(ChoicesDecided these) {
        this.byRule = new LinkedHashMap<>();
        these.byRule.forEach((rule, fates) -> byRule.put(rule, new LinkedHashMap<>(fates)));
    }

    /** The fate of the choice written at {@code at} of {@code rule}, which nothing had before. */
    void settled(RuleRef.Invariant rule, ClauseOccurrence at, Settlement.OfAChoice fate) {
        byRule.computeIfAbsent(rule, _ -> new LinkedHashMap<>()).put(at, fate);
    }

    /**
     * The same choice met again, somewhere else it stands.
     *
     * <p>Distribution puts one written branch inside each branch of every choice met with it, and
     * what its author can act on is the whole declaration's answer — so the copies join rather than
     * overwrite, by an operation that cannot depend on the order they were met in.
     */
    void met(RuleRef.Invariant rule, ClauseOccurrence at, Settlement.OfAChoice fate) {
        byRule.computeIfAbsent(rule, _ -> new LinkedHashMap<>())
                .merge(at, fate, Settlement.OfAChoice::alsoSeen);
    }

    /** The fates of {@code rule}'s own choices, with the rule spent rather than handed on. */
    ChoicesOfRule of(RuleRef.Invariant rule) {
        Map<ClauseOccurrence, Settlement.OfAChoice> fates = byRule.get(rule);
        return fates == null ? ChoicesOfRule.NONE : new ChoicesOfRule(fates);
    }

    /** How many choices are decided here, over every rule. */
    int size() {
        return byRule.values().stream().mapToInt(Map::size).sum();
    }
}
