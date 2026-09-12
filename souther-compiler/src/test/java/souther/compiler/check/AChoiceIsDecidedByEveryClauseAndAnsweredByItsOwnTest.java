package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourcePos;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which branches of a choice anybody can be in is the whole declaration's answer, and what a rule
 * left unread is that rule's own.
 *
 * <p>The two pull the reading in different directions and both hold at once. A branch live on its
 * own clause can be impossible under a clause written later, so the decision waits for every clause
 * — and a branch nobody anywhere can be in takes its unread rules with it, since there is no branch
 * for an author to look at. But a clause a neighbour wrote does not read this rule's alternatives
 * for it: whether an alternative of this rule went unread is answered over this rule's clauses
 * alone, or the gap is hidden by a constraint that happens to stand beside it
 * ({@code EveryPartAReadingStoppedOnSaysWhyTest} holds that end).
 *
 * <p>And a branch's fate is aggregated over every place distribution put it. The same written
 * choice stands inside each branch of every choice met with it, live in one place and dead in
 * another — dead is only dead everywhere, and the aggregate cannot depend on the order the clauses
 * were written in.
 */
class AChoiceIsDecidedByEveryClauseAndAnsweredByItsOwnTest {

    private static final String UNREAD_TAG = souther.compiler.ARuleNoReadingTakesIn.about("tag");
    private static final String UNREAD_S = souther.compiler.ARuleNoReadingTakesIn.about("s");

    /**
     * A branch live on its own clause and impossible under the next one.
     *
     * <p>Reading {@code one} alone, both alternatives admit something, and the rule this reading
     * has no word for stands only in the left one. {@code two} then leaves nobody able to be in
     * that branch.
     */
    private static final String A_LATER_CLAUSE_KILLS_A_BRANCH = """
            module demo

            data Pair = { code: String, tag: String }
                invariant one =
                    (code == "a" && UNREAD_TAG)
                    || code == "c"
                invariant two = code == "c"
            """.replace("UNREAD_TAG", UNREAD_TAG);

    /**
     * One written choice standing in both branches of another, live in one and dead in the other.
     *
     * <p>Distribution puts each alternative of {@code b} beside each alternative of {@code a}:
     * {@code x == 0} is impossible beside {@code x == 1} and possible beside {@code x == 0}. Both
     * of {@code b}'s branches are live somewhere, so neither is dead.
     */
    private static final String LIVE_SOMEWHERE_IS_LIVE = """
            module demo

            data N = { x: Int, s: String }
                invariant a = x == 0 || x == 1
                invariant b =
                    (x == 0 && UNREAD_S)
                    || x == 1
            """.replace("UNREAD_S", UNREAD_S);

    /**
     * A branch dead in every place it stands.
     *
     * <p>{@code x == 2} is impossible beside {@code x == 0} and beside {@code x == 1}, so the
     * branch carrying the rule nothing reads is dead everywhere — and only then do its unread
     * rules go with it.
     */
    private static final String DEAD_EVERYWHERE_IS_DEAD = """
            module demo

            data M = { x: Int, s: String }
                invariant a = x == 0 || x == 1
                invariant b =
                    (x == 2 && UNREAD_S)
                    || x == 0 || x == 1
            """.replace("UNREAD_S", UNREAD_S);

    /** The same two rules with the clauses the other way round. */
    private static final String THE_OTHER_CLAUSE_ORDER = """
            module demo

            data M = { x: Int, s: String }
                invariant b =
                    (x == 2 && UNREAD_S)
                    || x == 0 || x == 1
                invariant a = x == 0 || x == 1
            """.replace("UNREAD_S", UNREAD_S);

    /**
     * A question a rule of a dead branch raised is settled, not left standing.
     *
     * <p>The branch is live on its own clause — this is what waiting for every clause buys. Left
     * standing, an author is sent to a rule of a branch their own next clause already forbids.
     */
    @Test
    void aBranchALaterClauseForbidsTakesItsUnreadRulesWithIt() {
        Map<String, List<UnreadReason>> standing =
                byQuestion(read(A_LATER_CLAUSE_KILLS_A_BRANCH, "Pair"));
        assertFalse(standing.containsKey("invariant Pair (one) at tag"),
                "nothing satisfies the branch the pattern is in, so there is no branch to look at");
    }

