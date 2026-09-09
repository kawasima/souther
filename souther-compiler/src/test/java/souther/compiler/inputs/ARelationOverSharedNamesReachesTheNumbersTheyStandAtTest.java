package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule relating two names a sum's cases share reaches the numbers those names stand at.
 *
 * <p>Beside {@link ABoundOnASharedNameReachesTheNumbersItStandsAtTest}, which measures an end. An
 * end is a per-name answer and reaches the case through the reading of the position; a relation is
 * not a per-name answer, and what carries it is the rules of the value above said again in the
 * words of the case.
 *
 * <p><b>Once per context and not once per narrowing crossed.</b> The last measurement here relates
 * two names under two sums that are narrowed independently. A renaming worked out edge by edge
 * leaves one of the two names spelled as the sum wrote it in either copy, and the two copies relate
 * nothing when they are met.
 */
class ARelationOverSharedNamesReachesTheNumbersTheyStandAtTest {

    private static final String ONE_SUM = """
            module g

            data Shared = { lo: Int, hi: Int }
            data A = { ...Shared, x: Int }
            data B = { ...Shared, y: Int }
            data Q = A | B

            data Holder = { q: Q }
                invariant ordered = q.lo <= q.hi

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same model with the rule taken out, so that what the rule does is what is measured. */
    private static final String UNRULED =
            ONE_SUM.replace("    invariant ordered = q.lo <= q.hi\n", "");

    private static final String TWO_SUMS = """
            module g

            data Shared = { lo: Int, hi: Int }
            data A = { ...Shared, x: Int }
            data B = { ...Shared, y: Int }
            data Q = A | B

            data Holder = { left: Q, right: Q }
                invariant across = left.lo <= right.hi

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    private static final String TWO_SUMS_UNRULED =
            TWO_SUMS.replace("    invariant across = left.lo <= right.hi\n", "");

    private static final String ONE_SUM_OF_TWO = """
            module g

            data Shared = { lo: Int, hi: Int }
            data A = { ...Shared, x: Int }
            data B = { ...Shared, y: Int }
            data Q = A | B

            data Holder = { p: Q, r: Q }
                invariant ordered = p.lo <= p.hi

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    private static final String TWO_DEEP = """
            module g

            data Deep = { lo: Int, hi: Int }
            data DA = { ...Deep, x: Int }
            data DB = { ...Deep, y: Int }
            data D = DA | DB

            data Mid = { d: D }
            data MA = { ...Mid, p: Int }
            data MB = { ...Mid, q: Int }
            data M = MA | MB

            data Holder = { m: M }
                invariant ordered = m.d.lo <= m.d.hi

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    private static final String TWO_DEEP_UNRULED =
            TWO_DEEP.replace("    invariant ordered = m.d.lo <= m.d.hi\n", "");

    /** The rule the record wrote about the sum, read at the numbers one case stands at. */
    @Test
    void aRelationReachesTheCaseTheNumbersStandUnder() {
        for (String at : List.of("A", "B")) {
            NumericDomain.Bounds runs = stoppedAt(ONE_SUM,
                    "h.q@" + at + ".lo", "h.q@" + at + ".hi", 7);

            assertNotNull(runs, "h.q@" + at + ".lo is a number this reading answers about");
            assertNotNull(runs.max(), () -> "h.q@" + at + ".lo stops where h.q@" + at
                    + ".hi was fixed, and runs " + runs);
            assertEquals("7", number(runs.max()),
                    "h.q@" + at + ".lo stops where the rule the record wrote put it");
        }
    }

    /** And nothing else stops it, so the end above is the rule's doing. */
    @Test
    void andWithoutTheRuleNothingStopsIt() {
        NumericDomain.Bounds runs = stoppedAt(UNRULED, "h.q@A.lo", "h.q@A.hi", 7);

        assertTrue(runs == null || runs.max() == null,
                () -> "h.q@A.lo is stopped by nothing once the rule is gone, and runs " + runs);
    }

    /**
     * The relation carried into one case is about that case's numbers.
     *
     * <p>Two values of one sum's declaration, related by a rule written about one of them. Fixing
     * the one leaves the other where its own type leaves it — a renaming that carried the rule to
     * every position spelled {@code lo} would stop this one too.
     */
    @Test
    void andItIsAboutTheValueTheRuleWasWrittenAbout() {
        NumericDomain.Bounds runs = stoppedAt(ONE_SUM_OF_TWO, "h.r@A.lo", "h.p@A.hi", 7);

        assertTrue(runs == null || runs.max() == null,
                () -> "h.r@A.lo is stopped by nothing, the rule being about h.p, and runs " + runs);
    }

