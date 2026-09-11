package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An edge nothing has shown to be writable says what was tried, not only that nothing was.
 *
 * <p>A line the rules draw is not counted until a concrete value is accepted at it, and a search
 * that composed nothing settles nothing either way. That is the semantics, and read off the report
 * alone it looks like an accident: the same four lines are counted in one model and not in another
 * that differs by a constraint on a field they have nothing to do with.
 *
 * <p>What separates the two is which facts the compiler managed to establish, and it is the one
 * thing the line does not say. An author who cannot see that reads it as the tool being unreliable
 * rather than as the tool being honest about what it could not prove.
 */
class AnEdgeNothingPromisesSaysWhatWasTriedTest {

    /** A disequality nothing can project, and a sibling whose pattern nothing can write. */
    private static final String MODEL = """
            module example.d4

            data Ok

            data Amount = Int
                invariant range = value >= 0 && value <= 100

            data Tag = String
                invariant shape = String.matches("(a+)\\\\1", value)

            data Pair = { low: Amount, high: Amount, tag: Tag }
                invariant together = low.value /= high.value

            behavior check : (p: Pair) -> Ok

            let check (p) = Ok

            example check
                | "a pair" : (Pair { low = Amount(5), high = Amount(7), tag = Tag("aa") }) -> Ok
            """;

    private static String report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /** The line is still said, which is what keeps a discovered edge from disappearing. */
    @Test
    void theEdgeIsStillNamed() {
        String human = report();

        // Under the declarations, whose line it is, in the words the declaration wrote — and the
        // positions that read it under that.
        assertTrue(human.contains("nothing could show a row can be written at the ON point"
                + " value = 0 (invariant Amount (range))"), human);
        assertTrue(human.contains("read as check/p.low: = 0"), human);
    }

    /**
     * And the edge is inside the count, which is what a reader walks from.
     *
     * <p>Four obligations and two rows, over a model whose one uncomposable rule is about a field
     * the four have nothing to do with. Left out of the count, they made the same model read as
     * fully answered — so the number moved with what this compiler could build rather than with
     * what the model owes, and a model measured one day stopped being measured the next by a
     * neighbour's rule.
     */
    @Test
    void theEdgeIsCountedAndTheVerdictSaysNobodyCouldDecideIt() {
        String human = report();

        assertTrue(human.contains("declarations   obligations 2/4"), human);
        assertTrue(human.contains("adequacy: undetermined"), human);
    }

    /** And it says what the search came to, so the verdict is legible rather than surprising. */
    @Test
    void theLineSaysWhatTheSearchCameTo() {
        String human = report();

        String line = human.lines()
                .filter(each -> each.contains("read as check/p.low: = 0"))
                .findFirst().orElseThrow(() -> new AssertionError(human));

        assertTrue(line.contains("nothing composed one"), line);
    }
}
