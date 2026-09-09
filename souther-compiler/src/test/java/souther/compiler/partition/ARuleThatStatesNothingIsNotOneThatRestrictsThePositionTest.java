package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.RuleWithoutALine;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Two rules read from end to end that draw no line, and they are not the same news.
 *
 * <p>One of them states nothing about the position: {@code a - a > 0} names it and compares a
 * number it does not appear in, so the values there are exactly what they were. The other holds the
 * position to the strings a format accepts, and everything else is refused at construction — so the
 * value written there has to be one of them, which is a fact a reader acts on.
 *
 * <p>Neither is a position the model divides no way, and their verdicts do not tell them apart:
 * what a verdict says is how far the readings got, and these two got equally far. What differs is
 * what the rule did to the position, and the reason is where that lives — so the difference is held
 * there, which is the only place it can be held at all.
 *
 * <p><b>Which is what stops the over-generalisation either way.</b> A reading that gave every rule
 * without a line the word for a restriction would say the model holds down a position it leaves
 * alone; one that gave a restriction the word for a division would say the model tells apart values
 * the declaration refuses to build.
 */
class ARuleThatStatesNothingIsNotOneThatRestrictsThePositionTest {

    /** A rule about a number the position cancels out of, which states nothing about it. */
    private static final String STATES_NOTHING = """
            module probe

            data Ok

            data N = Int
                invariant nothing = value - value > 0

            behavior read : (n: N) -> Ok
            let read (n) = Ok
            """;

    /** A rule holding the position to the strings a format accepts. */
    private static final String RESTRICTS_THE_POSITION = """
            module probe

            data Ok

            data N = String
                invariant format = String.matches("T[0-9]{3}", value)

            behavior read : (n: N) -> Ok
            let read (n) = Ok
            """;

    /**
     * Neither is a position the model divides no way, which is the claim they were both filed under.
     *
     * <p>The one thing both have to have. Each states a rule about the position it is at, so a
     * verdict saying the model divides it no way denies the declaration two tokens away — which is
     * what a rule nothing records as read to the end comes to.
     */
    @Test
    void neitherIsAPositionTheModelDividesNoWay() {
        for (String source : List.of(STATES_NOTHING, RESTRICTS_THE_POSITION)) {
            List<UndividedPosition> undivided = partitioningOf(source).undivided();
            assertEquals(1, undivided.size(), undivided.toString());
            assertFalse(undivided.get(0).why() instanceof UndividedPosition.Why.Absent, source);
        }
    }

    /** A format this compiler does not read that far into, which is a reading that stopped. */
    private static final String NOT_READ = """
            module probe

            data Ok

            data N = String
                invariant format = String.matches("(a+)\\\\1", value)

            behavior read : (n: N) -> Ok
            let read (n) = Ok
            """;

    /**
     * A pattern this could not take apart is a reading that stopped, and never one that finished.
     *
     * <p>The pair the reason exists for. Both are `String.matches` about one position, and what
     * tells them apart is whether the pattern was read — so a producer deciding from the operation
     * alone would say the rule holds the position down, which claims a set nobody worked out. A
     * reader told the value written there has to be one the rule admits would be acting on a fact
     * this compiler never established, and the rule they could rewrite would go unmentioned.
     */
    @Test
    void aPatternThisCouldNotReadRestrictsNothing() {
        List<UndividedPosition> undivided = partitioningOf(NOT_READ).undivided();

        assertEquals(1, undivided.size(), undivided.toString());
        assertInstanceOf(UndividedPosition.Why.CannotDerive.class, undivided.get(0).why(),
                "the pattern was not read, so nothing about what stands there follows");
        assertFalse(reasonsOf(NOT_READ).contains(
                        UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS),
                "and the word for a rule that holds a position down is not said of it: "
                        + reasonsOf(NOT_READ));
    }

    /** A predicate every string satisfies, which tells no value here from another. */
    private static final String RULES_NOTHING_OUT = """
            module probe

            data Ok

            data N = String
                invariant nothing = String.contains("", value)

            behavior read : (n: N) -> Ok
            let read (n) = Ok
            """;

    /** And one no string satisfies, which leaves no value rather than restricting to some. */
    private static final String LEAVES_NOTHING = """
            module probe

            data Ok

            data N = String
                invariant nothing = Bool.not(String.contains("", value))

            behavior read : (n: N) -> Ok
            let read (n) = Ok
            """;

