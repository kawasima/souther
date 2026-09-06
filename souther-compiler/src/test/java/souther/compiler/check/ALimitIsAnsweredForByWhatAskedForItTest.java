package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A limit this compiler reached while working out a position is answered for by whatever asked for
 * it, and not by everything that named the position.
 *
 * <p>An allowance is held per position and every rule reaching one pays into the machine it finally
 * admits. When one of those machines is refused, what is recorded is a fact about the pattern
 * somebody wrote — and where it is recorded is the position, which is what the spending is arranged
 * by. Which rule asked is not the same question as which rules name the place, and an account that
 * answered the first with the second told an author to go and change a rule that is not why.
 *
 * <p>Two things are lost by asking the position, and both are here. Which rule is answerable is one.
 * Where the reason stands among the parts the author wrote is the other: a limit recovered from a
 * position has no place among them, and a reading that hands its reasons on in the order the author
 * wrote them cannot put it back.
 */
class ALimitIsAnsweredForByWhatAskedForItTest {

    /**
     * One rule whose answer runs past the allowance, beside one rule whose own pattern is larger
     * than any machine this makes. Both are about {@code y}.
     *
     * <p>{@code a{60000}} is written in {@code huge} and nowhere else, so nothing about {@code r} is
     * why that machine was refused.
     */
    private static final String A_LIMIT_ONE_RULE_ASKED_FOR = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (String.matches("a{300}", y) && String.matches("b{300}", y))
                    || x == "A"
                invariant huge = String.matches("a{60000}", y)
            """;

    /** The same declaration with the rule that asks for nothing costly taken away. */
    private static final String THE_RULE_THAT_ASKED_FOR_IT_ALONE = """
            module demo

            data N = { x: String, y: String }
                invariant huge = String.matches("a{60000}", y)
            """;

    /**
     * The rule that asked carries the limit, and the rule beside it carries what it was short of on
     * its own.
     *
     * <p>Both halves matter. Naming {@code huge} is right and is not enough: {@code r} standing on
     * a limit its neighbour reached is an author sent to a clause that reads perfectly well.
     *
     * <p>Asked of what a rule is answerable for and not of everything its question stands on. A
     * question also stands on what the answer at its position was short of, which is a fact about
     * the place and holds of every rule waiting on it — so both of these questions carry it, and
     * neither is answerable for it.
     */
    @Test
    void aLimitIsCarriedByTheRuleThatAskedForItAndByNothingBeside() {
        Map<String, List<UnreadReason>> answerable = answerableFor(A_LIMIT_ONE_RULE_ASKED_FOR);

        assertEquals(List.of(UnreadReason.PATTERN_TOO_COSTLY), answerable.get("huge at y"),
                "the rule whose pattern was refused answers for it");
        assertEquals(List.of(), answerable.getOrDefault("r at y", List.of()),
                "and the rule beside it is answerable for nothing of it");
    }

    /** The same pattern written into two rules, which is one machine both of them asked for. */
    private static final String ONE_PATTERN_TWO_RULES_ASKED_FOR = """
            module demo

