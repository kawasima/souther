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
                borderIn("(s < \"\" && Int.abs(n) >= 5) || n >= 2"),
                "nothing satisfies the branch the unread form is written in, so no value of this"
                        + " type stands anywhere on its account");
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

    private static List<String> linesOf(String clause,
                                        java.util.function.Predicate<String> which) {
        Compilation compilation = Compilation.ofSource(model(clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity()).lines()
                .map(String::strip)
                .filter(which)
                .toList();
    }
}
