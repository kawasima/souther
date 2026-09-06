package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.UnreadReason;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A question a rule leaves standing is accounted for by a reading or by the answer, and the two are
 * told apart.
 *
 * <p>Three causes reach one outcome and only two of them are about a rule. A rule whose form no
 * reading takes apart is the reading's own account; a rule read in full whose exact answer ran past
 * the allowance is an account of the answer, which no rule is answerable for
 * ({@link ReadingEvidence#stoppedBy} refuses to file one). A question with neither is the accounting
 * disagreeing with itself and is refused where it is made.
 *
 * <p>The third case is what this is for. Reaching an admitted-values question with no reading having
 * recognised the rule's positions was one, and a helper was the way to it: the clause was read at
 * the names the expansion's binding introduced and every reading passed over it. Going inside the
 * binding closed that (ADR-0106), so the helper here is held to answering as the rule written out
 * does — and the answer-level case is held beside it, so that closing one is not read as the other
 * having gone away.
 */
class WhatAStandingQuestionIsAccountedForByTest {

    /**
     * A rule no reading takes apart, written where the clause is.
     *
     * <p>The reading recognised the position and gave up on the rule, which is its own account to
     * give.
     */
    private static final String A_FORM_NO_READING_TAKES_APART = """
            module demo

            data Range = { min: String, max: String }

            data N = { range: Range }
                invariant UNREAD_MAX
            """.replace("UNREAD_MAX", souther.compiler.ARuleNoReadingTakesIn.about("range.max"));

    /** The same rule, reached through a helper, which is the same rule about the same position. */
    private static final String THE_SAME_FORM_THROUGH_A_HELPER = """
            module demo

            data Range = { min: String, max: String }

            data N = { range: Range }
                invariant valid(range)

            let valid (r: Range) : Bool = UNREAD_MAX
            """.replace("UNREAD_MAX", souther.compiler.ARuleNoReadingTakesIn.about("r.max"));

    /**
     * Rules read in full whose answer is more than the allowance will build.
     *
     * <p>Each pattern is small and both are taken in; showing the branch they share empty takes a
     * machine of about ninety thousand states. So nothing is short of a rule here, and what is short
     * is the answer they come to between them — which is why the question stands with no reading
     * naming a rule. The same model is read for what it leaves the position in
     * {@code ABranchNobodyCouldWorkOutIsNotOneAnybodyReadTest}.
     */
    private static final String AN_ANSWER_BEYOND_THE_ALLOWANCE = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (String.matches("a{300}", y) && String.matches("b{300}", y))
                    || x == "A"
            """;

    /**
     * A rule relating two positions, written out and reached through a helper standing in one of
     * its operands.
     *
     * <p>What the reading recognised is what tells a relation from a form it has no word for, so
     * a binding it did not go inside turned {@code hi >= lo} into a shape nothing reads — and an
     * author was sent after the form of a rule whose form was never the matter. Here because it is
     * the account rather than the verdict that moves: both spellings leave the position open either
     * way, and only the reason says which.
     */
    private static final String A_RELATION_WRITTEN_OUT = """
            module demo

            data N =
                { lo: Int
                , hi: Int
                }
                invariant hi >= lo
            """;

    private static final String THE_SAME_RELATION_THROUGH_A_HELPER = """
            module demo

            let itself (n: Int) = n

            data N =
                { lo: Int
                , hi: Int
                }
                invariant hi >= itself(lo)
            """;

    @Test
    void aRelationIsOneWhicheverSideReachesItThroughAHelper() {
        assertEquals(souther.compiler.values.UnreadReason.RELATES_TWO_POSITIONS,
                whyWider(A_RELATION_WRITTEN_OUT, "lo"),
                "both sides are recognised, so the rule relates two positions");
        assertEquals(whyWider(A_RELATION_WRITTEN_OUT, "lo"),
                whyWider(THE_SAME_RELATION_THROUGH_A_HELPER, "lo"),
                "and a binding standing in an operand is not a form nothing reads");
    }

    /** What the values reading was left short by at one name. */
    private static souther.compiler.values.UnreadReason whyWider(String source, String field) {
        return ((souther.compiler.values.AdmissibleSet.Widening.RuleUnread)
                ((souther.compiler.values.AdmissibleSet.Completeness.Wider)
                        read(source).admits(RuleKey.of(field)).completeness())
                        .why().iterator().next()).why();
    }

    @Test
    void aFormNoReadingTakesApartIsTheReadingsOwnAccount() {
        assertEquals(Map.of("invariant N #1 at range.max", "TheValueReadingSays"),
                whyStanding(A_FORM_NO_READING_TAKES_APART));
    }

    /** And reaching that rule through a helper does not change whose account it is. */
    @Test
    void reachingThatRuleThroughAHelperLeavesTheSameAccount() {
        assertEquals(whyStanding(A_FORM_NO_READING_TAKES_APART),
                whyStanding(THE_SAME_FORM_THROUGH_A_HELPER));
    }

    /**
     * And a rule the reading took in whole leaves a question the answer accounts for.
     *
     * <p>No rule is answerable for it, so nothing is filed under the rule and the account is read
     * off the position. What it says is the limit itself: an arm meaning no reading claimed the
     * rule would send an author after a reader that was never short of their clause.
     */
    @Test
    void anAnswerBeyondTheAllowanceIsWhatTheQuestionStandsOn() {
        assertEquals(Map.of("invariant N (r) at y",
                        new StandsOn(List.of(), Set.of(UnreadReason.EXACT_VALUES_TOO_COSTLY))),
                standingOn(AN_ANSWER_BEYOND_THE_ALLOWANCE),
                "the rule has nothing of its own, and what the question stands on is the answer's");
    }

    /**
     * And a rule short in both ways stands on both.
     *
     * <p>One rule, one position, two limits of its own: a conjunct nothing reads, and a choice
     * beside it whose meet ran past the allowance. Neither is the other's account — rewriting the
     * form leaves the answer unbuilt, and allowing more leaves the form unread — and the two are
     * different words out there and different answers about what a wider run would get past.
     *
     * <p>Which is why the account asks the position beside the rule and not instead of it. Taken as
     * a fallback, this question went out short in the one way its rule had filed, and an author who
     * did what they were told found the position exactly as wide as before.
     */
    @Test
    void aRuleShortInBothWaysStandsOnBoth() {
        assertEquals(new StandsOn(List.of(UnreadReason.FORM_NOT_READ),
                        Set.of(UnreadReason.EXACT_VALUES_TOO_COSTLY)),
                standingOn(A_FORM_NO_READING_TAKES_APART_BESIDE_AN_ANSWER_BEYOND_THE_ALLOWANCE)
                        .get("invariant N (r) at y"),
                "both, each where it belongs — the form under the rule that wrote it, and the"
                        + " limit at the position whose answer ran into it");
    }

    /**
     * And a rule short in one way is spelled the way it was before there was a second.
     *
     * <p>The control on the one above. Asking the position beside the rule leaves every question
     * that stands on one reason alone exactly where it was — the reasons a reader had, in the order
     * they had them.
     */
    @Test
    void askingThePositionLeavesAQuestionShortInOneWayWhereItWas() {
        assertEquals(new StandsOn(List.of(UnreadReason.FORM_NOT_READ), Set.of()),
                standingOn(A_FORM_NO_READING_TAKES_APART).get("invariant N #1 at range.max"));
    }

    /**
     * A rule with a conjunct nothing reads, written beside a choice about the same position whose
     * meet is past what the allowance builds.
     *
     * <p>Both limits are this rule's own, which is what makes it the pair rather than a rule
     * carrying a neighbour's. The conjunct is refused by every reading of clauses; the two patterns
     * beside it are read in full and meet at about ninety thousand states.
     *
     * <p>A conjunction and no choice in it. A rule whose branch a reading gave up on is answerable
     * for an alternative nothing read, which is a third thing to say about this rule and not the
     * pair this is about — held here, the test would move whenever what a choice is answerable for
     * moved.
     */
    private static final String
            A_FORM_NO_READING_TAKES_APART_BESIDE_AN_ANSWER_BEYOND_THE_ALLOWANCE = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    String.matches("a{300}", y) && String.matches("b{300}", y) && UNREAD_Y
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /**
     * And a reason about neither is no account of a question a rule raised.
     *
     * <p>Asked of the two carriers a question's account is made of, because that is where such a
     * reason would have to be written down. Neither takes one: what a rule is answerable for is
     * about a rule by construction, and what an answer was short of is about the answer.
     */
    @Test
    void aReasonAboutNeitherIsNoAccountOfARulesQuestion() {
        Arrays.stream(UnreadReason.values())
                .filter(each -> each.about() == UnreadReason.About.NEITHER)
                .forEach(each -> assertThrows(IllegalArgumentException.class,
                        () -> new AnswerShortfalls(Set.of(each)),
                        () -> each + " reached no position, so the answer was not short of it"));
    }

    /** What one question stands on, held as the two things it is made of. */
    private record StandsOn(List<UnreadReason> itsRule, Set<UnreadReason> itsAnswer) {}

    /**
     * What every question of every rule that nothing answered stands on, as its two halves.
     *
     * <p>Read apart, because that is how the account holds them. A test flattening the two would be
     * asserting an order between a form somebody wrote and a limit the rules ran into together,
     * which is the order this is about there being none of.
     */
    private static Map<String, StandsOn> standingOn(String source) {
        Map<String, StandsOn> out = new LinkedHashMap<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted unaccounted
                            && unaccounted.why()
                            instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.put(accounting.cited().rule().citedName() + " at " + owed,
                                new StandsOn(says.why(), says.aboutTheAnswer().reasons()));
                    }
                }));
        return out;
    }

    /**
     * And what may stand in for a rule's own account is the answer, and nothing else.
     *
     * <p>The negative control on the refusal. A reason is about a rule, about the answer, or about
     * neither, and only the second is an account of a question the rule left standing: the third is
     * a reading that never reached the position, which accounts for nothing — as its name says.
     *
     * <p>Enumerated from {@link UnreadReason} rather than listed, so a reason added to either of the
     * other two cannot quietly become an account by being written where this one is not looking.
     * Asked of the classification because that is what the refusal asks: written as "not about a
     * rule", one of the third kind at the name would stand in for an account that is not there.
     */
    @Test
    void onlyAReasonAboutTheAnswerStandsInForARulesOwnAccount() {
        assertEquals(Set.of(UnreadReason.About.THE_ANSWER), aboutWhat(true),
                "only a reason about the answer stands in for what a rule would have said");
        assertEquals(Set.of(UnreadReason.About.A_RULE, UnreadReason.About.NEITHER), aboutWhat(false),
                "a rule's own reason is filed under the rule, and a reason about neither accounts"
                        + " for nothing — the refusal must take neither for the above");
        // And every kind is decided, so the two above are the whole of the classification rather
        // than the part of it this enum happens to hold reasons for today.
        assertEquals(Set.of(UnreadReason.About.values()),
                Set.copyOf(union(aboutWhat(true), aboutWhat(false))));
    }

    /** What the reasons that do, or do not, stand in for a rule's own account are about. */
    private static Set<UnreadReason.About> aboutWhat(boolean standingIn) {
        return Arrays.stream(UnreadReason.values())
                .filter(each -> FieldDomains.standsBesideARulesOwnAccount(each) == standingIn)
                .map(UnreadReason::about)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UnreadReason.About> union(Set<UnreadReason.About> one,
                                                 Set<UnreadReason.About> other) {
        Set<UnreadReason.About> out = new LinkedHashSet<>(one);
        out.addAll(other);
        return out;
    }

    /** What became of every question of every rule that nothing answered. */
    private static Map<String, String> whyStanding(String source) {
        Map<String, String> out = new LinkedHashMap<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted unaccounted) {
                        out.put(accounting.cited().rule().citedName() + " at " + owed,
                                unaccounted.why().getClass().getSimpleName());
                    }
                }));
        return out;
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
