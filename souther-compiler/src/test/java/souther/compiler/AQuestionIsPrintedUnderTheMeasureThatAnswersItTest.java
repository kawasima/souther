package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A question stands under the heading of the measure that answers it.
 *
 * <p>Two questions, two measures. Which values may stand at a position is what equivalence
 * partitions are made of; where the values stop is a border. #869 gave them separate headings and
 * separate counts, and a rule nothing accounted for is reported under one of them — a line printed
 * beside the classes sends an author looking for a class, two headings away from the border it is
 * about, which is the shape of issue #842.
 *
 * <p>Not read off the borders that came back either. The line below is exactly the one this compiler
 * could not fold, so there is no border to walk; that is when the question stands.
 */
class AQuestionIsPrintedUnderTheMeasureThatAnswersItTest {

    /** A bound this reads, a line it does not, and a rule the reading of values has no word for. */
    private static final String MODEL = """
            module example.rooms

            data Length = Int
                invariant min = value >= 1
                invariant max = value <= 10 * 2
                invariant square = value * value >= 4

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 5 then 1 else 2

            example price
                | "one" : (Length(5)) -> 1
            """;

    private static String report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /** The lines of the section a heading opens, up to the next heading. */
    private static String section(String report, String heading) {
        StringBuilder out = new StringBuilder();
        boolean inside = false;
        for (String line : report.split("\n", -1)) {
            if (line.startsWith("    ") && !line.startsWith("      ")) {
                inside = line.startsWith("    " + heading);
            }
            if (inside) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /** The rule about which values may stand there, under the heading for how they are divided. */
    @Test
    void aQuestionAboutTheValuesIsUnderThePartitionHeading() {
        String partition = section(report(), "partition");

        assertTrue(partition.contains(
                        "not accounted for: invariant Length (square) — which values may stand at"),
                partition);
    }

    /** And the rule about where they stop, under the heading for the borders. */
    @Test
    void aQuestionAboutTheLineIsUnderTheBorderHeading() {
        String report = report();

        assertTrue(section(report, "border").contains(
                        "not accounted for: invariant Length (max) — where the values stop on"),
                section(report, "border"));
        // The question, and not every mention of the rule. The classes measure names the same rule
        // where it could not turn it into a line, which is a different thing said about it: what
        // this is about is where the question a rule raised is answered.
        assertTrue(!section(report, "partition")
                        .contains("not accounted for: invariant Length (max)"),
                "and not beside the classes, which is a different measure with a count of its own:\n"
                        + section(report, "partition"));
    }
}
