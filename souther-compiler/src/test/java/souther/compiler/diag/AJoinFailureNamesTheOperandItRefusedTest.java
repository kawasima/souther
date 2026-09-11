package souther.compiler.diag;

import souther.compiler.WhereItSits;
import souther.compiler.Compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a join is refused, the report is drawn at the operand it was refused at, and the type that
 * operand conflicts with is labelled only where a written operand has it.
 *
 * <p>Which of the two decides is where the type came from, not whether a region could be found for
 * it. A list literal and an {@code else}'s arms reach the join through an accumulator, and an
 * accumulator is the join of everything before it — so it is stated in words. That it happens to be
 * one operand's type when only one has been seen is a fact about the length of the input, and making
 * the report's shape turn on it would have {@code [1, "x", 2]} and {@code [1, 2, "x"]} reported two
 * different ways.
 *
 * <p>{@code ++} reaches the same join from two written operands, one level in from what it compares.
 * Both of them have their element type labelled, because both of them have one.
 *
 * <p><b>An accumulator naming several cases names them in the order the join met them</b>, which for
 * an {@code else} is the {@code then} branch and then the arms as they are written, and for a
 * {@code match} is the arms as they are written. What is quoted back to an author is then a function
 * of what the author wrote; a set handing its members over in the order their numbers fall would
 * quote an order nothing about the program decides, and moving any one of those numbers would move
 * the report.
 */
class AJoinFailureNamesTheOperandItRefusedTest {

    private static final String ROLES = """
            module demo

            data Admin
            data Member
            data Guest
            data Role = Admin | Member | Guest
            """;

    /** Two elements: the second is where the join was refused, and the first is still an accumulator. */
    @Test
    void aListOfTwoIsReportedAtTheSecondElement() {
        String source = """
                module demo

                let xs =
                    [ 1
                    , "a"
                    ]
                """;
        Diagnostic report = only(source);

        assertEquals("\"a\"", underlined(source, report));
        assertTrue(values(report).contains("String"), values(report).toString());
        assertTrue(values(report).contains("Int"), values(report).toString());
    }

    /**
     * The same list one element longer. The type the third element conflicts with is the join of the
     * two before it, which is no element's type.
     */
    @Test
    void aListOfThreeNamesTheJoinOfWhatCameBefore() {
        String source = ROLES + """

                let rs =
                    [ Admin
                    , Member
                    , 1
                    ]
                """;
        Diagnostic report = only(source);

        assertEquals("1", underlined(source, report));
        assertTrue(values(report).contains("Admin | Member"), values(report).toString());
    }

    /**
     * The row that pins the decision: an accumulated type is never labelled, however few elements
     * went into it. Two and three elements are reported the same way.
     */
    @Test
    void anAccumulatedTypeIsNeverGivenARegion() {
        String twoElements = """
                module demo

                let xs = [ 1, "a" ]
                """;
        String threeElements = """
                module demo

                let xs = [ 1, 2, "a" ]
                """;

        assertEquals(List.of(), only(twoElements).secondary());
        assertEquals(List.of(), only(threeElements).secondary());
    }

    /** `++` compares two written operands' element types, so both are labelled. */
    @Test
    void concatenationLabelsBothOperands() {
        String source = """
                module demo

                let xs = [1] ++ ["a"]
                """;
        Diagnostic report = only(source);

        assertEquals(2, report.secondary().size(), "both operands carry their element type");
        // where each label starts, not how wide it is drawn — the width of a list literal is
        // <<a-region-is-an-extent>>'s question and is one column here.
        String written = "let xs = [1] ++ [\"a\"]";
        assertEquals(List.of(written.indexOf("[1]") + 1, written.indexOf("[\"a\"]") + 1),
                report.secondary().stream().map(s -> WhereItSits.in(written, ((souther.compiler.diag.DiagnosticPlace.InSource) s.place()).region()).start().column()).toList());
        assertTrue(values(report).isEmpty(), "the message names neither type: " + values(report));
    }

    /**
     * An {@code else} answering per clause folds its arms the way a list folds its elements, so the
     * report is at the arm the join was refused at and the type it conflicts with is the join of the
     * branches before it — here the `then` branch and the first arm, which is neither one's type.
     */
    @Test
    void anElseArmIsReportedAtTheArmAndNamesTheJoinOfWhatCameBefore() {
        String source = BOUNDED + """

                let pick (n: Int) = {
                    guard Bounded(n) as b else
                        | nonNeg -> Blue
                        | small  -> 1
                    Red
                }
                """;
        Diagnostic report = only(source);

        assertEquals("1", underlined(source, report));
        assertTrue(values(report).contains("Red | Blue"), values(report).toString());
    }

    /**
     * One arm, so the accumulator holds only the `then` branch's type. It is still stated rather than
     * labelled: the `then` branch keeps no region of its own here.
     */
    @Test
    void aSingleElseArmIsReportedTheSameWay() {
        String source = BOUNDED + """

                let pick (n: Int) = {
                    guard Bounded(n) as b else 1
                    Red
                }
                """;
        Diagnostic report = only(source);

        assertEquals("1", underlined(source, report));
        assertEquals(List.of(), report.secondary(), "an accumulator is not an operand to point at");
        assertTrue(values(report).contains("Red"), values(report).toString());
    }

    /**
     * A {@code match} already reports this way and is left alone. It is here so that the three sites
     * changed are held against the one that was right.
     */
    @Test
    void aMatchIsAlreadyReportedAtTheArmThatWasRefused() {
        String source = ROLES + """

                data Red
                data Blue
                data Colour = Red | Blue

                let pick (r: Role) =
                    match r with
                        | Admin -> Red
                        | Member -> Blue
                        | Guest -> 1
                """;
        Diagnostic report = only(source);

        assertEquals("| Guest -> 1", line(source, report).trim());
        assertEquals(List.of(), report.secondary());
        assertTrue(values(report).contains("Red | Blue"), values(report).toString());
    }

    private static final String BOUNDED = """
            module demo

            data Red
            data Blue
            data Colour = Red | Blue

            data Bounded = Int
                invariant nonNeg = value >= 0
                invariant small = value <= 100
            """;

    /** The one report compiling {@code source} produces. */
    private static Diagnostic only(String source) {
        CompileException thrown =
                assertThrows(CompileException.class, () -> Compiler.compile(source));
        List<Diagnostic> all = thrown.diagnostics();
        assertEquals(1, all.size(), "one mistake, one report: " + all);
        return all.get(0);
    }

    /** The characters of {@code source} a report's primary region covers. */
    private static String underlined(String source, Diagnostic report) {
        return at(source, ((Primary.InSource) report.primary()).place().region());
    }

    /** The whole source line a report's primary region begins on. */
    private static String line(String source, Diagnostic report) {
        return source.lines().toList().get(WhereItSits.in(source, ((Primary.InSource) report.primary()).place().region()).start().line() - 1);
    }

    /** The characters of {@code source} {@code region} covers. */
    private static String at(String source, Region region) {
        assertEquals(WhereItSits.in(source, region).start().line(), WhereItSits.in(source, region).end().line(), "one line's worth");
        return WhereItSits.underlined(source, region);
    }

    /** The types a report carries, as it renders them. */
    private static List<String> values(Diagnostic report) {
        return report.values().values().stream().map(String::valueOf).toList();
    }
}