    /**
     * One relation, two sums narrowed independently, and it reaches both.
     *
     * <p>What the whole context selects, in one renaming. Crossed one narrowing at a time, the copy
     * carried into {@code left@A} still calls the other name {@code right.hi} and the copy carried
     * into {@code right@B} still calls this one {@code left.lo}, so nothing relates the two numbers
     * a caller asked about.
     */
    @Test
    void aRelationCrossesTwoNarrowingsAtOnce() {
        NumericDomain.Bounds runs = stoppedAt(TWO_SUMS, "h.left@A.lo", "h.right@B.hi", 7);

        assertNotNull(runs, "h.left@A.lo is a number this reading answers about");
        assertNotNull(runs.max(),
                () -> "h.left@A.lo stops where h.right@B.hi was fixed, and runs " + runs);
        assertEquals("7", number(runs.max()),
                "h.left@A.lo stops where the rule across the two sums put it");
    }

    @Test
    void andWithoutThatRuleNothingStopsItEither() {
        NumericDomain.Bounds runs =
                stoppedAt(TWO_SUMS_UNRULED, "h.left@A.lo", "h.right@B.hi", 7);

        assertTrue(runs == null || runs.max() == null,
                () -> "h.left@A.lo is stopped by nothing once the rule is gone, and runs " + runs);
    }

    /**
     * A name that crosses two narrowings on the way down.
     *
     * <p>What the value above calls {@code m.d.lo} stands at {@code m@MA.d@DA.lo}, two cases in. The
     * renamings compose or they do not: applied in an order that takes the second before the first
     * has moved the name, the clause is left about a place no position of this input is at.
     */
    @Test
    void aRelationCrossesTwoNarrowingsOneUnderTheOther() {
        NumericDomain.Bounds runs =
                stoppedAt(TWO_DEEP, "h.m@MA.d@DA.lo", "h.m@MA.d@DA.hi", 7);

        assertNotNull(runs, "h.m@MA.d@DA.lo is a number this reading answers about");
        assertNotNull(runs.max(),
                () -> "h.m@MA.d@DA.lo stops where h.m@MA.d@DA.hi was fixed, and runs " + runs);
        assertEquals("7", number(runs.max()),
                "the rule reaches the numbers its names stand at, two narrowings down");
    }

    @Test
    void andWithoutThatRuleNothingStopsItTwoDeepEither() {
        NumericDomain.Bounds runs =
                stoppedAt(TWO_DEEP_UNRULED, "h.m@MA.d@DA.lo", "h.m@MA.d@DA.hi", 7);

        assertTrue(runs == null || runs.max() == null,
                () -> "h.m@MA.d@DA.lo is stopped by nothing once the rule is gone, and runs "
                        + runs);
    }

    private static final String BESIDE_A_SUM = """
            module g

            data A = { x: Int }
            data B = { y: Int }
            data Q = A | B

            data Holder = { q: Q, note: Int }
                invariant capped = note <= 5

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same, with one case of the sum left with no value, and with an element of one. */
    private static final String BESIDE_A_DEAD_CASE = BESIDE_A_SUM.replace(
            "data A = { x: Int }",
            """
                    data A = { x: Int }
                        invariant impossible = x >= 1 && x <= 0""");

    private static final String BESIDE_A_DEAD_ELEMENT = BESIDE_A_SUM.replace(
            "data Holder = { q: Q, note: Int }",
            """
                    data Item = { charge: Int }
                        invariant impossible = charge >= 1 && charge <= 0
                    data Holder = { q: Q, note: Int, items: List<Item> }""");

    /**
     * A question about a position under no narrowing answers the same whatever became of the cases.
     *
     * <p>Which rules reach a position is settled by where it stands, so a case the rules refuse
     * takes nothing away from a number beside it — and neither does an element declaration nothing
     * can build. Read as one space met out of every reading, both of those emptied the answer here.
     */
    @Test
    void aQuestionOutsideTheNarrowingDoesNotMove() {
        NumericDomain.Bounds alone = stoppedAt(BESIDE_A_SUM, "h.note", "h.note", 3);

        for (String beside : List.of(BESIDE_A_DEAD_CASE, BESIDE_A_DEAD_ELEMENT)) {
            assertEquals(alone, stoppedAt(beside, "h.note", "h.note", 3),
                    "h.note runs where its own rule leaves it, whatever stands beside it");
        }
    }

    /** Where {@code asked} runs once {@code fixed} stands at {@code at}. */
    private static NumericDomain.Bounds stoppedAt(String source, String asked, String fixed,
                                                  int at) {
        InputDomain read = reading(source, "read");
        Quantities quantities = read.quantities(rulesOf(source));
        return quantities
                .given(new NumericTerm.ValueOf(pathOf(read, fixed)),
                        Count.of(BigDecimal.valueOf(at)))
                .runsBetween(new NumericTerm.ValueOf(pathOf(read, asked)));
    }

    private static String number(souther.compiler.numeric.Endpoint end) {
        return Count.number(end.at()).at().stripTrailingZeros().toPlainString();
    }

    private static TermPath pathOf(InputDomain read, String spelled) {
        return read.positions().stream().map(Position::path)
                .filter(each -> each.toString().equals(spelled))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no position at " + spelled + " among " + read.positions().stream()
                                .map(Position::path).toList()));
    }

    private static RuleReadingSource rulesOf(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        return RuleReadings.of(compilation, compilation.modules().get(0));
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