            data N = { x: String, y: String }
                invariant one = String.matches("a{60000}", y)
                invariant other = String.matches("a{60000}", y)
            """;

    /**
     * And a pattern two rules wrote is one machine that both of them are answerable for.
     *
     * <p>What a refusal is about is the pattern. A machine is made once however many rules ask for
     * it — the allowance is spent once, and the same model with the clause written twice comes to
     * the same answer — so both of the rules that asked are answerable, and neither is answerable
     * because it happened to be read first.
     */
    @Test
    void aPatternTwoRulesWroteIsOneMachineBothAreAnswerableFor() {
        Map<String, List<UnreadReason>> standing = standingOn(ONE_PATTERN_TWO_RULES_ASKED_FOR);

        assertEquals(List.of(UnreadReason.PATTERN_TOO_COSTLY), standing.get("one at y"));
        assertEquals(List.of(UnreadReason.PATTERN_TOO_COSTLY), standing.get("other at y"));
    }

    /**
     * And the position says it all the same.
     *
     * <p>The other half of what this is about, and the half that says which direction the fix runs
     * in. A position is as wide as it is because a machine was refused, and a reader of the place is
     * owed that — what is not owed is an account of a rule built back out of it. Read as "take it
     * off the position", the same test would pass with a reader told a position is exact when it is
     * not.
     */
    @Test
    void andThePositionSaysWhatItWasLeftWith() {
        assertTrue(whatThePositionWasLeftWith(A_LIMIT_ONE_RULE_ASKED_FOR)
                        .contains(UnreadReason.PATTERN_TOO_COSTLY),
                "the place a refused machine widened says so, whoever asked for the machine");
    }

    /**
     * And writing a rule beside another does not change what that other is reported as short of.
     *
     * <p>The invariant, said as the thing an author can watch. What {@code huge} is answerable for
     * is a fact about {@code huge}, so adding or removing a neighbour that asks for nothing costly
     * leaves it where it was.
     *
     * <p>What its question stands on does move, and that is not this. A neighbour whose rules the
     * answer at the position could not be built out of leaves that answer unbuilt, and every
     * question waiting on it stands on that — a fact about the place, arriving because the place
     * changed and not because {@code huge} did.
     */
    @Test
    void aRuleIsAnswerableForWhatItIsAnswerableForWhoeverIsWrittenBesideIt() {
        assertEquals(answerableFor(THE_RULE_THAT_ASKED_FOR_IT_ALONE).get("huge at y"),
                answerableFor(A_LIMIT_ONE_RULE_ASKED_FOR).get("huge at y"));
    }

    /**
     * The half of that a rule is answerable for, which is what this is about.
     *
     * <p>A question stands on what its own rule left and on what the answer at its position was
     * short of, and only the first names a rule. Asked of both, this would be watching a fact about
     * the place — which moves when the place does, whoever wrote what.
     */
    private static Map<String, List<UnreadReason>> answerableFor(String source) {
        Map<String, List<UnreadReason>> out = new LinkedHashMap<>();
        standingOn(source).forEach((question, why) -> out.put(question, why.stream()
                .filter(each -> each.about() == UnreadReason.About.A_RULE).toList()));
        return out;
    }

    /**
     * What every question of every rule that nothing answered is filed under that rule as, keyed by
     * rule and name.
     *
     * <p>What a rule is answerable for and nothing else. A question may stand on more than this —
     * an answer at its position that was not built is no rule's — and what that comes to is
     * {@code WhatAStandingQuestionIsAccountedForByTest}'s. Here the question is which rule a
     * shortfall was filed under, so what is read is the filing.
     */
    private static Map<String, List<UnreadReason>> standingOn(String source) {
        Map<String, List<UnreadReason>> out = new LinkedHashMap<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted unaccounted
                            && unaccounted.why()
                            instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.put(named(accounting) + " at " + owed, says.why());
                    }
                }));
        return out;
    }

    /** What the position the two rules are both about was left holding, and why. */
    private static List<UnreadReason> whatThePositionWasLeftWith(String source) {
        souther.compiler.values.AdmissibleSet.Completeness said =
                read(source).admits(RuleKey.of("y")).completeness();
        return said instanceof souther.compiler.values.AdmissibleSet.Completeness.Wider wider
                ? wider.why().stream()
                        .filter(souther.compiler.values.AdmissibleSet.Widening.RuleUnread.class
                                ::isInstance)
                        .map(each -> ((souther.compiler.values.AdmissibleSet.Widening.RuleUnread)
                                each).why())
                        .toList()
                : List.of();
    }

    /** The name the author gave the rule, which is how this test says which one it means. */
    private static String named(RuleAccounting accounting) {
        String printed = accounting.cited().rule().citedName();
        return printed.substring(printed.indexOf('(') + 1, printed.indexOf(')'));
    }

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(each -> each.diagnostic().code())
                .toList(), "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
