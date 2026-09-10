package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row's value at a position divided into sets of strings falls in one of them, whether or not the
 * position's type is a name over the string.
 *
 * <p>A name over one value is not a step in a path, so a walk to the position leaves the
 * construction and the string is one inside it — while the rules that divided the position divided
 * the strings it wraps. Read as the case it is, such a value is in none of the sets those rules
 * stated, and the classes of the position turn out to hold none of what the position holds. That is
 * the partition's own exhaustiveness broken, and it is raised rather than reported, so a build over
 * a model of this shape stops.
 *
 * <p>Asked of what a run left and not of the classes alone. The classes are exclusive and exhaustive
 * over the strings either way — the reading that drew them never saw the name — so a test that read
 * them would have passed throughout.
 */
class AValueWrittenUnderANameIsInTheClassesOfWhatTheNameHoldsTest {

    /**
     * A string a body divides, under a name.
     *
     * <p>No invariant on {@code Ticket}: a rule bounding some other number of the position takes
     * these classes off the measures altogether, and then nothing asks which class anything is in.
     */
    private static final String UNDER_A_NAME = """
            module example.prefix

            data Ticket = String
            data Seated = { ticket: Ticket }

            behavior seat : (t: Ticket) -> Seated | NotOnTheList
                constructs Seated

            let seat (t) = {
                guard String.startsWith("w-", t.value) else NotOnTheList
                Seated { ticket = t }
            }

            example seat
                | "one that is on the list" : (Ticket("w-1")) -> Seated { ticket = Ticket("w-1") }
            """;

    /**
     * The same body over a record whose one field happens to be called {@code value}.
     *
     * <p>A name over a value and a record with one field are two declarations and not two spellings
     * of one (spec §data), and the difference is what a reader of an observation cannot see: both
     * are observed as a construction carrying a field called {@code value}. So the difference has
     * to be settled from the declarations, and it is — here it is settled before any of this, by
     * the record being given up in favour of the position under it.
     */
    private static final String IN_A_RECORD = """
            module example.boxed

            data Ticket = { value: String }
            data Seated = { ticket: Ticket }

            behavior seat : (t: Ticket) -> Seated | NotOnTheList
                constructs Seated

            let seat (t) = {
                guard String.startsWith("w-", t.value) else NotOnTheList
                Seated { ticket = t }
            }

            example seat
                | "one that is on the list" :
                    (Ticket { value = "w-1" }) -> Seated { ticket = Ticket { value = "w-1" } }
            """;

    private static PartitionEvidence.AxisCoverage axis(String source, String path) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence partition = compilation.db()
                .ask(new Adequacy.Coverage(compilation.modules().get(0))).value().get("seat");
        assertNotNull(partition, "the model under test compiles");
        return partition.axes().stream().filter(each -> each.path().equals(path))
                .findFirst().orElseThrow();
    }

    /**
     * The row is in the class its string belongs to.
     *
     * <p>Which class, and not that some class was reached: the point is that the value the name
     * holds is what places it, so the assertion names the side of the prefix the row's string is
     * on. A row placed in the other one would be a value read as something it is not.
     */
    @Test
    void aRowIsPlacedByTheStringItsNameHolds() {
        PartitionEvidence.AxisCoverage at = axis(UNDER_A_NAME, "t");

        PartitionEvidence.AxisCoverage.Reached reached = at.reached().made().orElseThrow();
        assertEquals(0, reached.unclassifiedRows(),
                "a row whose value the classes were drawn over was read for it");
        assertTrue(reached.covered().contains("t/String.startsWith(\"w-\", x)"),
                () -> "the class the row's string is in, among " + at.classes()
                        + ", reached " + reached.covered());
    }

    /** And the classes it is placed among are the ones the body drew. */
    @Test
    void theClassesAreTheSidesTheBodyDrew() {
        assertEquals(
                java.util.List.of("t/String.startsWith(\"w-\", x)",
                        "t/not String.startsWith(\"w-\", x)"),
                axis(UNDER_A_NAME, "t").classes());
    }

    /**
     * And a record with a field of that name divides the field, not the record.
     *
     * <p>Which is where the two declarations part, and the reason nothing downstream has to tell
     * them apart from what a row wrote: a name over a value is one position and the classes are of
     * it, a record is given up in favour of the positions under it and the classes are of the field.
     *
     * <p>What this holds is that separation and not the absence of a reader that would guess at it.
     * Nothing here hands a construction to a class at all — the position is the field, so what
     * arrives is the string — so a reader that read the two constructions alike would pass this as
     * readily. What refuses one is that the names a class is written under are the ones the
     * position's type view read, and taking them off asks for those names.
     */
    @Test
    void aRecordIsDividedAtItsFieldAndNotAsANameOverIt() {
        PartitionEvidence.AxisCoverage at = axis(IN_A_RECORD, "t.value");

        assertEquals(
                java.util.List.of("t.value/String.startsWith(\"w-\", x)",
                        "t.value/not String.startsWith(\"w-\", x)"),
                at.classes());
        assertTrue(at.reached().made().orElseThrow().covered()
                        .contains("t.value/String.startsWith(\"w-\", x)"),
                "the row is placed at the field the classes are of");
    }
}
