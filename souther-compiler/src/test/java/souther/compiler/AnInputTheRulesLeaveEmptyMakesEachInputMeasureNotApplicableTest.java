package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules leaving an input no value is proved once, and every measure of that input is
 * inapplicable for it.
 *
 * <p>Two things, and they are at different levels. The proof is one: two clauses each admitting
 * values are empty together, neither of them is the one that failed, and there is no position for it
 * to be about — so it is made where a behavior's parameters are held together, which is the only
 * place such a contradiction is visible at all. What a document carries is per measure: a partition
 * and a border each say they have no subject, in the word this proof spells.
 *
 * <p>So the count below is two and not one. A reading that answered per position or per rule would
 * say it four times or twice over the record here, and one that derived it per measure could let two
 * measures disagree about a model — the fixture has four positions and two clauses so that both of
 * those show.
 *
 * <p>Not a gap. A gap is a row nobody has written and somebody could; here no row exists to write,
 * so a strict build has nothing to refuse over and no author is being asked for anything.
 */
class AnInputTheRulesLeaveEmptyMakesEachInputMeasureNotApplicableTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Four positions and two clauses, and the emptiness is in neither clause alone.
     *
     * <p>{@code lo <= hi} and {@code hi < lo} each admit plenty. Together they admit nothing, and
     * the record has four fields for the answer to be said at if anything said it per position.
     */
    private static final String MODEL = """
            module m

            data Box =
                { lo: Int
                , hi: Int
                , name: String
                , tag: String
                }
                invariant ordered = lo <= hi
                invariant reversed = hi < lo

            data Ok

            behavior read : (b: Box) -> Ok
            let read (b) = Ok
            """;

    private record Measured(JsonNode behavior, String human) {}

    private static Measured measured() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        AdequacyReport report = AdequacyReport.of(compilation);
        JsonNode document = JSON.readTree(report.json(SourceRendering.namedByIdentity(compilation.texts())));
        return new Measured(document.get("modules").get(0).get("behaviors").get(0),
                report.human(SourceRendering.namedByIdentity(compilation.texts())));
    }

    /** The measures of that input are not applicable, and say so for the one reason. */
    @Test
    void everyMeasureOfTheInputReadsTheOneAnswer() {
        JsonNode partition = measured().behavior().path("partition");
        for (String measure : List.of("axesMeasure", "boundariesMeasure")) {
            assertEquals("no_feasible_input",
                    partition.path(measure).path("reason").asString(),
                    measure + " reads the behavior's own answer: " + partition.path(measure));
        }
    }

    /**
     * And the human report says it once per measure, not once per position.
     *
     * <p>The count is the assertion. Four positions and two clauses are what would make a reading
     * that answered per position or per rule say it four times or twice, and a reading that puts the
     * one proof where each measure says why it has no subject says it as many times as there are
     * such measures, whatever the record holds.
     *
     * <p>Counted in the sentence a person reads and not in the word a document writes. The two are
     * the same fact and they are not the same vocabulary: the line said {@code no_feasible_input}
     * while nothing in this report had written a sentence for the reason, which is a schema word
     * standing where an English clause belongs.
     */
    @Test
    void theReportSaysItOncePerMeasureAndNotOncePerPosition() {
        List<String> said = new ArrayList<>();
        measured().human().lines()
                .filter(line -> line.contains(
                        "the rules reaching this behavior's input leave it no value"))
                .forEach(said::add);
        assertEquals(2, said.size(),
                "one line per measure of the input, and no more: " + said);
    }

    /** And nothing is owed over it: no row exists to be missing. */
    @Test
    void nothingIsOwedOverAnInputThatHoldsNothing() {
        List<String> kinds = new ArrayList<>();
        measured().behavior().path("findings")
                .forEach(each -> kinds.add(each.path("kind").asString()));
        assertTrue(kinds.stream().noneMatch(kind -> kind.contains("row")),
                "a row nobody can write is not a row nobody has written: " + kinds);
    }
}
