package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two positions of one behavior divided into classes of the same name, told apart.
 *
 * <p>A class is a class <em>of</em> something, and a finding that names only the class is the same
 * words about both of them. A reader is told to write a row and not where to write it; a consumer
 * joining the finding to the axis it came from has two axes whose {@code classes} both hold the
 * name. The document already said this of an input's cases — one carries {@code (in #1)} because two
 * parameters of one type give two findings a case name cannot tell apart — and did not say it of the
 * axes, which is the same fact one position over.
 */
class AClassNoRowIsInNamesThePositionItIsAClassOfTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One behavior, two positions of one type, one case reached at neither.
     *
     * <p>The two axes are as alike as the language lets them be: the same classes, uncovered in the
     * same class, differing in nothing but which position they are of. Anything a report says that
     * tells the two findings apart is saying the position, because there is nothing else left.
     */
    private static final String TWO_POSITIONS = """
            module m

            data Yes
            data No
            data Flag = Yes | No
            data Res = { n: Int }

            behavior both : (left: Flag, right: Flag) -> Res
                constructs Res

            let both (left, right) = Res { n = 1 }

            example both
                | "both say yes" : (Yes, Yes) -> Res { n = 1 }
            """;

    /** The human report writes the position beside the class. */
    @Test
    void theReportSaysWhichPositionEachClassIsOf() {
        String human = report().human(SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertEquals(List.of("      · no row is in `No` at left",
                        "      · no row is in `No` at right"),
                lines(human, "no row is in"), human);
    }

    /**
     * And the document, in the field a consumer joins on.
     *
     * <p>Two entries and not one repeated. Written as the class alone these were the same string
     * twice, and a consumer holding the pair could not say which axis's {@code classes} either of
     * them was one of — which is a document that has published a finding it cannot be asked about.
     */
    @Test
    void theDocumentTellsTheTwoFindingsApart() {
        JsonNode findings = JSON.readTree(report().json(SourceRendering.namedByIdentity(SourceLayouts.NONE)))
                .get("modules").get(0).get("behaviors").get(0).get("findings");

        assertEquals(List.of("No (at left)", "No (at right)"),
                subjects(findings, "axis_class_uncovered"), findings.toString());
    }

    /**
     * The names the two axes go by, which is what the subjects are held against.
     *
     * <p>Read off the axes rather than spelled here, so that a position renamed in the document and
     * a position named in a finding cannot come apart without this failing.
     */
    @Test
    void thePositionsNamedAreTheOnesTheAxesPublish() {
        JsonNode behavior = JSON.readTree(report().json(SourceRendering.namedByIdentity(SourceLayouts.NONE)))
                .get("modules").get(0).get("behaviors").get(0);

        List<String> paths = new ArrayList<>();
        for (JsonNode axis : behavior.get("partition").get("axes")) {
            paths.add(axis.get("path").asString());
        }
        assertEquals(List.of("left", "right"), paths, behavior.toString());
    }

    private static AdequacyReport report() {
        Compilation compilation = Compilation.ofSource(TWO_POSITIONS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    private static List<String> lines(String human, String saying) {
        return human.lines().filter(line -> line.contains(saying)).toList();
    }

    private static List<String> subjects(JsonNode findings, String kind) {
        List<String> out = new ArrayList<>();
        for (JsonNode each : findings) {
            if (kind.equals(each.get("kind").asString())) {
                out.add(each.get("subject").asString());
            }
        }
        return out;
    }
}