    /**
     * A branch live beside one alternative of a neighbour is live, however dead it is beside
     * another.
     *
     * <p>Live, so the rule this reading has no word for inside it is still a rule somebody wrote
     * about a branch somebody can be in — the question at {@code s} stands. What would fail here is
     * a fate recorded per place rather than aggregated: whichever copy was settled last would win,
     * the branch would be reported dead, and the question would be settled with it.
     */
    @Test
    void aBranchLiveAnywhereIsLive() {
        Map<String, List<UnreadReason>> standing =
                byQuestion(read(LIVE_SOMEWHERE_IS_LIVE, "N"));
        assertTrue(standing.containsKey("invariant N (b) at s"),
                "somebody can be in the branch, so its unread rule is theirs to look at: "
                        + standing);
    }

    /** Dead is only dead everywhere — and then the unread rule goes with the branch. */
    @Test
    void aBranchDeadEverywhereIsDead() {
        Map<String, List<UnreadReason>> standing =
                byQuestion(read(DEAD_EVERYWHERE_IS_DEAD, "M"));
        assertFalse(standing.containsKey("invariant M (b) at s"),
                "no alternative of `a` admits the branch, so there is no branch to look at");
    }

    /**
     * The aggregate reads the same whichever clause is written first.
     *
     * <p>Written the other way round, the same written choice is distributed into different
     * places in a different order. A fate that depended on either would answer the same model two
     * ways.
     */
    @Test
    void aFateDoesNotTurnOnTheOrderTheClausesWereWritten() {
        assertEquals(byQuestion(read(DEAD_EVERYWHERE_IS_DEAD, "M")),
                byQuestion(read(THE_OTHER_CLAUSE_ORDER, "M")));
    }

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject CONSTRAINED = FactSubject.of(NAMES.written("constrained"));
    private static final FactSubject SETTLED = FactSubject.of(NAMES.written("settled"));
    private static final FactSubject UNREAD = FactSubject.of(NAMES.written("unread"));

    /**
     * What a rule is answerable for says which choice left the constraint open.
     *
     * <p>What an author is sent to is this choice, which is what offered the alternative nothing
     * could read: a leaf under the branch that was read is a clause nothing complained of. The
     * position's own account is not this and is somewhere else — it says the position is open and
     * says nothing about what anybody wrote, since every rule reaching it is in it.
     *
     * <p>And an unread alternative widens what a branch constrained, and not what a dead branch
     * inside it settled: a position a dead branch settled holds an answer, so the choice imposes
     * nothing there and a further alternative, read or not, imposes nothing extra either
     * ({@code Adoption.either} draws the same line). Asked over what the branch mentions instead,
     * an author would be sent to a choice about a position that is answered.
     */
    @Test
    void whatLeftTheConstraintOpenIsTheChoiceItWasOffered() {
        ChoiceSite choice = aChoice();

        assertEquals(java.util.Set.of(new RuleShortfall(CONSTRAINED,
                        UnreadReason.ALTERNATIVE_NOT_READ,
                        choice)),
                theBranchRead(java.util.Set.of())
                        .either(choice, opened(choice), NEITHER_HOLDS_A_POSITION_DOWN,
                                theBranchNothingRead(java.util.Set.of()))
                        .ruleShortfalls(),
                "an author is sent to the choice that offered the alternative, and to nothing"
                        + " about the position the branch settled");
    }

    /** And one the unread branch holds does account for it. */
    @Test
    void aShortfallOnlyTheUnreadBranchHoldsAnswersForThePosition() {
        RuleShortfall inside = new RuleShortfall(CONSTRAINED, UnreadReason.ALTERNATIVE_NOT_READ,
                aChoice());
        ChoiceSite choice = aChoice();

        assertEquals(java.util.Set.of(inside),
                theBranchRead(java.util.Set.of())
                        .either(choice, opened(choice), NEITHER_HOLDS_A_POSITION_DOWN,
                                theBranchNothingRead(java.util.Set.of(inside)))
                        .ruleShortfalls(),
                "answering it settles the position through this branch, which takes the choice's"
                        + " shortfall with it");
    }

    /**
     * And which of the two is not asked of where the shortfall was written.
     *
     * <p>A form is one thing an author can lift and a choice is another, and neither tells whether
     * lifting it is what would leave the branch readable. Asked of the site instead, a site added
     * later would have this answer written for it a second time.
     */
    @Test
    void andWhichOfTheTwoIsNotAskedOfWhereItWasWritten() {
        RuleShortfall form = new RuleShortfall(CONSTRAINED, UnreadReason.FORM_NOT_READ,
                new RuleShortfall.Site.AtALeaf(new ClauseOccurrence(0), new SourcePos(1, 1)));
        ChoiceSite choice = aChoice();

        assertEquals(java.util.Set.of(form),
                theBranchRead(java.util.Set.of())
                        .either(choice, opened(choice), NEITHER_HOLDS_A_POSITION_DOWN,
                                theBranchNothingRead(java.util.Set.of(form)))
                        .ruleShortfalls(),
                "a form the unread branch holds accounts for the position, exactly as a choice"
                        + " under it would");
    }

