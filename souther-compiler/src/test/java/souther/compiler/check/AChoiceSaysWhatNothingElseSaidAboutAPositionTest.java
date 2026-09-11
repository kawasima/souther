package souther.compiler.check;

import souther.compiler.WhereItSits;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A choice is answerable for a position its unread alternative said nothing else about.
 *
 * <p>What an alternative nothing could read does is leave the positions the other branch promised
 * open. At a position that branch is already answerable for something, that is the same shortfall
 * arriving by another road — a form nothing reads is one thing to lift, and a reader told to lift
 * it and then told about the choice above it would lift the form and find the question still there.
 * At a position the branch never named, the choice is the first thing to say anything.
 *
 * <p><b>Asked of what the branch is answerable for, and never of what the position holds.</b> A
 * place holds the reasons of every rule that reached it, so a rule suppressed by it would be a fact
 * about a rule turning on what a neighbour wrote. The position's own account has a rule of its own
 * that comes to the same answer here, and the two are two rules ({@code UnreadReason.leftOpen}).
 *
 * <p>Which choice, because that is what it is a fact about. Filed at a leaf under the branch that
 * was read, an author would be sent to a clause nothing complained of.
 */
class AChoiceSaysWhatNothingElseSaidAboutAPositionTest {

    /**
     * A branch nothing reads, beside one that promises a position it never names.
     *
     * <p>The unread side is about {@code a}; {@code b} is promised by the other side and appears
     * nowhere in the branch that was not read, so nothing but the choice has anything to say about
     * why {@code b} is open.
     */
    private static final String NOTHING_ELSE_SAID_ABOUT_IT = """
            module demo

            data N = { a: String, b: String }
                invariant r = UNREAD_A || b == "A"
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /** And one where the branch nothing reads is about the position itself. */
    private static final String THE_BRANCH_SAID_IT_ITSELF = """
            module demo

            data N = { a: String }
                invariant r = a == "A" || UNREAD_A
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /** Two choices of one rule, each leaving one position open and neither naming it. */
    private static final String TWO_CHOICES_LEAVE_IT_OPEN = """
            module demo

            data N = { a: String, b: String, c: String }
                invariant r =
                    (UNREAD_A || c == "A") && (UNREAD_B || c == "A")
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"))
            .replace("UNREAD_B", souther.compiler.ARuleNoReadingTakesIn.about("b"));

    /** The same unreadness under one more bracket, which is one connective and not a tree. */
    private static final String THE_SAME_THROUGH_ONE_MORE_BRACKET = """
            module demo

