package souther.compiler.query;

import souther.compiler.partition.Axis;
import souther.compiler.partition.PartitionClass;
import souther.compiler.partition.PositionMeasurements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A location is measured at every number its rules name of it, whichever of them wrote about which.
 *
 * <p>What stands at a position and what an operation counts of it are two numbers, and a rule about
 * either is a rule about that one. So which measures a location has is a question about numbers: a
 * declaration bounding one of them says nothing about whether a body divides the other, and a stage
 * that read the location instead — "this place is measured already" — drops the second, whichever
 * of the two the model happens to have written second.
 *
 * <p>Dropped rather than refused. The measure is not published, so nothing counts the classes it
 * would have had and no report names them; what an author wrote divides the model and is measured
 * nowhere. Which is why this is asked of a model where the two rules are about different numbers of
 * one place, and where each is plainly a distinction the model makes.
 */
class APositionIsMeasuredAtEveryNumberItsRulesNameTest {

    /**
     * A declaration about the length, and a body about the value.
     *
     * <p>Two numbers of one position, written by the two sides. Neither rule mentions the other's
     * number: the invariant admits every string of a length, and the guard admits strings without
     * end and leaves out strings without end.
     */
    private static final String THE_LENGTH_AND_THE_VALUE = """
            module example.prefix

            data Ticket = String
                invariant String.length(value) >= 1
            data Seated = { ticket: Ticket }

            behavior seat : (t: Ticket) -> Seated | NotOnTheList
                constructs Seated

            let seat (t) = {
                guard String.startsWith("w-", t.value) else NotOnTheList
                Seated { ticket = t }
            }
            """;

    /**
     * The same, with the declaration bounding both numbers and the body writing twice about one.
     *
     * <p>What holds the measures to one per number, from both sides at once: a number a declaration
     * bounded and a body divides, and a number two of the body's own rules are about. A stage
     * taking up what it is handed without asking which measure each belongs to would publish the
     * position's value more than once, and an axis is what a report counts classes at — so the same
     * number counted twice is a denominator that says the model makes distinctions it does not.
     *
     * <p>A third number is not written here because the position has not got one: a string is what
     * stands there and what its length counts, and a rule about anything else is about a number
     * this position has not.
     */
    private static final String BOTH_NUMBERS_BOUNDED = """
            module example.prefix

            data Ticket = String
                invariant String.length(value) >= 1 && value >= "A"
            data Seated = { ticket: Ticket }

            behavior seat : (t: Ticket) -> Seated | NotOnTheList
                constructs Seated

            let seat (t) = {
                guard String.startsWith("w-", t.value) else NotOnTheList
                guard String.endsWith("-1", t.value) else NotOnTheList
                Seated { ticket = t }
            }
            """;

    private static List<Axis> axesOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors(), "the model under test compiles");
        List<Axis> out = new java.util.ArrayList<>();
        for (PositionMeasurements at : compilation.db()
                .ask(new Adequacy.Divided("example.prefix", "seat")).value().measurements()) {
            out.addAll(at.axes());
        }
        return out;
    }

    private static List<String> namesOf(List<Axis> axes) {
        return axes.stream().map(each -> each.id().term()).toList();
    }

    private static List<String> classesAt(List<Axis> axes, String term) {
        return axes.stream().filter(each -> each.id().term().equals(term))
                .flatMap(each -> each.classes().stream()).map(PartitionClass::id).toList();
    }

    /** Both numbers are measured, and the one the body divides carries what it divided into. */
    @Test
    void aBodyDividingOneNumberIsMeasuredWhereADeclarationBoundsTheOther() {
        List<Axis> axes = axesOf(THE_LENGTH_AND_THE_VALUE);

        assertEquals(List.of("t", "String.length(t)"), namesOf(axes));
        assertEquals(List.of("t/String.startsWith(\"w-\", x)", "t/not String.startsWith(\"w-\", x)"),
                classesAt(axes, "t"));
    }

    /**
     * And a number both sides write about is one measure.
     *
     * <p>The classes are what the body's rules come to between them, at the number the declaration
     * bounded. Two measures of it would be one number counted twice, and the classes of the second
     * would be counted as distinctions beside the first's rather than as the same values divided
     * again.
     */
    @Test
    void aNumberBothSidesWriteAboutIsMeasuredOnce() {
        List<Axis> axes = axesOf(BOTH_NUMBERS_BOUNDED);

        assertEquals(List.of("t", "String.length(t)"), namesOf(axes));
        assertEquals(List.of(
                        "t/String.startsWith(\"w-\", x) and String.endsWith(\"-1\", x)",
                        "t/String.startsWith(\"w-\", x) and not String.endsWith(\"-1\", x)",
                        "t/not String.startsWith(\"w-\", x) and String.endsWith(\"-1\", x)",
                        "t/not String.startsWith(\"w-\", x) and not String.endsWith(\"-1\", x)"),
                classesAt(axes, "t"));
    }
}