    /**
     * What a choice left open, decided over the two alternatives as they were written.
     *
     * <p>Two answers of one decision, and they are not the same set. An author has to look at the
     * choice wherever the alternative beside the unread one reached a position; a position is
     * reported wider than the rules only where the choice would be narrower without the unread
     * alternative, which is settled where the values are and arrives here. What neither of them
     * holds is a position the alternative merely settled — a branch nobody can be in settles what
     * it named, and a choice imposes nothing extra there.
     */
    @Test
    void whatAChoiceLeftOpenIsWhatTheAlternativeBesideTheUnreadOneReachedAndWidened() {
        StatedByClauses.AlternativeOpening opened = StatedByClauses.opens(new ClauseOccurrence(0),
                widthRestingOnTheRight(),
                theBranchRead(java.util.Set.of()),
                theBranchNothingRead(java.util.Set.of()));

        assertEquals(java.util.Set.of(CONSTRAINED), opened.byValues().byTheRightGoingUnread(),
                "an author is sent here about the position the branch beside it constrained, and"
                        + " not about the one it settled");
        assertEquals(java.util.Set.of(CONSTRAINED), opened.byValues().positions(),
                "and the position hears about it, the width there being the unread branch's");
        assertEquals(java.util.Set.of(), opened.byValues().byTheLeftGoingUnread(),
                "the left alternative was read, so nothing is open by its going unread");
        assertEquals(java.util.Set.of(), opened.byOrder().positions(),
                "and the reading of order read both alternatives, so the width it could not"
                        + " account for is not something an unread alternative left open");
        assertEquals(java.util.Set.of(), opened.byOrder().byTheRightGoingUnread(),
                "and it sends an author nowhere for the same reason: which alternative went"
                        + " unread is each reading's own, and the ends had a word for both");
    }

    /**
     * A position neither alternative is why the choice is wide at is one the choice can still speak
     * for, however many of them went unread.
     *
     * <p>{@code (P(a) && f(b)) || (P(a) && f(b))} holds {@code a} exactly where {@code P} does:
     * either branch dropped leaves the other saying the same thing there, so nothing about
     * {@code a} rests on the branch that may hold nothing.
     */
    @Test
    void aPositionNoAlternativeWidenedIsNotOpenedByEitherOfThemGoingUnread() {
        StatedByClauses.AlternativeOpening opened = StatedByClauses.opens(new ClauseOccurrence(0),
                Settlement.WidthDependency.none(),
                theBranchNothingRead(java.util.Set.of()),
                theBranchNothingRead(java.util.Set.of()));

        assertEquals(java.util.Set.of(), opened.byValues().positions(),
                "the choice is as wide as it is at every position without either of them");
    }

    /**
     * And where one of two unread alternatives is what the width rests on, the position is opened.
     *
     * <p>The rule holds of a choice neither alternative of which was read whole, as it holds of one
     * where the branch beside the unread one was: {@code (a == A && f(b)) || f(b)} leaves {@code a}
     * at every value only because the right branch is one this compiler cannot show anybody is in.
     * Answered off what the branches took in, both of them holding a clause nothing read would say
     * neither promised anything, and the reading would speak for {@code a}.
     */
    @Test
    void whereTwoUnreadAlternativesAreOneTheWidthRestsOnThePositionIsStillOpened() {
        StatedByClauses.AlternativeOpening opened = StatedByClauses.opens(new ClauseOccurrence(0),
                widthRestingOnTheRight(),
                theBranchNothingRead(java.util.Set.of()),
                theBranchNothingRead(java.util.Set.of()));

        assertEquals(java.util.Set.of(CONSTRAINED), opened.byValues().positions(),
                "the right alternative is why the choice is that wide, and nothing read it");
    }

    /**
     * What that choice left open, which the branches below are read against.
     *
     * <p>The constrained position and not the settled one: a branch nobody can be in settles the
     * positions it named, and a choice imposes nothing extra there. Which positions a choice opens
     * is worked out over the clause as its author wrote it and handed here, so what these tests
     * hold is the other half — which of them a rule is still answerable for.
     */
    private static StatedByClauses.AlternativeOpening opened(
            ChoiceSite choice) {
        return new StatedByClauses.AlternativeOpening(choice.at(),
                new Opening<>(java.util.Set.of(CONSTRAINED), java.util.Set.of(),
                        java.util.Set.of(CONSTRAINED)),
                Opening.nothing());
    }