    /**
     * A predicate that rules nothing out holds the position to nothing, and is not called a
     * restriction.
     *
     * <p>Read and restricts are two questions. {@code String.contains("", value)} is read perfectly
     * — the strings it admits are every string there is — and a reading that answered the first for
     * the second would tell a reader the value written there has to be one the rule admits, which
     * is every value and no help.
     *
     * <p>The pair is the point: the same call, one needle apart, and only one of them holds the
     * position down. A reading that decided from the operation, or from whether the text was
     * written, would give them one answer.
     */
    @Test
    void aPredicateThatRulesNothingOutRestrictsNothing() {
        assertFalse(reasonsOf(RULES_NOTHING_OUT)
                        .contains(UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS),
                () -> "every string satisfies it, so the position is left where it was found: "
                        + reasonsOf(RULES_NOTHING_OUT));
        assertEquals(List.of(UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS),
                reasonsOf(RESTRICTS_THE_POSITION),
                "and the one that does hold it down still says so");
    }

    /** And one no string satisfies leaves no value, which is not a restriction either. */
    @Test
    void aPredicateNoStringSatisfiesRestrictsNothingEither() {
        assertFalse(reasonsOf(LEAVES_NOTHING)
                        .contains(UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS),
                () -> "no string satisfies it, so the rules leave no value rather than holding the"
                        + " position to some: " + reasonsOf(LEAVES_NOTHING));
    }

    /** A rule that states nothing, standing beside one that holds the same position down. */
    private static final String NOTHING_BESIDE_A_NARROWING = """
            module probe

            data Ok

            data T = { value: Int }
                invariant tautology = value == 5 || value /= 5
                invariant narrows = value == 7

            behavior read : (t: T) -> Ok
            let read (t) = Ok
            """;

    /**
     * A rule is answered for by what it did, and never by what the rule beside it did.
     *
     * <p>What the declaration leaves at a position is met from every rule that reached it, so a
     * reading that took it for one rule's answer hands that rule its neighbour's narrowing. Here
     * the first rule states nothing — every value satisfies one side or the other — and the second
     * holds the position to a single value; read off the declaration, the first is reported as
     * holding the position to what it admits, and an author is sent to a rule that admits
     * everything.
     *
     * <p>The fates of its choices are the neighbour's work too, which is what makes the pair
     * necessary rather than the tautology on its own. Nothing rules out {@code value == 5} until
     * {@code value == 7} stands beside it, and a reading that answered over the rule's tree with
     * those fates applied would find one branch dead and the other holding the position away from
     * five — a narrowing assembled out of a rule that states none.
     */
    @Test
    void aRuleIsNotHandedTheNarrowingItsNeighbourDid() {
        assertEquals(List.of(), reasonsOf(NOTHING_BESIDE_A_NARROWING),
                "one rule states nothing and the other draws a line, so neither holds the position"
                        + " to what it admits");
    }

    /**
     * And the format is a reading that finished, which is the half that moved.
     *
     * <p>Said of this one alone. What verdict the other comes to is a fact about which readings stop
     * on an arithmetic that cancels, and holding both to one answer here would be this test claiming
     * something it was not written to be about.
     */
    @Test
    void theFormatIsAReadingThatRanToTheEnd() {
        assertInstanceOf(UndividedPosition.Why.StatedWithoutALine.class,
                partitioningOf(RESTRICTS_THE_POSITION).undivided().get(0).why(),
                "the rule was taken in; what is absent is a line");
    }

    /** And what tells them apart is the reason, which is the only place the difference is. */
    @Test
    void whatTellsThemApartIsTheReason() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_CUTS_NOTHING),
                reasonsOf(STATES_NOTHING),
                "a rule about a number the position cancels out of cuts nothing");
        assertEquals(List.of(UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS),
                reasonsOf(RESTRICTS_THE_POSITION),
                "a format holds the position to the strings it admits, and nothing else can be"
                        + " built there");
    }

    /** The words the rules of one model come to, in the order they are held. */
    private static List<UndividedPosition.Reason> reasonsOf(String source) {
        return partitioningOf(source).rulesWithoutALine().stream()
                .map(RuleWithoutALine::why)
                .map(ReportedReason::of)
                .toList();
    }

    private static Partitions.Partitioning partitioningOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of("read",
                InputDomain.of(sigs.get("read"), rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
