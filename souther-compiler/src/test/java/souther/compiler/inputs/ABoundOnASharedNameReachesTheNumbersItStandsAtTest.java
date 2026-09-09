package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An end a record puts on a name its cases share is an end on the numbers that name stands at.
 *
 * <p>{@code Holder} says {@code q.limit <= 10} and {@code q} is a sum whose cases spread the
 * declaration that writes {@code limit}. The name is written at {@code q}; the numbers are at
 * {@code q@A.limit} and {@code q@B.limit}. A reader asking where one of those runs is asking about
 * a number the rule put an end on, and the end has to be part of the answer.
 *
 * <p><b>An end and not a relation.</b> What reaches those numbers is what the rule leaves this one
 * name, which is a projection of it. A rule over two shared names — {@code q.lo <= q.hi} — is not a
 * projection of either, and nothing of it reaches them: fixing {@code q@A.hi} leaves {@code q@A.lo}
 * running as widely as its own type does. That is the thing this test does not measure, and it is
 * not the same reading being incomplete — the relation is lost where the outer value's rules are
 * carried to the cases as per-name answers rather than as rules.
 */
class ABoundOnASharedNameReachesTheNumbersItStandsAtTest {

    private static final String SHARED = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Holder = { q: Q }
                invariant small = q.limit <= 10

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same model with the rule taken out, so that what the rule does is what is measured. */
    private static final String UNRULED = SHARED.replace("    invariant small = q.limit <= 10\n",
            "");

    @Test
    void theEndTheRecordPutOnItStopsEachCase() {
        for (String at : List.of("h.q@A.limit", "h.q@B.limit")) {
            NumericDomain.Bounds runs = runsAt(SHARED, at);

            assertNotNull(runs, at + " is a number this reading answers about");
            assertNotNull(runs.max(),
                    () -> at + " is stopped above by the rule the record wrote, and runs " + runs);
            assertEquals("10", souther.compiler.numeric.Count.number(runs.max().at()).at()
                            .stripTrailingZeros().toPlainString(),
                    at + " stops where the rule the record wrote put it");
        }
    }

    /**
     * And nothing else stops it, so the end above is the rule's doing.
     *
     * <p>Without this the first would pass on a reading that stops every {@code Int} for reasons of
     * its own, and would say nothing about whether an end put on a name reaches the numbers that
     * name stands at.
     */
    @Test
    void andWithoutTheRuleNothingStopsIt() {
        for (String at : List.of("h.q@A.limit", "h.q@B.limit")) {
            NumericDomain.Bounds runs = runsAt(UNRULED, at);

            assertTrue(runs == null || runs.max() == null,
                    () -> at + " is stopped by nothing once the rule is gone, and runs " + runs);
        }
    }

    /**
     * And the two readings of that end are one answer.
     *
     * <p>What a rule of the value above leaves a position is read twice: once per name, as the
     * reading of the position is made, and once as a rule said in the words of the case, where the
     * relations are. Both are readings of one crossing, and the day they part is the day a caller's
     * answer depends on which of them it happened to ask.
     */
    @Test
    void andThePositionAndTheQuantityAgreeAboutIt() {
        for (String at : List.of("h.q@A.limit", "h.q@B.limit")) {
            InputDomain read = reading(SHARED, "read");
            NumericDomain.Bounds position = read.positions().stream()
                    .filter(each -> each.path().toString().equals(at))
                    .map(each -> each.boundsFor(new NumericTerm.ValueOf(each.path())).admissible())
                    .findFirst().orElseThrow();

            assertEquals(position, runsAt(SHARED, at),
                    at + " stops where it stops, whichever reading is asked");
        }
    }

    private static NumericDomain.Bounds runsAt(String source, String spelled) {
        InputDomain read = reading(source, "read");
        return read.quantities(rulesOf(source))
                .runsBetween(new NumericTerm.ValueOf(pathOf(read, spelled)));
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