    /**
     * A choice neither reading could show is as wide as it is without its right alternative.
     *
     * <p>Both languages, and the same position in each. What silences the ordered half in the tests
     * below is that its account read every alternative, and a width of nothing there would silence
     * it whatever the accounts said.
     */
    private static Settlement.WidthDependency widthRestingOnTheRight() {
        return new Settlement.WidthDependency(
                new Settlement.Width<>(java.util.Set.of(), java.util.Set.of(CONSTRAINED)),
                new Settlement.Width<>(java.util.Set.of(), java.util.Set.of(CONSTRAINED)));
    }

    /**
     * And the reading of ends is sent to the choice by its own alternative going unread.
     *
     * <p>The other half of the same fact, and the one nothing else here reaches. Which alternative
     * a reading had no word for is that reading's, and so is where the branch beside it reached —
     * so an opening of the ends is not the values' answer with another name on it, and holding only
     * the empty case would leave a half that is always empty passing for one that is right.
     *
     * <p>The values read both alternatives here, which is what makes the two answers differ: the
     * ends send an author to this choice and the values send them nowhere, over one written
     * {@code ||}.
     */
    @Test
    void andTheEndsAreSentToTheChoiceByTheirOwnAlternativeGoingUnread() {
        StatedByClauses.AlternativeOpening opened = StatedByClauses.opens(new ClauseOccurrence(0),
                widthRestingOnTheRight(),
                theBranchTheEndsCouldNotRead(), theBranchTheEndsRead());

        assertEquals(java.util.Set.of(CONSTRAINED), opened.byOrder().byTheLeftGoingUnread(),
                "the ends had no word for the left alternative, so an author is sent here about"
                        + " the position the right one reached");
        assertEquals(java.util.Set.of(), opened.byOrder().byTheRightGoingUnread(),
                "and nowhere for the right, which they read");
        assertEquals(java.util.Set.of(), opened.byValues().byTheLeftGoingUnread(),
                "and the values read both, so what they send an author to is not this");
        assertEquals(java.util.Set.of(), opened.byValues().byTheRightGoingUnread(),
                "either way round");
    }

    /** A branch the ends had no word for, the values having read it. */
    private static StatedByClauses.Part theBranchTheEndsCouldNotRead() {
        return new StatedByClauses.Part(
                new Adoption<>(java.util.Set.of(CONSTRAINED), java.util.Set.of(),
                        java.util.Set.of(), false, java.util.Set.of()),
                new Adoption<>(java.util.Set.of(), java.util.Set.of(),
                        java.util.Set.of(UNREAD), true, java.util.Set.of()),
                NOTHING_STOPPED,
                Map.of(), java.util.Set.of(), java.util.Set.of(), EndsLeftOpen.nothing(),
                BoundaryState.nothing(), java.util.Map.of());
    }

    /** And the alternative beside it that both of them read, constraining one position. */
    private static StatedByClauses.Part theBranchTheEndsRead() {
        return new StatedByClauses.Part(
                new Adoption<>(java.util.Set.of(CONSTRAINED), java.util.Set.of(),
                        java.util.Set.of(), false, java.util.Set.of()),
                new Adoption<>(java.util.Set.of(CONSTRAINED), java.util.Set.of(),
                        java.util.Set.of(), false, java.util.Set.of()),
                NOTHING_STOPPED,
                Map.of(), java.util.Set.of(), java.util.Set.of(), EndsLeftOpen.nothing(),
                BoundaryState.nothing(), java.util.Map.of());
    }

    /** No part here states an end, so none of them stops a position short of its order. */
    private static final java.util.Set<FactSubject> NOTHING_STOPPED = java.util.Set.of();

    /** What each alternative leaves, which nothing here states an end about and which is why
     *  these branches leave the position wherever they found it. */
    private static final WhatTheAlternativesLeave NEITHER_HOLDS_A_POSITION_DOWN =
            WhatTheAlternativesLeave.nothing();

    /** One choice somebody wrote, told from every other by where in its clause it stands. */
    private static ChoiceSite aChoice() {
        return aChoiceWrittenAt(0);
    }

    /** The choice written at {@code occurrence} of the clause, which is what names one. */
    private static ChoiceSite aChoiceWrittenAt(int occurrence) {
        return new ChoiceSite(new ClauseOccurrence(occurrence), new SourcePos(1, 1));
    }

