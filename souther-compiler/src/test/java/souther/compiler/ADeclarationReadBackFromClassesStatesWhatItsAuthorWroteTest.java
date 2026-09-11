package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A declaration reached through a dependency's published classes says what its author wrote, down
 * to the definitions the author never exposed.
 *
 * <p>The third of the three the spec puts on one footing: a type declared in this module, one
 * imported from a module compiled in the same run, and one imported from a module compiled
 * separately and reached through its classes all state the same invariant
 * (spec §invariant-discharge-representation). The first two are held next door; this is the one
 * where nothing of the library is in the room but its artifact.
 *
 * <p><b>The {@code let} is deliberately not exposed.</b> It is what the clause is composed out of,
 * and an importer has no name for it — so a reading that tried to work the clause out here could
 * not, however it went about it. What makes this work is not that the definition is importable but
 * that the artifact carries what the declaration cannot be read without, and the clause is expanded
 * where it was written. Exposed, the test would pass for a reason that says nothing: the consumer
 * would have a name for the value and a reading built the wrong way round would still reach it.
 *
 * <p>Held against the literal clause beside it rather than against a form of words. Both are read
 * to the end, so both come to the same measure; if the composed one is not read, its measure moves
 * and the literal one does not.
 */
class ADeclarationReadBackFromClassesStatesWhatItsAuthorWroteTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * The dependency, whose {@code exposing} names neither the value nor the helper its clauses are
     * written out of.
     *
     * <p>Both, because the population is both. A clause composes a value here and another calls a
     * helper, and the two are what a declaration can name of its own module; a test over one of them
     * holds the artifact to carrying one kind of thing.
     */
    private static final String LIBRARY = """
            module shared.codes exposing ( Composed, ThroughHelper, Written )

            let tail = "[0-9]{3}"

            let atLeastOne (s: String) = String.length(s) >= 1

            data Composed = String
                invariant String.matches("00" ++ tail, value)

            data ThroughHelper = String
                invariant atLeastOne(value)

            data Written = String
                invariant String.matches("00[0-9]{3}", value)
            """;

    /** The consumer, which has the dependency's classes and none of its source. */
    private static final String CONSUMER = """
            module app.intake exposing ( onComposed, onThroughHelper, onWritten )

            import shared.codes ( Composed, ThroughHelper, Written )

            data Yes
            data No

            behavior onComposed : (i: Composed) -> Yes | No
            let onComposed (i) = Yes

            example onComposed
                | "one" : (Composed("00123")) -> Yes

            behavior onThroughHelper : (i: ThroughHelper) -> Yes | No
            let onThroughHelper (i) = Yes

            example onThroughHelper
                | "one" : (ThroughHelper("a")) -> Yes

            behavior onWritten : (i: Written) -> Yes | No
            let onWritten (i) = Yes

            example onWritten
                | "one" : (Written("00123")) -> Yes
            """;

    /** How far the rules about each behavior's position were read, in the consumer's own report. */
    private static Map<String, JsonNode> readInTheConsumer() {
        Map<String, ClassFileImage> published = Compiler.compile(LIBRARY);
        Compilation compilation =
                Compilation.ofSource(CONSUMER, "Main", ModulePath.of(published));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode document = JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (JsonNode module : document.get("modules")) {
            for (JsonNode behavior : module.path("behaviors")) {
                out.put(behavior.path("name").asString(),
                        behavior.path("partition").path("axesMeasure"));
            }
        }
        return out;
    }

    @Test
    void aClauseComposedFromAnUnexposedValueIsReadAsFarAsOneWrittenOut() {
        Map<String, JsonNode> read = readInTheConsumer();
        assertNotNull(read.get("onComposed"), "the consumer measures the composed clause");
        assertNotNull(read.get("onWritten"), "and the written one beside it");

        assertEquals(read.get("onWritten"), read.get("onComposed"),
                "a clause composed from a value the dependency never exposed is read here as far"
                        + " as one written out, because the artifact carries what the declaration"
                        + " cannot be read without and the clause is expanded where it was written");
    }

    /**
     * The clause calling an unexposed helper reaches the same outcome here as in its own module,
     * and it is the outcome a clause read to the end gets.
     *
     * <p>Two halves, and both are needed. That it agrees with the module that wrote it is the claim;
     * that the outcome is the readable one is what says the agreement is about something — two
     * readings that both stopped agree perfectly. The second half is what a helper's clause being
     * read as the rule it states settles (ADR-0106); before that, this held the two apart.
     */
    @Test
    void aClauseCallingAnUnexposedHelperIsReadHereAsInItsOwnModule() {
        Map<String, JsonNode> here = readInTheConsumer();
        assertNotNull(here.get("onThroughHelper"), "the consumer measures it");

        assertEquals(inTheLibrarysOwnBuild("throughHelper"), here.get("onThroughHelper"),
                "a clause calling a helper the dependency never exposed reaches from here exactly"
                        + " what it reaches in the module that wrote it");
        assertEquals(here.get("onWritten"), here.get("onThroughHelper"),
                "and that outcome is the one a clause read to the end gets, so the agreement above"
                        + " is not two readings that both said nothing");
    }

    /**
     * The same measure taken in the library's own compile, where the helper is in scope.
     *
     * <p>What the consumer's answer is held against. Written as a compile of the library beside a
     * behavior over the declaration, because the claim is about two readings of one declaration and
     * one of them has to come from the module that wrote it.
     */
    private static JsonNode inTheLibrarysOwnBuild(String behavior) {
        Compilation compilation = Compilation.ofSource(LIBRARY.replace(
                "module shared.codes exposing ( Composed, ThroughHelper, Written )",
                "module shared.codes exposing ( Composed, ThroughHelper, Written, Yes, No,"
                        + " throughHelper )") + """

                data Yes
                data No

                behavior throughHelper : (i: ThroughHelper) -> Yes | No
                let throughHelper (i) = Yes

                example throughHelper
                    | "one" : (ThroughHelper("a")) -> Yes
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode document = JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        for (JsonNode module : document.get("modules")) {
            for (JsonNode each : module.path("behaviors")) {
                if (behavior.equals(each.path("name").asString())) {
                    return each.path("partition").path("axesMeasure");
                }
            }
        }
        throw new AssertionError("the library's own build measures `" + behavior + "`");
    }

    /** And nothing of the dependency was rebuilt here to make that true. */
    @Test
    void theConsumerEmitsNothingOfTheDependency() {
        Map<String, ClassFileImage> app = Compiler.compileModules(
                java.util.List.of(CONSUMER), ModulePath.of(Compiler.compile(LIBRARY)));

        assertEquals(java.util.List.of(),
                app.keySet().stream().filter(each -> each.startsWith("shared.codes")).toList(),
                "the dependency's classes are its own build's");
    }
}
