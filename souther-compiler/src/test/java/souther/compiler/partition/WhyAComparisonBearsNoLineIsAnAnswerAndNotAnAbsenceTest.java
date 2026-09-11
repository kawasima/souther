package souther.compiler.partition;

import souther.compiler.WhereItSits;
import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.diag.Citation;
import souther.compiler.inputs.InputReads;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A comparison that bears no line says which of the reasons it is.
 *
 * <p>They are not one answer. A truth nothing reads is one the behavior draws no boundary on at all;
 * a comparison no run answers through is one whose outcome is about no row, arrived at from where
 * it stands rather than from what reads it. Neither is reported, and which of the two refused a
 * comparison is a fact the policy has when it refuses — folded into one {@code false}, whoever
 * needs it next works it out again.
 *
 * <p>What a comparison is written inside is no part of this. A step a combinator applies once per
 * element is passed as many times as there are elements, and the two written here stand together to
 * say that the policy admits both: what each of them comes to is settled by the arithmetic, over
 * the container the input walk names and over the one written in the body alike.
 */
class WhyAComparisonBearsNoLineIsAnAnswerAndNotAnAbsenceTest {

    private static final String MODEL = """
            module example.why
            import List ( filter )

            data Temp = Int

            behavior read : (temp: Temp, xs: List<Int>) -> List<Int>
            let read (temp, xs) = {
                let cold = temp.value < 240
                let unread = temp.value < 100
                let kept = filter(x -> x < 50, xs)
                let fixed = filter(y -> y < 70, [ 1, 2, 3 ])

                if cold then kept else fixed
            }
            """;

    /** What this policy says about the comparison written on {@code line}. */
    private static BoundaryPolicy.Standing standingAt(int line) {
        BoundaryPolicy.Standing said = classified().get(line);
        assertNotNull(said, () -> "the model under test writes a comparison on line " + line);
        return said;
    }

    /** Every comparison of the body, by the line of the source it is written on. */
    private static Map<Integer, BoundaryPolicy.Standing> classified() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("read");
        assertNotNull(body);
        souther.compiler.inputs.InputDomain inputs = compilation.db()
                .ask(new Adequacy.Inputs(module)).value().get("read");

        RuleReadingSource rules = RuleReadings.of(compilation, module);

        Map<Integer, BoundaryPolicy.Standing> byLine = new LinkedHashMap<>();
        for (ComparisonReadings.Reading each
                : ComparisonReadings.of("read", body, inputs.reading(rules),
                        InputReads.ofParameters(inputs.parameterReads(),
                                checked.elementBindings().get("read"))).comparisons()) {
            Citation.Written at = assertInstanceOf(
                    Citation.Written.class, each.at(),
                    "the model under test is written in this compile's own source");
            byLine.put(WhereItSits.in(module, at.at()).line(), each.standing());
        }
        return byLine;
    }

    /** A truth a fork below reads is a line. */
    @Test
    void aTruthSomethingReadsBearsALine() {
        assertEquals(BoundaryPolicy.Standing.Admitted.class,
                standingAt(8).getClass());
    }

    /** A truth nothing reads is not a boundary of the behavior at all. */
    @Test
    void aTruthNothingReadsIsNotABoundaryOfTheBehavior() {
        assertEquals(NotABoundary.NOTHING_READS_IT, whyOf(standingAt(9)));
    }

    /** A truth one run reaches once per element of an input bears a line at that element. */
    @Test
    void aTruthReachedOncePerElementOfAnInputBearsALine() {
        assertEquals(BoundaryPolicy.Standing.Admitted.class,
                standingAt(10).getClass(),
                "each pass is one occurrence of a position, so a row can be read at it");
    }

    /**
     * The same comparison over a container the input walk names nothing at is a boundary all the
     * same, and what it comes to is the reading's answer.
     *
     * <p>How many times a run passes it is not one of these reasons. A comparison this admits bears
     * a line where the arithmetic reads one, and a reading comes to a line over positions of the
     * input and nothing else — so every pass of a comparison that bears one reads what the row
     * holds. This one is read over the elements of a list written {@code [1, 2, 3]}, which state
     * three different numbers and therefore state none; that is said where a rule that could not be
     * read is said, and not here.
     */
    @Test
    void aTruthOneRunReachesMoreThanOnceIsStillOneThisPolicyAdmits() {
        assertEquals(BoundaryPolicy.Standing.Admitted.class, standingAt(11).getClass());
    }

    private static NotABoundary whyOf(BoundaryPolicy.Standing standing) {
        return standing instanceof BoundaryPolicy.Standing.Refused none ? none.why() : null;
    }
}