            data N = { a: String, b: String }
                invariant r = b == "A" || (b == "B" || UNREAD_A)
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /** And the same disjunction bracketed the other way, which says what a bracket is not. */
    private static final String THE_SAME_BRACKETED_THE_OTHER_WAY = """
            module demo

            data N = { a: String, b: String }
                invariant r = (b == "A" || b == "B") || UNREAD_A
            """.replace("UNREAD_A", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /**
     * A form nothing reads, written beside the brackets rather than inside them.
     *
     * <p>Both alternatives read perfectly well. What nothing reads is the conjunct written next to
     * the whole choice, and taking it away leaves the rule with no such reason at all.
     */
    private static final String BESIDE_THE_BRACKET = """
            module demo

            data N = { x: String, y: String }
                invariant r = (x == "A" || x == "B") && UNREAD_Y
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** The same form inside one alternative, with a read conjunct beside the brackets. */
    private static final String INSIDE_ONE_ALTERNATIVE = """
            module demo

            data N = { x: String, y: String }
                invariant r = (x == "A" || UNREAD_Y) && y /= "Z"
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** And an alternative nothing reads, beside a conjunct that is the only thing to name `y`. */
    private static final String THE_CONJUNCT_NAMES_A_POSITION_OF_ITS_OWN = """
            module demo

            data N = { x: String, y: String }
                invariant r = (x == "A" || UNREAD_X) && y == "Q"
            """.replace("UNREAD_X", souther.compiler.ARuleNoReadingTakesIn.about("x"));

    /**
     * A form nothing reads inside a branch of an inner choice that nobody can be in.
     *
     * <p>The inner alternative holds two rules nothing satisfies together, so it is a branch there
     * is no reading of; what it could not read goes with it. The outer choice does offer an
     * alternative nothing could read, and what it is answerable for has to come from that and not
     * from a branch the reading has already given up on.
     */
    private static final String THE_UNREAD_FORM_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN = """
            module demo

            data N = { x: String, y: String }
                invariant r =
                    ((x == "A" && x == "B" && UNREAD_Y) || x == "C") || UNREAD_X
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"))
            .replace("UNREAD_X", souther.compiler.ARuleNoReadingTakesIn.about("x"));

    /**
     * A form nothing reads inside a branch a clause written elsewhere shows nobody can be in.
     *
     * <p>Nothing here says so until every rule of the value has been read: the inner alternative
     * holds a rule this reading takes in, and it is a neighbouring rule that leaves nobody able to
     * be in it. Once that is settled the branch takes what it could not read with it, and the
     * choice above has been offered no alternative anything failed to read.
     */
    private static final String A_NEIGHBOUR_SHOWS_THE_INNER_BRANCH_EMPTY = """
            module demo

            data N = { x: String, y: String }
                invariant one =
                    ((x == "A" && UNREAD_Y) || x == "B")
                    || x == "C"
                invariant two = x /= "A"
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** The same without the neighbour, where the inner branch stands. */
    private static final String THE_SAME_WITH_THE_INNER_BRANCH_STANDING = """
            module demo

            data N = { x: String, y: String }
                invariant one =
                    ((x == "A" && UNREAD_Y) || x == "B")
                    || x == "C"
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /** An alternative that constrains the position and holds a form nothing reads beside it. */
    private static final String THE_UNREAD_FORM_IS_UNDER_AN_ALTERNATIVE = """
            module demo

            data N = { x: String, y: String }
                invariant r = x == "A" || (x == "B" && UNREAD_Y)
            """.replace("UNREAD_Y", souther.compiler.ARuleNoReadingTakesIn.about("y"));

    /**
     * Eight clauses about one position that nothing reads, written along one line.
     *
     * <p>Enough of them that an order arrived at by hashing would be some other order.
     */
    private static final String EIGHT_FORMS_NOTHING_READS = """
            module demo

            data N = { a: String }
                invariant r = U && U && U && U && U && U && U && U
            """.replace("U", souther.compiler.ARuleNoReadingTakesIn.about("a"));

    /** Where nothing else said anything about the position, the choice says it. */
    @Test
    void aChoiceIsAnswerableWhereItsUnreadAlternativeSaidNothing() {
        assertEquals(Set.of(UnreadReason.ALTERNATIVE_NOT_READ),
                whatARuleIsAnswerableFor(NOTHING_ELSE_SAID_ABOUT_IT, "b"),
                "the branch that was not read never names this position, so the choice is the"
                        + " first thing with anything to say about why it is open");
    }

    /** And where the branch said it itself, the choice adds nothing. */
    @Test
    void aChoiceAddsNothingWhereItsUnreadAlternativeSaidItAlready() {
        assertEquals(Set.of(UnreadReason.FORM_NOT_READ),
                whatARuleIsAnswerableFor(THE_BRANCH_SAID_IT_ITSELF, "a"),
                "one form nothing reads is one thing to lift, and the choice above it is the same"
                        + " shortfall arriving by another road");
    }

    /** And two choices leaving one position open are two things to look at. */
    @Test
    void twoChoicesLeavingOnePositionOpenAreBothAnswerable() {
        assertEquals(2, sitesFor(TWO_CHOICES_LEAVE_IT_OPEN, "c").size(),
                "each choice offered an alternative of its own, and an author has both to look"
                        + " at — held as reasons at the position they were one");
    }

    /** And one more bracket around the same unreadness is not one more thing to look at. */
    @Test
    void oneMoreBracketIsNotOneMoreThingToLookAt() {
        assertEquals(1, sitesFor(THE_SAME_THROUGH_ONE_MORE_BRACKET, "b").size(),
                "a choice is one connective and not a tree, so a shortfall that passed through a"
                        + " bracket on its way up is not a second shortfall");
    }

    /**
     * And bracketing the same disjunction the other way says the same thing.
     *
     * <p>Which is what a bracket not being a second shortfall comes to: the reasons are of the
     * choices an author wrote, and {@code ||} means the same thing however it was grouped. Two
     * choices are still two — what is held here is that grouping alone makes neither.
     */
    @Test
    void theSameDisjunctionGroupedTheOtherWaySaysTheSame() {
        assertEquals(whatARuleIsAnswerableFor(THE_SAME_THROUGH_ONE_MORE_BRACKET, "b"),
                whatARuleIsAnswerableFor(THE_SAME_BRACKETED_THE_OTHER_WAY, "b"),
                "a reader is told the same thing whichever way the author grouped the choice");
        assertEquals(sitesFor(THE_SAME_THROUGH_ONE_MORE_BRACKET, "b").size(),
                sitesFor(THE_SAME_BRACKETED_THE_OTHER_WAY, "b").size(),
                "and has the same number of things to look at");
    }

    /**
     * And the facts come out in the order they were met, whatever order that is.
     *
     * <p>No order is claimed of them: which written place a reader is sent to first is the source's
     * to say, and a set of facts says nothing about it. What is held here is that the order does not
     * come from somewhere else — a projection out of this set is published, so a copy free to
     * arrange them by hash would have one compiler over one source publish two documents, and which
     * one an author saw would be the run they happened to make.
     *
     * <p>Eight of them because a copy that reorders may leave two or three where they were.
     */
    @Test
    void theFactsComeOutInTheOrderTheyWereMet() {
        List<Integer> columns = new java.util.ArrayList<>();
        shortfallsAt(EIGHT_FORMS_NOTHING_READS, "a").forEach(each -> {
            if (each.site() instanceof RuleShortfall.Site.AtALeaf leaf) {
                columns.add(WhereItSits.in(EIGHT_FORMS_NOTHING_READS, leaf.writtenAt()).column());
            }
        });

        assertEquals(8, columns.size(), "one for each clause nothing reads");
        assertEquals(columns.stream().sorted().toList(), columns,
                "they were met along the line, and nothing between there and here rearranged them");
    }

    /**
     * A form nothing reads written beside the brackets leaves the choice answerable for nothing.
     *
     * <p>A conjunction of a choice is also the choice between the conjunctions, and the values are
     * worked out that way — so read over that tree, both alternatives hold a form nothing reads and
     * the choice comes out offering an alternative nothing could read. Neither alternative holds
     * one. An author following it is sent to a choice that reads perfectly well, to lift something
     * written somewhere else.
     */
    @Test
    void aChoiceIsAnswerableForNothingWhereTheUnreadFormIsBesideIt() {
        assertEquals(Set.of(), whatARuleIsAnswerableFor(BESIDE_THE_BRACKET, "x"),
                "both alternatives were read, so the choice offered no alternative nothing could"
                        + " read — whatever was written beside the brackets");
        assertEquals(Set.of(UnreadReason.FORM_NOT_READ),
                whatARuleIsAnswerableFor(BESIDE_THE_BRACKET, "y"),
                "and what the conjunct beside them left is the conjunct's own, at the leaf it was"
                        + " written as");
    }

    /**
     * And the same form written inside one alternative leaves it answerable.
     *
     * <p>The control that makes the one above a claim rather than a spelling: the two models differ
     * in where the form stands and in nothing else.
     */
    @Test
    void aChoiceIsAnswerableWhereTheUnreadFormIsInsideAnAlternative() {
        assertEquals(Set.of(UnreadReason.ALTERNATIVE_NOT_READ),
                whatARuleIsAnswerableFor(INSIDE_ONE_ALTERNATIVE, "x"),
                "the alternative beside the one naming `x` is one nothing could read, so what that"
                        + " branch said of `x` binds nothing");
    }

    /**
     * And a position only the conjunct beside the brackets names is not the choice's to answer for.
     *
     * <p>The other way the same rewriting is read as an author's work: distributed, the conjunct is
     * part of each alternative, and the positions it reached are read as positions the alternative
     * left open.
     */
    @Test
    void aChoiceIsAnswerableForNothingAtAPositionItsAlternativesNeverReach() {
        assertEquals(Set.of(),
                whatARuleIsAnswerableFor(THE_CONJUNCT_NAMES_A_POSITION_OF_ITS_OWN, "y"),
                "neither alternative names `y`, so nothing about it turns on which of them"
                        + " anybody is in");
        assertEquals(Set.of(UnreadReason.FORM_NOT_READ),
                whatARuleIsAnswerableFor(THE_CONJUNCT_NAMES_A_POSITION_OF_ITS_OWN, "x"),
                "while `x` is left by the form nothing reads, at the leaf it was written as");
    }

    /**
     * And an alternative that constrains the position is still one nothing could read.
     *
     * <p>Held so that the three above are not answered by making a rule's account agree with what
     * the position finally admits. Here the position comes out held to two strings and the choice is
     * answerable all the same: had the branch been read, it might have turned out one nobody can be
     * in, and then the position would be held to one. What a rule is answerable for and how wide a
     * position ended up are two questions.
     */
    @Test
    void anAlternativeThatConstrainsThePositionIsStillOneNothingCouldRead() {
        assertEquals(Set.of(UnreadReason.ALTERNATIVE_NOT_READ),
                whatARuleIsAnswerableFor(THE_UNREAD_FORM_IS_UNDER_AN_ALTERNATIVE, "x"),
                "the form nothing reads stands under one alternative, which is what makes that"
                        + " alternative one nothing could read");
    }

    /**
     * And a branch nobody can be in leaves the choice above it nothing to be answerable for.
     *
     * <p>What such a branch could not read is not a rule of this declaration that went unread —
     * there is no branch for an author to go and look at — so it is no part of what the choice
     * above it offered either. Decided before the branches are, the alternative holding the dead
     * one comes out as one nothing could read, and the choice above says so at a position that
     * branch is the only thing to have reached.
     */
    @Test
    void aBranchNobodyCanBeInLeavesTheChoiceAboveItNothingToAnswerFor() {
        assertEquals(Set.of(UnreadReason.FORM_NOT_READ),
                whatARuleIsAnswerableFor(THE_UNREAD_FORM_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN, "x"),
                "the alternative nothing could read is the form written beside the brackets, and"
                        + " an author lifting it has everything there is to do here");
        assertEquals(Set.of(),
                whatARuleIsAnswerableFor(THE_UNREAD_FORM_IS_IN_A_BRANCH_NOBODY_CAN_BE_IN, "y"),
                "and the form inside the branch nobody can be in went with it");
    }

    /**
     * And a branch a neighbouring rule shows empty leaves the choice above it nothing either.
     *
     * <p>Which branches anybody can be in is the whole declaration's answer, so what an alternative
     * nothing could read left open is not knowable until that is. Asked while the clauses are being
     * read, the alternative holding the branch comes out as one nothing could read — the reading of
     * it has given something up — and the choice above says so at a position that branch was the
     * only thing to reach.
     */
    @Test
    void aBranchANeighbourShowsEmptyLeavesTheChoiceAboveItNothingToAnswerFor() {
        assertEquals(Set.of(), whatARuleIsAnswerableFor(A_NEIGHBOUR_SHOWS_THE_INNER_BRANCH_EMPTY,
                        "x"),
                "nobody can be in the branch the form nothing reads is in, so the alternative"
                        + " holding it is one that was read whole");
        assertEquals(Set.of(), whatARuleIsAnswerableFor(A_NEIGHBOUR_SHOWS_THE_INNER_BRANCH_EMPTY,
                        "y"),
                "and the form itself went with the branch");
    }

    /**
     * And the same clauses without that neighbour keep it, which is what makes the one above a
     * claim.
     *
     * <p>Nothing shows the inner branch empty here, so the alternative holding it is one nothing
     * could read and the choice above is answerable at the position the other alternative
     * promised. A reading that dropped what a nested choice left open would answer both models
     * alike.
     */
    @Test
    void andTheSameClausesWithoutThatNeighbourKeepIt() {
        assertEquals(Set.of(UnreadReason.ALTERNATIVE_NOT_READ),
                whatARuleIsAnswerableFor(THE_SAME_WITH_THE_INNER_BRANCH_STANDING, "x"),
                "the branch stands, so the alternative holding it is one nothing could read");
    }

    /** What a rule of the declaration is answerable for at {@code field}. */
    private static Set<UnreadReason> whatARuleIsAnswerableFor(String source, String field) {
        Set<UnreadReason> out = new LinkedHashSet<>();
        shortfallsAt(source, field).forEach(each -> out.add(each.why()));
        return out;
    }

    /** The written places it is answerable at, which is what tells two of them apart. */
    private static Set<RuleShortfall.Site> sitesFor(String source, String field) {
        Set<RuleShortfall.Site> out = new LinkedHashSet<>();
        shortfallsAt(source, field).forEach(each -> out.add(each.site()));
        return out;
    }

    /**
     * What every question about {@code field} is answerable for, over every rule of the value.
     *
     * <p>Keyed by the question and not by the position inside a shortfall: a question is of one rule
     * at one position, and what is filed under it is that position's. Read the other way, this test
     * would be reading a place's reasons to find a rule's, which is the thing being closed.
     */
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

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