    /** A branch that was read, constraining one position and settling another. */
    private static StatedByClauses.Part theBranchRead(java.util.Set<RuleShortfall> shortfalls) {
        return new StatedByClauses.Part(
                new Adoption<>(java.util.Set.of(CONSTRAINED), java.util.Set.of(SETTLED),
                        java.util.Set.of(), false, java.util.Set.of()),
                Adoption.nothing(), NOTHING_STOPPED, Map.of(), java.util.Set.of(), shortfalls,
                EndsLeftOpen.nothing(),
                BoundaryState.nothing(), java.util.Map.of());
    }

    /** And the alternative beside it that nothing could read. */
    private static StatedByClauses.Part theBranchNothingRead(
            java.util.Set<RuleShortfall> shortfalls) {
        return new StatedByClauses.Part(
                new Adoption<>(java.util.Set.of(), java.util.Set.of(), java.util.Set.of(UNREAD),
                        true, java.util.Set.of()),
                Adoption.nothing(), NOTHING_STOPPED, Map.of(), java.util.Set.of(), shortfalls,
                EndsLeftOpen.nothing(),
                BoundaryState.nothing(), java.util.Map.of());
    }

    /**
     * And two choices leaving one position open are two things an author can look at.
     *
     * <p>Two of them because their author wrote them at two places in the clause. Two readings
     * meeting one written choice are one thing to look at and are told so by standing at one
     * occurrence, which is the other half of this and is what an identity per object could not
     * say.
     */
    @Test
    void twoChoicesLeavingOnePositionOpenAreTwo() {
        ChoiceSite one = aChoiceWrittenAt(0);
        ChoiceSite other = aChoiceWrittenAt(3);

        assertEquals(2, java.util.Set.of(
                        new RuleShortfall(CONSTRAINED, UnreadReason.ALTERNATIVE_NOT_READ,
                                one),
                        new RuleShortfall(CONSTRAINED, UnreadReason.ALTERNATIVE_NOT_READ,
                                other)).size(),
                "the position is open twice and there are two clauses to look at; held as reasons"
                        + " at the position they were one, and which of them a reader was sent to"
                        + " was whichever the walk met first");
    }

    /**
     * A fate aggregates the same whichever occurrence comes first — the reasons included.
     *
     * <p>{@code Emptiness.joined} alone being commutative is not enough: the reasons probing two
     * occurrences left behind travel with the fate, and two occurrences of one branch can be
     * stopped by two limits. Kept in the order the occurrences were met, the same model written
     * with its clauses the other way round would say the same reasons in a different order — a
     * neighbouring clause's order, which is no order of this rule's.
     */
    @Test
    void aFateAggregatesTheSameWhicheverOccurrenceComesFirst() {
        // Two of the same half, which is where an order could reach the answer. A pair of
        // shortfalls held in the order the copies were met would come out one way round from one
        // side and the other way round from the other, and the aggregate of a branch would be a
        // fact about which copy was settled first.
        Settlement.Sided one = new Settlement.Sided(
                Confinement.Admission.left(souther.compiler.values.Emptiness.UNDECIDED), Map.of(),
                java.util.Set.of(new souther.compiler.values.Unbuilt.RuleShortfall<>(UNREAD,
                        aPattern("a{300}"), UnreadReason.PATTERN_TOO_COSTLY)),
                java.util.Set.of());
        Settlement.Sided other = new Settlement.Sided(
                Confinement.Admission.left(souther.compiler.values.Emptiness.UNDECIDED), Map.of(),
                java.util.Set.of(new souther.compiler.values.Unbuilt.RuleShortfall<>(UNREAD,
                        aPattern("b{300}"), UnreadReason.PATTERN_TOO_COSTLY)),
                java.util.Set.of());

        assertEquals(one.alsoSeen(other), other.alsoSeen(one),
                "one branch, one aggregate, whichever copy was settled first");
    }

    /** The pattern a rule would have this compiler build, as a plan. */
    private static souther.compiler.regex.PatternPlan aPattern(String regex) {
        return souther.compiler.regex.PatternPlan.of(
                ((souther.compiler.regex.PatternRead.Read)
                        souther.compiler.regex.PatternParser.read(regex)).syntax());
    }

    /** Every question of every rule that nothing answered, and what stopped this reading of it. */
    private static Map<String, List<UnreadReason>> byQuestion(FieldDomains read) {
        Map<String, List<UnreadReason>> out = new LinkedHashMap<>();
        read.accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted unaccounted
                            && unaccounted.why()
                                    instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.put(accounting.cited().rule().citedName() + " at " + owed,
                                says.why());
                    }
                }));
        return out;
    }

    private static FieldDomains read(String source, String name) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(each -> each.diagnostic().code())
                .toList(), "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule at = TypeSymbols.declared(new TypeKey(symbols.module(), name));
        return FieldDomains.of(at,
                RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
