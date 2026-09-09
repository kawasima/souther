package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position the model states the values of is measured, whichever way the model states them.
 *
 * <p>Two spellings of one distinction. A sum with two cases and a newtype whose invariant admits two
 * values divide their position exactly alike — every value of it is one or the other, and a row sits
 * in one of them — and one of them was owed a row for the second class while the other was owed
 * nothing.
 *
 * <p>The one that was owed nothing was not merely unmeasured. Its report said the model draws no
 * distinction at that position, which is a sentence about the model, and the model says otherwise in
 * its own declaration. That is what a derivation reading two producers and calling their silence an
 * absence comes to: the third producer had the answer and was not part of what "everything was
 * asked" meant.
 *
 * <p>So what is fixed here is the shape of the two answers, not their spellings. A class off a sum
 * is named by the case and a class off an equality is named by the value, and no reading is asked to
 * make those one word.
 */
class ADistinctionIsMeasuredHoweverItIsSpelledTest {

    private static Partitions.Partitioning partitioningOf(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of(behavior, InputDomain.of(sigs.get(behavior), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    private static Axis only(Partitions.Partitioning partitioning) {
        assertEquals(1, partitioning.axes().size(), partitioning.axes().toString());
        return partitioning.axes().get(0);
    }

    private static final String AS_AN_INVARIANT = """
            module g

            data Gender = String
                invariant value == "A" || value == "B"

            data Accepted = { gender: Gender }
            data Refused  = { gender: Gender }

            behavior classify : (gender: Gender) -> Accepted | Refused
            """;

    private static final String AS_A_SUM = """
            module g

            data A
            data B
            data Gender = A | B

            data Accepted = { gender: Gender }
            data Refused  = { gender: Gender }

            behavior classify : (gender: Gender) -> Accepted | Refused
            """;

    /**
     * The two spellings divide the position into the same number of classes, each with a value to
     * write for it.
     *
     * <p>An injected behavior, which is where this was found and where it matters most: a behavior
     * with no body is measured by nothing but its declarations, so a position its declarations
     * divide is the whole of what a report can hold an implementation to.
     */
    @Test
    void anEnumerationWrittenAsAnInvariantDividesThePositionAsTheSumDoes() {
        Axis asInvariant = only(partitioningOf(AS_AN_INVARIANT, "classify"));
        Axis asSum = only(partitioningOf(AS_A_SUM, "classify"));

        assertTrue(asInvariant.derivable(), "the model admits two values and divides the position");
        assertEquals(asSum.classes().size(), asInvariant.classes().size());
        assertTrue(asInvariant.classes().stream().allMatch(PartitionClass::generatable),
                "each class has a value to write for it: " + asInvariant.classes());
    }

    /** And the position is not among the ones a report says nothing divides. */
    @Test
    void anEnumeratedPositionIsNotReportedAsOneTheModelDividesNoWay() {
        assertEquals(List.of(), partitioningOf(AS_AN_INVARIANT, "classify").undivided());
    }

    /** The same where the values are numbers: an equality over an `Int` states which values stand
     *  there as plainly as one over a `String`, and neither is an interval. */
    @Test
    void anEnumerationOverNumbersDividesThePositionToo() {
        Axis axis = only(partitioningOf("""
                module g

                data Small = Int
                    invariant value == 1 || value == 2

                data Accepted = { at: String }

                behavior classify : (n: Small) -> Accepted
                """, "classify"));

        assertTrue(axis.derivable());
        assertEquals(2, axis.classes().size(), axis.classes().toString());
    }

    /**
     * A position whose rule this cannot enumerate is said to be one this could not read.
     *
     * <p>Not one the model divides no way. There is a rule about which strings stand here, written
     * in the model, and what a report can say is that this compiler did not read it — which sends a
     * reader to a limit of the compiler rather than to a distinction their own model does not make.
     */
    @Test
    void aRuleThisCannotEnumerateIsNotAnAbsence() {
        List<UndividedPosition> undivided = partitioningOf("""
                module g

                data Email = String
                    invariant UNREAD

                data Accepted = { at: String }

                behavior classify : (email: Email) -> Accepted
                """.replace("UNREAD", souther.compiler.ARuleNoReadingTakesIn.about("value")),
                "classify").undivided();

        assertEquals(1, undivided.size(), undivided.toString());
        assertFalse(undivided.get(0).why() instanceof UndividedPosition.Why.Absent,
                "the model states a rule about this position, so nothing here may say it states none");
    }

    /**
     * A position nothing is written about is still an absence, and is still said to be one.
     *
     * <p>The other half, and the one that decides whether the first is worth having. Nothing is
     * invented for a position the model leaves open, and a reading that answered "I could not read
     * the rules here" for a bare {@code String} would say it of almost every position in every
     * model.
     */
    @Test
    void aPositionNothingIsWrittenAboutIsStillAnAbsence() {
        List<UndividedPosition> undivided = partitioningOf("""
                module g

                data Accepted = { at: String }

                behavior classify : (s: String) -> Accepted
                """, "classify").undivided();

        assertEquals(1, undivided.size(), undivided.toString());
        assertTrue(undivided.get(0).why() instanceof UndividedPosition.Why.Absent);
    }
}
