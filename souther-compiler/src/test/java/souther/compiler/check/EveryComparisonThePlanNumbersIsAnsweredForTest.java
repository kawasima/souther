package souther.compiler.check;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.InvariantChecker.GaveUp;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A walk that finished answered for every comparison the plan numbered, arrivals included.
 *
 * <p>Absence and the answer that restricts nothing read alike below — a line is dropped only by a
 * proof, so a reader handed neither keeps its line either way — and that is the fail-open direction
 * and the right one. It is also why nothing downstream can notice a walk that quietly stopped
 * filing: the measure it produces is a measure over lines nobody took away, which is what a correct
 * run looks like. So the completeness of the reading is established here, where the walk finishes,
 * and not left to a consumer that has no way to ask.
 *
 * <p>Two layers and they are not one question. Whether an entry is there is a fact about this
 * analysis, which is what this test reads; what the entry means is a restriction a reader may
 * apply, and a missing one degrades to the restriction that is none.
 */
class EveryComparisonThePlanNumbersIsAnsweredForTest {

    /**
     * Comparisons in the places a walk files answers from: under a guard, inside a short circuit,
     * under an arm of a fork, inside a helper the call expands, and below a guard that leaves
     * nothing standing.
     */
    private static final String COMPARISONS = """
            module d

            data Amount = Int invariant value >= 0 && value <= 1000000
            data Free
            data Charged = { yen: Int }

            let over (n: Int): Int = if n < 6000 then 0 else 1

            behavior charge : (a: Amount, b: Amount) -> Free | Charged
                constructs Charged

            let charge (a, b) = {
                guard a.value < 5000 && b.value < 5000 else Free
                guard over(a.value) == 0 else Free
                if a.value < 100 then {
                    guard b.value > 2000000 else Free
                    guard a.value < 6000 else Free
                    Charged { yen = 1 }
                } else Charged { yen = 2 }
            }
            """;

    @Test
    void aWalkThatFinishedIsHoldingAnAnswerForEachOfThem() {
        List<GaveUp> gaveUp = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.GAVE_UP = gaveUp;
        try {
            Compilation compilation = Compilation.ofSource(COMPARISONS, "Main");
            compilation.measure(Adequacy.Asked.fullReport());
            compilation.answerEverything();
            AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        } finally {
            InvariantChecker.GAVE_UP = null;
        }

        assertTrue(gaveUp.stream().noneMatch(each -> "reachability".equals(each.where())),
                () -> "the reading owes one answer per numbered comparison and said so: "
                        + gaveUp.stream().map(each -> each.why().said()).toList());
    }
}
