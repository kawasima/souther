package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A branch nobody can be in reaches no question that stands, whatever it was holding.
 *
 * <p>What such a branch leaves is that the positions it named are settled, and nothing else: no
 * rule of it is one this declaration went short on, and no choice inside it left a position wider
 * than the rules hold it. There is no branch for an author to look at, so there is nothing for
 * either to send them to.
 *
 * <p>Which is a claim about a whole declaration and needs a question that stands to be told to.
 * Held over a branch nothing else names, both would be true of a reading that kept everything: the
 * positions the dead branch spoke of are settled, so no question about them is left for what it
 * kept to reach. So the branch that stands here names the position too, and the position hears
 * about a rule of the declaration or about how wide it is — and what the dead branch was holding
 * would arrive at that question if it were kept.
 *
 * <p>Each of them beside the same model with the branch standing, which is where the reading does
 * say both things. What is under test is that a fate decides it, and a pair of models one fate
 * apart is what says so.
 */
class NothingAWholeDeclarationLeftInADeadBranchReachesAQuestionThatStandsTest {

    /**
     * A form nothing reads inside a branch nobody can be in, beside one that names the position.
     *
     * <p>The standing alternative is about {@code y}, so a question about {@code y} stands and is
     * answered by the rule. The branch nobody can be in holds a form nothing reads about the same
     * position, and what that form is short of is not this declaration's shortfall.
     */
    private static final String THE_FORM_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && x == "B" && UNREAD_Y) || (x == "C" && y == "Q")
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** The same clauses with the branch standing, where the form is one nothing read. */
    private static final String THE_SAME_WITH_THE_BRANCH_STANDING = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && UNREAD_Y) || (x == "C" && y == "Q")
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /**
     * Two forms nothing reads about one position, one of them in a branch nobody can be in.
     *
     * <p>The question about {@code y} stands and nothing answers it, so what the declaration is
     * short of at {@code y} is reported — and there is one place to go and look at, not two. The
     * pair is what tells a shortfall that was dropped from one that was never made: counted alone,
     * a reading holding the dead branch's would agree with a reading holding nothing.
     */
    private static final String ONE_OF_TWO_UNREAD_FORMS_IS_IN_A_DEAD_BRANCH = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && x == "B" && UNREAD_Y) || (x == "C" && UNREAD_Y)
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** The same two forms with both branches standing, where both are places to look at. */
    private static final String BOTH_UNREAD_FORMS_ARE_IN_BRANCHES_THAT_STAND = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && UNREAD_Y) || (x == "C" && UNREAD_Y)
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /**
     * The first of those with the dead alternative written second instead.
     *
     * <p>Which alternative nobody can be in is the author's, and what a choice with one of them
     * comes to is the branch beside it either way round. Written one way only, the claim would hold
     * of a reading that keeps a dead second alternative and drops a dead first.
     */
    private static final String THE_FORM_IS_IN_A_DEAD_SECOND_ALTERNATIVE = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "C" && y == "Q") || (x == "A" && x == "B" && UNREAD_Y)
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** And the second of them the same way round. */
    private static final String ONE_OF_TWO_UNREAD_FORMS_IS_IN_A_DEAD_SECOND_ALTERNATIVE = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "C" && UNREAD_Y) || (x == "A" && x == "B" && UNREAD_Y)
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /**
     * One pattern no machine can be made for, written in a dead branch and beside it.
     *
     * <p>A machine is made once for a pattern at a position however many clauses wrote it, so the
     * refusal is one fact and the clauses that asked for it are where an author is sent. Both
     * clauses here ask for that machine at that position, and one of them is written where nobody
     * can be.
     */
    private static final String ONE_REFUSED_MACHINE_IS_ASKED_FOR_FROM_A_DEAD_BRANCH = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && x == "B" && String.matches("a{60000}", y))
                    || String.matches("a{60000}", y)
            """;

    /** The same pattern in two alternatives that both stand, which is two places to go to. */
    private static final String THE_SAME_MACHINE_IS_ASKED_FOR_FROM_TWO_BRANCHES_THAT_STAND = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    (x == "A" && String.matches("a{60000}", y))
                    || String.matches("a{60000}", y)
            """;

    /**
     * A choice inside a branch nobody can be in, one alternative of which nothing reads.
     *
     * <p>Inside the branch the choice does leave {@code b} wider than the rules hold it: the
     * alternative beside the unread one holds it to one string, and the unread one never names it,
     * so the choice is the only thing with anything to say about why it is open. The branch is one
     * nobody can be in, so the declaration is under nothing of the kind.
     */
    private static final String THE_CHOICE_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN = """
            module demo

            data N = { x: String, a: String, b: String }
                invariant r =
                    (x == "A" && x == "B" && (UNREAD_A || b == "P")) || x == "C"
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /** The same clauses with that branch standing, where the position is left open. */
    private static final String THE_SAME_CHOICE_IN_A_BRANCH_THAT_STANDS = """
            module demo

            data N = { x: String, a: String, b: String }
                invariant r =
                    (x == "A" && (UNREAD_A || b == "P")) || x == "C"
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /**
     * What a rule of a dead branch was short of is no shortfall of the declaration.
     *
     * <p>The question about {@code y} stands and is the standing alternative's to answer. A reading
     * that kept what the dead branch was short of would answer it with a form written where nobody
     * can be, and an author lifting it would find the question unchanged.
     */
    @Test
    void whatARuleOfADeadBranchWasShortOfReachesNoQuestion() {
        assertEquals(Set.of(),
                whatARuleIsAnswerableFor(THE_FORM_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN, "y"),
                "nothing satisfies the branch the form is written in, so there is no clause for an"
                        + " author to go and look at");
        assertEquals(Set.of(UnreadReason.FORM_NOT_READ),
                whatARuleIsAnswerableFor(THE_SAME_WITH_THE_BRANCH_STANDING, "y"),
                "and the same form in a branch somebody can be in is one this declaration went"
                        + " short on, which is what makes the answer above a fate's doing");
    }

    /**
     * And it is not a second place to go and look at either.
     *
     * <p>Read as a set of reasons, a shortfall of a dead branch and one of the branch beside it are
     * the same reason twice and one of them hides the other. What tells them apart is the written
     * place each sends an author to, and that is what is counted here.
     */
    @Test
    void norIsItASecondPlaceForAnAuthorToLookAt() {
        assertEquals(1, placesToLookAt(ONE_OF_TWO_UNREAD_FORMS_IS_IN_A_DEAD_BRANCH, "y"),
                "one of the two forms is written where nobody can be, so there is one clause to"
                        + " lift and not two");
        assertEquals(2, placesToLookAt(BOTH_UNREAD_FORMS_ARE_IN_BRANCHES_THAT_STAND, "y"),
                "and with both branches standing both forms are places to go to, which is what"
                        + " makes the count above a fate's doing");
    }

    /**
     * And the same whichever of the two alternatives is the one nobody can be in.
     *
     * <p>What is above is written with the dead branch first, and every claim of it is about a
     * choice and not about a side. Held one way round only, a reading that treated a choice as a
     * choice wherever the second alternative was the dead one would answer the same on all of it —
     * the branch beside a dead first is composed with nothing either way, and the two ways are told
     * apart by nothing else here.
     */
    @Test
    void andTheSameWhicheverAlternativeNobodyCanBeIn() {
        assertEquals(Set.of(),
                whatARuleIsAnswerableFor(THE_FORM_IS_IN_A_DEAD_SECOND_ALTERNATIVE, "y"),
                "nothing satisfies the second alternative, so the form written in it is no clause"
                        + " for an author to go and look at");
        assertEquals(1,
                placesToLookAt(ONE_OF_TWO_UNREAD_FORMS_IS_IN_A_DEAD_SECOND_ALTERNATIVE, "y"),
                "and it is not a second place either, which is the same claim the other way round");
    }

    /**
     * And a refused machine is answered for by the clauses somebody may be in.
     *
     * <p>A refusal is matched to the clauses that asked for it by the pattern and the position, and
     * by nothing that says which branch either of them is in. So the request a dead branch made is
     * not a request this declaration is answerable for: it recovers no refusal of that branch,
     * where nothing was built to be refused, and it offers the refusal of the branch that stands a
     * second written place to be about.
     */
    @Test
    void andARefusedMachineIsAnsweredForByTheClauseThatStands() {
        assertEquals(1, placesToLookAt(ONE_REFUSED_MACHINE_IS_ASKED_FOR_FROM_A_DEAD_BRANCH, "y"),
                "the pattern is written twice and one of the two is where nobody can be, so there"
                        + " is one clause an author can do something about");
        assertEquals(2,
                placesToLookAt(THE_SAME_MACHINE_IS_ASKED_FOR_FROM_TWO_BRANCHES_THAT_STAND, "y"),
                "and where both branches stand both clauses asked for the machine that was"
                        + " refused, which is what makes the count above a fate's doing");
    }

    /**
     * And what a choice inside one left open leaves the position hearing nothing.
     *
     * <p>The other half, because the two travel by different roads: a shortfall is filed at the
     * clause that was short and a widening is told to the position. A reading keeping either would
     * report a declaration as answering less than it does, on the strength of a branch nobody can
     * be in.
     */
    @Test
    void andWhatAChoiceInsideOneLeftOpenReachesNoPosition() {
        assertEquals(List.of(),
                whyOpen(THE_CHOICE_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN, "b"),
                "the choice that left it open is inside a branch nobody can be in, so the rules"
                        + " leave the position exactly where the answer says");
        assertEquals(List.of(UnreadReason.ALTERNATIVE_NOT_READ),
                whyOpen(THE_SAME_CHOICE_IN_A_BRANCH_THAT_STANDS, "b"),
                "and the same choice in a branch somebody can be in does leave it open");
    }

    /** What a rule of the declaration is answerable for at {@code field}. */
    private static Set<UnreadReason> whatARuleIsAnswerableFor(String source, String field) {
        Set<UnreadReason> out = new LinkedHashSet<>();
        shortfallsAt(source, field).forEach(each -> out.add(each.why()));
        return out;
    }

    /** How many written places an author is sent to about {@code field}. */
    private static int placesToLookAt(String source, String field) {
        Set<RuleShortfall.Site> out = new LinkedHashSet<>();
        shortfallsAt(source, field).forEach(each -> out.add(each.site()));
        return out.size();
    }

    /** What every question about {@code field} is answerable for, over every rule of the value. */
    private static Set<RuleShortfall> shortfallsAt(String source, String field) {
        Set<RuleShortfall> out = new LinkedHashSet<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (owed.toString().equals(field)
                            && outcome instanceof RuleAccounting.Outcome.Unaccounted it
                            && it.why() instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.addAll(says.shortfalls());
                    }
                }));
        return out;
    }

    /** What the position is told left it open, empty where it is told nothing. */
    private static List<UnreadReason> whyOpen(String source, String field) {
        return switch (read(source).admits(RuleKey.of(field)).completeness()) {
            case AdmissibleSet.Completeness.Complete _ -> List.of();
            case AdmissibleSet.Completeness.Wider it -> it.why().stream()
                    .filter(AdmissibleSet.Widening.RuleUnread.class::isInstance)
                    .map(each -> ((AdmissibleSet.Widening.RuleUnread) each).why())
                    .toList();
        };
    }

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                ReadAs.THE_COMPILATION_DOES);
    }
}
