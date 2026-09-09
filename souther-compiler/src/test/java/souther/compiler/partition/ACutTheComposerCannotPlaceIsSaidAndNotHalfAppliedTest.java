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
import souther.compiler.numeric.Place;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A condition the composer cannot put a value under is said, and none of its positions is pinned.
 *
 * <p>Two things, and the second is why the first is worth saying. A cut over two positions is one
 * statement about the pair: one of them put where the cut admits while the other keeps whatever the
 * declarations leave is not half the condition holding, it is the condition not holding with a
 * position pinned on the strength of it. So a cut this cannot place every position of is placed at
 * none — and then the fact that it was handed a condition it could not act on is carried out with
 * the answer, because a row composed without it may not arrive and nothing else would say why.
 *
 * <p><b>What a location holds and what is measured there are two things.</b> A row writes one value
 * where a location is, and a location may have more than one number taken at it — the length of a
 * string beside the string. Keyed by the location, the second of them is dropped for the first; and
 * a condition above the line dropped without a word is the composer having been handed the way and
 * quietly not using it.
 *
 * <p>Held here rather than against a model, because what is under test is the rule and not which
 * models happen to reach it. Naming the terms directly says which case is which, where a search for
 * a model that produces one would leave that to be read off the answer.
 */
class ACutTheComposerCannotPlaceIsSaidAndNotHalfAppliedTest {

    private static final String BOUNDED = """
            module example.bounded

            data Amount = Int
                invariant value >= 0 && value <= 100
            data Code = String
                invariant String.length(value) >= 4
            data Req = { cost: Amount, code: Code }
            data Res = { n: Int }

            behavior f : (r: Req) -> Res
            """;

    /**
     * A cut over a position the declarations put nothing at is on the answer's account.
     *
     * <p>And the row is still composed. What the condition would have narrowed is a position the
     * row writes anyway, so leaving it out costs the row nothing here — what it costs is the
     * certainty that the row arrives, which is the thing being written down.
     */
    @Test
    void aCutNamingAPositionWithNoOrderIsCarriedOutWithTheAnswer() {
        Generator.BoundaryAttempt attempt = composing(costAxis(), Count.of(100),
                cut(new NumericTerm.ValueOf(TermPath.of("r").then("elsewhere"))));

        assertEquals(1, attempt.unrepresented().size(),
                "the one cut it was handed and could not place: " + attempt.unrepresented());
        assertInstanceOf(ReachabilityGap.Why.NoValueComposedForItsPositions.class,
                attempt.unrepresented().get(0).why());
        assertEquals(WHERE, attempt.unrepresented().get(0).anchor(),
                "said of the condition, which is what a reader is sent to");
    }

    /**
     * A cut naming another number taken at a location the item already writes is on it too.
     *
     * <p>The item fixes how long the string is; the condition above the line is about the string
     * itself. One location, two numbers, and the one value a row writes there would have to answer
     * both — which is not something this composes. Dropped for sharing a location with the item's
     * own number, the condition would go unmet by a row nothing said anything about.
     */
    @Test
    void aCutNamingAnotherNumberAtALocationTheItemWritesIsSaidToo() {
        Axis length = codeAxis();
        assertInstanceOf(NumericTerm.TakenOf.class, length.term(),
                "the item's own number here is one taken of the location");

        Generator.BoundaryAttempt attempt = composing(length, Count.of(4),
                cut(new NumericTerm.ValueOf(length.term().position())));

        assertEquals(1, attempt.unrepresented().size(),
                "one location, two numbers, and nothing composes a value to both: "
                        + attempt.unrepresented());
        assertInstanceOf(ReachabilityGap.Why.TwoNumbersAtOneLocation.class,
                attempt.unrepresented().get(0).why(),
                "said as what it is, and not as a position nothing could build at");
    }

    /**
     * And a cut it can place every position of is not on it.
     *
     * <p>The other half, so that an empty account means what it says. Asserted of the same
     * composition with one thing changed, since an account that was empty for some other reason
     * would pass a test that only looked at the first case.
     */
    @Test
    void aCutItCanPlaceIsNotSaidToBeUnrepresented() {
        Generator.BoundaryAttempt attempt =
                composing(costAxis(), Count.of(100), cut(costAxis().term()));

        assertTrue(attempt.unrepresented().isEmpty(),
                "every condition it was handed was one it put a value under: "
                        + attempt.unrepresented());
    }

    /** Which question a report about the condition under test would put. */
    private static final ConditionReportAnchor WHERE =
            new ConditionReportAnchor.WhereTheReadingMetIt("m",
                    new ConditionOccurrence("b", 0));

    private static OnTheWay.TakenIn cut(NumericTerm over) {
        return new OnTheWay.TakenIn(WHERE,
                new ReachingCuts.Cut(LinearForm.atom(over), Rel.GE));
    }

    /** A row composed with {@code axis} at {@code at}, and {@code taken} on the way to it. */
    private static Generator.BoundaryAttempt composing(Axis axis, Place at, OnTheWay.TakenIn taken) {
        return Generator.probeFixing(subject(), axis.path() + " = " + at,
                Map.of(new RealizationTarget.AtOnePosition(axis.term()), at),
                new Reachability.Reaching(domain().quantities(rules()).region(),
                        Requirements.NONE, List.of(taken)),
                Generator.CandidateCheck.ANY);
    }

    /** The number one position's own content is. */
    private static Axis costAxis() {
        return axisAt("r.cost");
    }

    /** And a number taken of a position rather than held by it. */
    private static Axis codeAxis() {
        return axisAt("r.code");
    }

    // The compilation, read once and answered from. Every helper below asks it for one thing, and a
    // compile apiece would be the same model built as many times as this file has questions.
    private static final Compilation COMPILATION = compiled();

    private static Compilation compiled() {
        Compilation made = Compilation.ofSource(BOUNDED, "Main");
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
                .filter(each -> each.name().equals("f")).findFirst().orElseThrow();
    }

    private static InputDomain domain() {
        InputDomain read = COMPILATION.db()
                .ask(new souther.compiler.query.Adequacy.Inputs(module())).value()
                .get(spec().name());
        assertNotNull(read, "the model under test compiles");
        return read;
    }

    private static Axis axisAt(String path) {
        return axes().stream()
                .filter(each -> each.path().toString().equals(path)).findFirst().orElseThrow();
    }

    private static List<Axis> axes() {
        return Partitions.of(spec().name(), domain(), rules(), ReadAs.THE_COMPILATION_DOES)
                .axes();
    }

    private static MeasuredInput subject() {
        List<String> names = new ArrayList<>();
        spec().params().forEach(each -> names.add(each.name()));
        return MeasuredInput.of(spec().name(), domain().reading(rules()),
                Partitions.of(spec().name(), domain(), rules(), ReadAs.THE_COMPILATION_DOES));
    }
}
