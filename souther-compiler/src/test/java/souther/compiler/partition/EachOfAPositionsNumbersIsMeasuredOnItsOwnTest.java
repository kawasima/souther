package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadings;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.ReadAs;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each number of a position is measured on its own, and the evidence goes to the number it names.
 *
 * <p>A {@code String} has two — its own order, and how long it is — and a rule is written about one
 * of them. What a rule about the length says is nothing a rule about the order contradicts, so
 * there is no choice to make between them: the position is measured at both, and each measure holds
 * the classes and lines about its own number.
 *
 * <p>Which is what keeps the two vocabularies apart. A class saying the value is {@code "AB"}
 * answers by reading a string; a line at two lies on the order a length is counted on. Brought to
 * one measure, one of them has to be labelled with the other's number, and then a class is asked
 * about a place it was never about.
 */
class EachOfAPositionsNumbersIsMeasuredOnItsOwnTest {

    /** Values named on the order, and a line on the length. */
    private static final String VALUES_AND_A_LENGTH = """
            module m

            data Code = String
                invariant named = value == "AB" || value == "CD"
                invariant long = String.length(value) >= 2

            data Ok = { size: Int }

            behavior onCode : (v: Code) -> Ok
                constructs Ok
            let onCode (v) = Ok { size = String.length(v) }
            """;

    /** A line on the order, and a line on the length. */
    private static final String TWO_LINES = """
            module m

            data Ok
            data R = { s: String }
                invariant low  = s >= "m"
                invariant long = String.length(s) >= 3

            behavior f : (v: R) -> Ok
                constructs Ok
            let f (v) = Ok
            """;

    /** The same two rules, written the other way round. */
    private static final String TWO_LINES_TURNED = """
            module m

            data Ok
            data R = { s: String }
                invariant long = String.length(s) >= 3
                invariant low  = s >= "m"

            behavior f : (v: R) -> Ok
                constructs Ok
            let f (v) = Ok
            """;

    /**
     * The classes go to the value and the line to the length, and both are measured.
     *
     * <p>The classes here are classes of the strings and the line is on a count of characters.
     * Given one measure between them, the classes were said to be classes of the count — a value
     * where a number is owed, which is the pair a measure exists to keep together coming apart.
     */
    @Test
    void classesOnTheValueAndALineOnTheLengthAreTwoMeasures() {
        assertEquals(List.of("v: \"AB\", \"CD\"", "String.length(v): -"),
                measuredIn(VALUES_AND_A_LENGTH, "Code"),
                "the values divide the order and the length carries the line");
    }

    /** And a line on each number is two measures as well, neither of them given up. */
    @Test
    void aLineOnEachNumberIsTwoMeasures() {
        assertEquals(List.of("v.s: -", "String.length(v.s): -"), measuredIn(TWO_LINES, "R"));
    }

    /** Which does not turn on the order the author wrote the two rules in. */
    @Test
    void andTheOrderTheyAreWrittenInDoesNotDecideIt() {
        assertEquals(measuredIn(TWO_LINES, "R"), measuredIn(TWO_LINES_TURNED, "R"));
    }

    /**
     * What each shape of declaration comes to, over the shapes a string position can be in.
     *
     * <p>One place to read the whole answer from, because what is being held is how the evidence is
     * filed rather than any one case of it. A number nothing said anything about is not a measure
     * and is absent; a number two kinds of evidence reached is one measure carrying both.
     */
    @Test
    void whatEachShapeOfDeclarationComesTo() {
        assertEquals(List.of("v: \"AB\", \"CD\""), measuredIn(codeOf("""
                    invariant named = value == "AB" || value == "CD"
                """), "Code"), "values named on the order, and nothing about the length");
        assertEquals(List.of("v: -"), measuredIn(codeOf("""
                    invariant low = value >= "m"
                """), "Code"), "a line on the order, and nothing about the length");
        assertEquals(List.of("String.length(v): -"), measuredIn(codeOf("""
                    invariant long = String.length(value) >= 2
                """), "Code"), "a line on the length, and nothing about the order");
        assertEquals(List.of("v: \"AB\", \"CD\""), measuredIn(codeOf("""
                    invariant named = value == "AB" || value == "CD"
                    invariant low = value >= "A"
                """), "Code"), "both about the order, so one measure carries them");
        assertEquals(List.of("v: \"AB\", \"CD\"", "String.length(v): -"), measuredIn(codeOf("""
                    invariant named = value == "AB" || value == "CD"
                    invariant low = value >= "A"
                    invariant long = String.length(value) >= 2
                """), "Code"), "and a rule about the length is a measure beside them");
    }

    /** A {@code Code} declared with {@code rules}, in a module a behavior takes one of. */
    private static String codeOf(String rules) {
        return """
                module m

                data Code = String
                %s
                data Ok = { size: Int }

                behavior onCode : (v: Code) -> Ok
                    constructs Ok
                let onCode (v) = Ok { size = String.length(v) }
                """.formatted(rules);
    }

    /**
     * Neither rule is reported as one nothing could read.
     *
     * <p>Both were read to the end and each drew its line. A report naming one of them sends an
     * author to a clause there is nothing wrong with, and leaves them looking for the other clause
     * it was supposedly competing with.
     */
    @Test
    void neitherRuleIsLeftUnread() {
        assertEquals(List.of(), notRead(TWO_LINES), "both rules drew their own line");
        assertEquals(List.of(), notRead(VALUES_AND_A_LENGTH));
        assertTrue(!human(TWO_LINES).contains("not read: invariant R"), human(TWO_LINES));
    }

    /** How the reading measures every position of {@code model}: the number, and the classes on
     *  it. */
    private static List<String> measuredIn(String model, String parameter) {
        RuleReadingSource rules = RuleReadings.ofSource(model);
        Type type = Type.ref(TypeSymbols.declared(new TypeKey(rules.symbols().module(), parameter)));
        InputDomain read = InputDomain.of(
                List.of(new InputDomain.Parameter("v", null, type)),
                rules, ReadAs.THE_COMPILATION_DOES);
        List<String> out = new ArrayList<>();
        for (souther.compiler.inputs.Position at : read.positions()) {
            switch (LocalInspection.of(at, rules, ReadAs.THE_COMPILATION_DOES)) {
                case LocalPartition.Divided divided -> divided.measures().forEach(each ->
                        out.add(each.term() + ": " + labelled(each)));
                case LocalPartition.Open _ -> { }
            }
        }
        return out;
    }

    /** What a measure divides its number into, or a dash where it draws lines and no classes. */
    private static String labelled(DeclaredMeasure measure) {
        return measure.classes().isEmpty() ? "-"
                : measure.classes().stream().map(PartitionClass::label)
                        .reduce((a, b) -> a + ", " + b).orElseThrow();
    }

    private static List<String> notRead(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0)
                .partition().notRead().stream()
                .map(PartitionEvidence.NotRead::at).toList();
    }

    private static String human(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
