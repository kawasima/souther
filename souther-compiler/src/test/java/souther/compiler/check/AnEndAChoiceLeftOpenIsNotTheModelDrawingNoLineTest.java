package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A choice offering an alternative this compiler does not read leaves the end at a position
 * undecided, and a document says so rather than saying the rules draw no line.
 *
 * <p>The two come back with no line and they are not the same sentence. Where the alternatives were
 * read, what they leave together is what the choice leaves, and a position they hold nowhere is one
 * the model draws no line at — an answer, and one a reader is owed nothing further about. Where one
 * of them is a form this compiler does not enter, a value satisfying it owes the branch beside it
 * nothing: the end is as far out as that branch allows, which is unknown. Said as the first, a limit
 * of this compiler went out as a fact about somebody's model, and an author was told to stop looking
 * for a line their own clause may well draw.
 *
 * <p><b>Composed by the reading and never worked out again from the shape.</b> Which positions a
 * choice is as wide as it is at because of an alternative is settled where the branches are
 * ({@link Settlement.WidthDependency}) and arrives here decided; what this adds is which of them the
 * reading of ends did not work out. A fold over the written clause would have to decide both again,
 * and the second is not a question a shape can answer — a branch nobody can be in constrains
 * nothing, and read off the shape it looks like a branch that constrains nothing.
 */
class AnEndAChoiceLeftOpenIsNotTheModelDrawingNoLineTest {

    private static final String YES_OR_NO = """
            data Yes
            data No
            data Answer = Yes | No
            """;

