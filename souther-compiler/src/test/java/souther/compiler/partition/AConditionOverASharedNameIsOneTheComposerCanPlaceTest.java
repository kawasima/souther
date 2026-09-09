package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.ast.Hir;
import souther.compiler.check.Prepared;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition above a line over a name every case of a sum spreads is one the composer has an order
 * for.
 *
 * <p>The generator's side of what the reading answers. {@code r.deadline} is a name a value of the
 * sum is read at: the reading has no position there because it goes under each case, and the
 * traversal that follows a written value has nothing there because a row writes one of the cases. A
 * composer that took the order from the second would report a condition it was handed as one nothing
 * could put a value under — the reachability was stated, and the answer would be that there is no
 * order for it.
 *
 * <p>So what is asserted is that the way's position was placed. What becomes of the row afterwards
 * is the construction question and is answered by the traversal that owns it: no value is composed
 * at the sum's own name, and the attempt says so rather than throwing.
 */
class AConditionOverASharedNameIsOneTheComposerCanPlaceTest {

    private static final String SPREAD = """
            module example.spread

            data Base = { deadline: Int }
            data P = { ...Base, x: Int }
            data T = { ...Base, y: Int }
            data Req = P | T

            data Ok
            data No

            behavior check : (r: Req, n: Int) -> Ok | No

            let check (r, n) = {
                guard n > 3 else No
                guard r.deadline > 10 else No
                Ok
            }
            """;

    /** The name every case spreads, which the reading has no position at. */
    private static final TermPath DEADLINE = TermPath.of("r").then("deadline");

    /** The reading has no position there, and no axis stands at it either — which is what sends the
     *  question to whatever the composer holds. */
    @Test
    void nothingStandsAtTheSharedNameToBeAskedInstead() {
        assertNull(domain().at(DEADLINE),
                "the reading goes under each case, so the shared name holds no position");
        assertEquals(List.of(), axes().stream()
                        .filter(each -> each.path().equals(DEADLINE)).toList(),
                "and no axis stands there to be asked in its place");
    }

    /**
     * The composer places the way's position and refuses the row for the reason that is true of it.
     *
     * <p>Asking the traversal that follows a written value instead answers that there is no order
     * for the number, which is a condition reported as unrepresentable and a row refused for
     * something that is not the case.
     */
    @Test
    void aConditionOverTheSharedNameIsPlacedAndTheRowIsRefusedForWhatIsTrueOfIt() {
        Generator.BoundaryAttempt attempt = composing(Count.of(4));

        assertEquals(List.of(), attempt.unrepresented(),
                "the composer had an order for the name every case spreads: "
                        + attempt.unrepresented());
        Generator.BoundaryAttempt.Unresolved no = (Generator.BoundaryAttempt.Unresolved) attempt;
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                no.why().reason(),
                "and no value is written at a sum's own name, which is the construction's answer");
    }

    /** Which question a report about the condition under test would put. */
    private static final ConditionReportAnchor WHERE =
            new ConditionReportAnchor.WhereTheReadingMetIt("m",
                    new ConditionOccurrence("b", 0));

    /** A row composed with the plain position fixed at {@code at}, and a condition over the shared
     *  name on the way to it. */
    private static Generator.BoundaryAttempt composing(Count at) {
        Axis fixed = axisAt("n");
        return Generator.probeFixing(subject(), "n = " + at,
                Map.of(new RealizationTarget.AtOnePosition(fixed.term()), at),
                new Reachability.Reaching(domain().quantities(rules()).region(),
                        Requirements.NONE,
                        List.of(new OnTheWay.TakenIn(WHERE, new ReachingCuts.Cut(
                                LinearForm.atom(new NumericTerm.ValueOf(DEADLINE)), Rel.GE)))),
                Generator.CandidateCheck.ANY);
    }

    // The compilation, read once and answered from.
    private static final Compilation COMPILATION = compiled();

    private static Compilation compiled() {
        Compilation made = Compilation.ofSource(SPREAD, "Main");
        // Measured, because the lines under test are drawn by the body: what the declarations alone
        // divide is the sum's cases, and a guard is read where a behavior is measured.
        made.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        made.answerEverything();
        return made;
    }

    private static String module() {
        return COMPILATION.modules().get(0);
    }

    private static RuleReadingSource rules() {
        return RuleReadings.of(COMPILATION, module());
    }

    private static Hir.SpecBehavior spec() {
        Prepared prepared = COMPILATION.db().ask(new Shapes.Prepared(module())).value();
        return (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(each -> each.name().equals("check")).findFirst().orElseThrow();
    }

    private static InputDomain domain() {
        InputDomain read = COMPILATION.db()
                .ask(new souther.compiler.query.Adequacy.Inputs(module())).value()
                .get(spec().name());
        assertNotNull(read, "the model under test compiles");
        return read;
    }

    /** The measures of this behavior as the phase that generates rows has them, which is where the
     *  body's guards are read. */
    private static List<Axis> axes() {
        return COMPILATION.db()
                .ask(new souther.compiler.query.Adequacy.Divided(module(), spec().name()))
                .value().axes();
    }

    private static Axis axisAt(String path) {
        return axes().stream().filter(each -> each.path().toString().equals(path))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "the model under test is measured at " + path + ", and this run has "
                                + axes().stream().map(each -> each.id().toString()).toList()));
    }

    private static MeasuredInput subject() {
        List<String> names = new ArrayList<>();
        spec().params().forEach(each -> names.add(each.name()));
        assertTrue(names.contains("n"), "the model takes the position the row is fixed at");
        return MeasuredInput.of(spec().name(), domain().reading(rules()),
                AxesATestWrote.asAMeasurement(spec().name(), axes()));
    }
}
