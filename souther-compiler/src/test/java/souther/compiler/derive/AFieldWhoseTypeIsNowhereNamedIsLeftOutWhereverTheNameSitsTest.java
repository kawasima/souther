package souther.compiler.derive;

import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Located;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A product a field of which carries a type nobody could name is left out of the derived module,
 * wherever in the field's type the unnamed part sits — and only that leaf is left unsaid.
 *
 * <p>The name was reported where it is written, so deriving says nothing more about it; what it
 * does is answer nothing about the declaration. That was true of a field whose whole type is the
 * unnamed one and not of {@code List<T>} with {@code T} unnamed: the walk that builds the
 * representation met the {@code ?} inside the list and raised the report for a type with no
 * boundary representation, through the store, out of the compilation. An author who breaks the
 * module a product imports from — a line that is not yet a comment — had the compiler throw where
 * it should report.
 *
 * <p>What is absorbed is the one report there would be about the unnamed type itself. What stands
 * outside it in the type is refused as it is refused for any other type: the walk meets the
 * unnamed leaf where it sits, so a tuple around it is refused as a tuple and a map keyed by an
 * {@code Int} for its key, before the leaf is reached. A scan for the unnamed type ahead of the
 * walk would have silenced those too, and they are not the same thing said twice.
 */
class AFieldWhoseTypeIsNowhereNamedIsLeftOutWhereverTheNameSitsTest {

    private static final SourceId UPSTREAM = new SourceId("upstream.sou");
    private static final SourceId DOWNSTREAM = new SourceId("downstream.sou");

    private static final String IMPORTED = """
            module upstream exposing ( Reason )
            data Reason = { text: String }
            """;

    private static final String IMPORTING = """
            module downstream
            import upstream ( Reason )
            data Refused = { reasons: List<Reason> }
            """;

    /** Compiles both, with {@code upstream} as written or with a line that does not parse
     *  appended, and answers what each document was told. */
    private static Map<SourceId, List<Located>> diagnostics(String upstream) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(UPSTREAM.toString(), upstream);
        byId.put(DOWNSTREAM.toString(), IMPORTING);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY).diagnostics();
    }

    /**
     * The document that does not parse is told so. The one importing it is told that its import
     * reaches no module — which is about the line it wrote — and nothing else: not the product,
     * whose field came to carry the unnamed type through that import.
     */
    @Test
    void aBrokenImportIsReportedWhereItIsWrittenAndTheProductSaysNothing() {
        Map<SourceId, List<Located>> told = diagnostics(IMPORTED + "\n-- not a comment\n");
        assertFalse(told.get(UPSTREAM).isEmpty(), "the line that does not parse is reported");
        assertEquals(List.of("E1504"), codes(told.get(DOWNSTREAM)),
                "the importing document is told of its import and of nothing else");
    }

    private static List<String> codes(List<Located> told) {
        return told.stream().map(each -> each.diagnostic().code().toString()).toList();
    }

    /** The negative control: as written, the pair compiles and neither document is told anything. */
    @Test
    void asWrittenNothingIsReported() {
        Map<SourceId, List<Located>> told = diagnostics(IMPORTED);
        assertEquals(List.of(), told.get(UPSTREAM));
        assertEquals(List.of(), told.get(DOWNSTREAM));
    }

    /**
     * Wherever the unnamed part sits under a position the boundary admits, the product says nothing
     * of its own: the unnamed name is reported once, where it is written, and that is all.
     */
    @Test
    void underAnAdmittedPositionOnlyTheNameIsReported() {
        for (String type : List.of("Missing", "List<Missing>", "Set<Missing>", "Option<Missing>",
                "Map<String, Missing>", "List<Option<Missing>>")) {
            Map<String, String> byId = new LinkedHashMap<>();
            byId.put("demo.sou", "module demo\ndata X = { f: " + type + " }\n");
            List<String> codes = codes(Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                    .diagnostics().get(new SourceId("demo.sou")));
            assertEquals(List.of("E1023"), codes, type + " is told " + codes);
        }
    }

    /**
     * What stands outside the unnamed part is refused as it would be for any type there: the walk
     * reaches the tuple and the map's key before the leaf. These are the reports the pre-walk scan
     * would have lost, and each is about something other than the name.
     */
    @Test
    void whatStandsOutsideTheUnnamedPartIsStillRefused() {
        assertTrue(refused("(Missing, Int)").contains("E1311"),
                "a tuple around the unnamed type is refused as a tuple");
        assertTrue(refused("Map<Int, Missing>").contains("E1314"),
                "a map keyed by an Int around the unnamed type is refused for its key");
    }

    private static String refused(String type) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(
                "module demo\ndata X = { f: " + type + " }\n"));
        return e.getMessage();
    }
}