    private static String model(String clause) {
        return """
                module demo
                %s
                data N = { n: Int, s: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(YES_OR_NO, clause);
    }

    /** The alternative is about {@code n}, in a form the reading of ends does not enter. */
    @Test
    void anEndRestingOnAnUnreadAlternativeIsNotMeasured() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn("n >= 2 || Int.abs(n) >= 5"),
                "what the alternative holds `n` to is unknown, so whether the choice bounds it is"
                        + " what reading further would answer");
    }

    /**
     * And an author is told the choice is why, which is what they can act on.
     *
     * <p>Not that the rule at this position is one nothing reads. It was read and it places its
     * end; an author sent after its form would rewrite a bound that is not the difficulty.
     */
    @Test
    void andTheDocumentSendsAnAuthorToTheChoice() {
        assertEquals(List.of("· not read: invariant N (r) — left open by a choice in it whose"
                        + " other alternative this compiler does not read, about `v.n`"),
                linesOf("n >= 2 || Int.abs(n) >= 5",
                        each -> each.startsWith("· not read:")),
                "the position comes back with no line, and this is what says which clause is why");
    }

    /**
     * And two choices of one rule leaving one end open are told once, as the values' two are.
     *
     * <p>They are two things an author has to do and this is one sentence about both, which is what
     * a document can say today: a rule an author named is found by that name, so there is nowhere
     * in what is said about it to put the operator each of them was written at. Split without one,
     * the two lines are the same sentence twice — which tells a reader less than one line does.
     *
     * <p>Pinned here rather than left to be noticed, because the count is not lost on the way: what
     * the position was left with says how many there were, and the reading holds which. What is
     * missing is a way to name a place inside a named rule, and this is where a document saying two
     * would show up.
     */
    @Test
    void andTwoChoicesOfOneRuleAreToldOnce() {
        assertEquals(List.of("· not read: invariant N (r) — left open by a choice in it whose"
                        + " other alternative this compiler does not read, about `v.n`"),
                linesOf("(n >= 2 || Int.abs(n) >= 5) && (n >= 7 || Int.abs(n) >= 9)",
                        each -> each.startsWith("· not read:")),
                "one rule, one position, one sentence about what became of it there");
    }

    /**
     * And an alternative about another position leaves the line drawn nowhere, which is an answer.
     *
     * <p>The reading of ends could not follow the branch and still knows what it is about, so it
     * knows the choice holds {@code n} nowhere: a value taking that branch is under no obligation
     * about {@code n} from either side. Read off whether a branch was followed at all, this would
     * come back undecided and the test above would be asserting that a check nothing can fail is
     * passing.
     */
    @Test
    void andAnAlternativeAboutAnotherPositionDrawsNoLineAndSaysSo() {
        assertEquals(theModelDrawsNoLine(),
                borderIn("n >= 2 || String.reverse(s) /= \"\""),
                "the alternative names another position and can hold `n` nowhere, so the choice"
                        + " draws no line there and that is the model rather than a reading that"
                        + " stopped");
    }

    /** And two alternatives it followed leave it drawn nowhere for the same reason. */
    @Test
    void andTwoAlternativesItFollowedDoTheSame() {
        assertEquals(theModelDrawsNoLine(), borderIn("n >= 2 || n <= 0"),
                "both were read, and between them they hold `n` nowhere");
    }

    /**
     * And an alternative beside one that places no end decides it, however little of it was read.
     *
     * <p>A disequality is a rule the ends follow to the end and place nothing from, so a value
     * taking that branch stands anywhere on the order — and the choice does too, whatever the
     * branch beside it says. What tells this from the first case is the reading's own answer for
     * having followed the rule, and not what it produced: both branches here produce no end.
     */
    @Test
    void andAnAlternativeBesideOneThatPlacesNoEndDecidesIt() {
        assertEquals(theModelDrawsNoLine(), borderIn("Int.abs(n) >= 5 || n /= 5"),
                "nothing about `n` rests on the unread branch: the branch beside it holds the"
                        + " order nowhere, so the choice does not either");
    }

    /**
     * And a rule it followed to the end brings nothing to the choice, whatever it placed.
     *
     * <p>What a leaf leaves here is the reading's own answer for having followed the rule, and not
     * what came out of it: a disequality is read to the end and places no end, and the conjunct
     * beside it places the one the branch has. Read off what was produced, that disequality is a
     * rule nobody read, the branch it is in is an alternative nothing could follow, and the choice
     * comes back undecided at a position both of its branches were read at.
     */
    @Test
    void andARuleItFollowedBringsNothingToTheChoice() {
        assertEquals(theModelDrawsNoLine(),
                borderIn("(n /= 5 && String.reverse(s) /= \"\") || n <= 0"),
                "the branch holds a form nothing follows, and what it leaves `n` is the"
                        + " disequality's — which was followed, so nothing rests on the form");
    }

    /**
     * And a choice inside an alternative answers for itself before the one above reads it.
     *
     * <p>The inner choice offers a branch that holds {@code n} nowhere, so what it leaves there is
     * every value however little of the branch beside it was read — and the choice above is between
     * a bound and that. Answered by asking which leaves under the whole rule name {@code n}, the
     * unread branch inside would still be one of them and the line would come back undecided.
     */
    @Test
    void andAChoiceInsideAnAlternativeIsAnsweredWhereItIs() {
        assertEquals(theModelDrawsNoLine(),
                borderIn("n >= 2 || (Int.abs(n) >= 5 || s == \"x\")"),
                "the inner choice holds `n` nowhere and the outer one is between that and a bound");
    }

    /**
     * And an alternative holding one position to another leaves no end here to be waiting on.
     *
     * <p>The line such a rule draws runs between the two positions rather than at either, and this
     * compiler draws it: written alone, {@code n < m} is a border. So the choice is between a form
     * nothing follows and a rule that was followed, and what it leaves at {@code n} is what the
     * followed one leaves — nothing.
     *
     * <p>Asked of whether the reading of ends had a range for the alternative, it had none, and the
     * end came back unknown at a position the model states a line about.
     */
    @Test
    void andAnAlternativeHoldingOnePositionToAnotherLeavesNoEndWaiting() {
        assertEquals(theModelDrawsNoLine(),
                borderOf("""
                        module demo
                        %s
                        data N = { n: Int, m: Int }
                            invariant r = Int.abs(n) >= 5 || n < m

                        behavior check : (v: N) -> Answer
                        let check (v) = Yes
                        """.formatted(YES_OR_NO)),
                "the branch beside the unfollowed one was followed, and holds `n` to `m` rather"
                        + " than stopping it anywhere");
    }

    /**
     * And one whose subject this reading cannot name leaves it open, which is weaker than the rules
     * are.
     *
     * <p>{@code n - n >= 0} holds every value, and written alone this compiler says so — the
     * reading that classifies a comparison reads it to the end and finds it cuts nothing. That
     * reading does not go into a choice, and the reading of ends cannot see that the arithmetic
     * cancels: what it has is a subject it cannot name, which is what an absolute value is as well.
     *
     * <p>So this is the conservative answer and not the exact one, and it is written down rather
     * than left to be found: what would close it is the classification of a comparison being
     * asked under a choice.
     */
    @Test
    void andOneWhoseSubjectItCannotNameIsLeftOpen() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn("Int.abs(n) >= 5 || n - n >= 0"),
                "neither alternative is one the reading of ends can name a position in, so what"
                        + " the choice leaves `n` is what following them would answer");
    }

    /**
     * And a choice above one that gave a constraint back does not collect it again.
     *
     * <p>The inner choice puts every value of {@code n} on the order: one of its alternatives says
     * nothing about {@code n} at all, so what the branch holds there is what it held before the
     * rule was written. The choice above is between that and a form nothing follows, and it stops
     * where it would without either.
     *
     * <p>Read off what some part of the branch put there, the constraint the inner choice already
     * took back comes round again a bracket further out — and an end this reading settled is
     * reported as one it did not.
     */
    @Test
    void andAChoiceAboveOneThatGaveAConstraintBackDoesNotCollectIt() {
        assertEquals(theModelDrawsNoLine(),
                borderIn("Int.abs(n) >= 5 || (n >= 2 || String.reverse(s) /= \"\")"),
                "the branch beside the unfollowed one holds `n` nowhere, so the choice does not"
                        + " either");
    }

    /**
     * And an end no alternative bounded is left open, not settled.
     *
     * <p>Neither branch is one this reading follows, so neither of them bounded the position and
     * what the choice leaves there is unknown — which is what the rule is: it does bound {@code n},
     * at two. Asked of what the choice was settled to leave open, this position is outside the
     * question: that answer is worked out over the positions the branches bounded, and none of them
     * bounded this one. An absence there is nothing asked, and read as a proof it published a
     * bound this compiler could not follow as a model that draws no line.
     */
    @Test
    void andAnEndNoAlternativeBoundedIsLeftOpen() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn("n >= 1 + 1 || n >= 1 + 3"),
                "both alternatives are forms this compiler does not follow, so where the values"
                        + " stop is what following them would answer");
    }

    /**
     * And a choice above one that answered does not take the answer back.
     *
     * <p>Each choice is asked about what its own alternatives leave, and an outer one whose
     * alternatives bound nothing has nothing to show about an end an inner one left open. Read as
     * an answer about that end, an alternative nobody followed silences a choice beside it — and
     * which of them is written outermost is not a fact about the rule.
     */
    @Test
    void andAChoiceAboveOneThatAnsweredDoesNotTakeItBack() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn("(n >= 1 + 1 || n >= 2) || n >= 1 + 3"),
                "the inner choice leaves the end at `n` open and the outer one shows nothing about"
                        + " it");
    }

    /**
     * And an end left open beside an alternative nobody can be in is left open.
     *
     * <p>The choice is not a choice any more: what is left of the rule is the branch that stands,
     * and its end is one this reading did not work out. What still happened is that the walk which
     * raises a rule's questions stopped at the {@code ||} the author wrote, so nothing else at this
     * position says the line was not derived — and the border said the model draws none.
     */
    @Test
    void andAnEndBesideAnAlternativeNobodyCanBeInIsLeftOpen() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn("s < \"\" || Int.abs(n) >= 2"),
                "the rule is its right half, and where the values stop under it was not worked"
                        + " out");
    }

    /**
     * And an author is sent nowhere for it, which is what there is to say.
     *
     * <p>The clause a reader would be sent to is the alternative beside it, and there is no such
     * alternative: nobody can be in it. Told the sentence about a choice, an author goes looking
     * for a branch their own rule does not have.
     */
    @Test
    void andNoChoiceIsNamedWhereNobodyCanBeInTheAlternative() {
        assertEquals(List.of(),
                linesOf("s < \"\" || Int.abs(n) >= 2",
                        each -> each.contains("left open by a choice")),
                "there is no branch for an author to look at, so nothing says there is");
    }

    /**
     * And a branch nobody can be in leaves nothing open, whatever is written inside it.
     *
     * <p>No value of this type is in it, so no end of this type rests on what it says. Which is not
     * a rule this composition has of its own: a dead alternative is not composed as one
     * ({@link Adoption#inADeadBranch}), so what a reader here is holding is a conjunction, and a
     * conjunction has no alternative for anything to have gone unread in.
     */
    @Test
    void andABranchNobodyCanBeInLeavesNothingOpen() {
        assertEquals(theModelDrawsNoLine(),
                borderIn("((s < \"\" && Int.abs(n) >= 5) || n >= 2)"
                        + " || String.reverse(s) /= \"\""),
                "nothing satisfies the branch the unread form is written in, so the end at `n` is"
                        + " the one the branch beside it places, and the alternative that names no"
                        + " `n` leaves it where it found it");
    }

    /** And a rule with no choice in it is measured as it was. */
    @Test
    void andARuleWithNoChoiceInItIsMeasuredAsItWas() {
        assertEquals(List.of("border      borders 1   obligations 0/0"),
                borderIn("n >= 2"),
                "one line, and nothing here is owed about it");
    }

    private static List<String> theModelDrawsNoLine() {
        return List.of("border      not applicable (the rules of this behavior draw no line)");
    }

    /** What the document says about this behavior's border. */
    private static List<String> borderIn(String clause) {
        return linesOf(clause, each -> each.startsWith("border"));
    }

    /** The same of a model of its own, for a rule about two positions. */
    private static List<String> borderOf(String source) {
        return linesOfSource(source, each -> each.startsWith("border"));
    }

    private static List<String> linesOf(String clause,
                                        java.util.function.Predicate<String> which) {
        return linesOfSource(model(clause), which);
    }

    private static List<String> linesOfSource(String source,
                                              java.util.function.Predicate<String> which) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip)
                .filter(which)
                .toList();
    }
}
