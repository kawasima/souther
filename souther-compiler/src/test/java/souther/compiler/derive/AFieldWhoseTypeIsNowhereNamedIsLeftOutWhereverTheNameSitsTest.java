package souther.compiler.derive;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A product a field of which carries a type nobody could name is left out of the derived module,
 * wherever in the field's type the unnamed part sits.
 *
 * <p>The name was reported where it is written, so deriving says nothing more; what it does is
 * answer nothing about the declaration. That was true of a field whose whole type is the unnamed
 * one and not of {@code List<T>} with {@code T} unnamed: the walk that builds the representation
 * met the {@code ?} inside the list and raised the report for a type with no boundary
 * representation as an exception, through the store, out of the compilation. An author who breaks
 * the module a product imports from — a line that is not yet a comment — has the compiler throw
 * where it should report, and the store keeps nothing of the question that threw.
 */
class AFieldWhoseTypeIsNowhereNamedIsLeftOutWhereverTheNameSitsTest {

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
     *  appended, and answers every code reported. */
    private static List<String> codes(String upstream) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("upstream.sou", upstream);
        byId.put("downstream.sou", IMPORTING);
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        compilation.diagnostics();
        List<String> codes = new ArrayList<>();
        compilation.db().allReports()
                .forEach(found -> codes.add(found.report().diagnostic().code().toString()));
        return codes;
    }

    @Test
    void aBrokenImportIsReportedWhereItIsWrittenAndNowhereElse() {
        List<String> codes = codes(IMPORTED + "\n-- not a comment\n");
        assertFalse(codes.isEmpty(), "the line that does not parse is reported");
        assertFalse(codes.contains("E1311"),
                "the product whose field carries the unnamed type says nothing of its own: " + codes);
    }

    /** The negative control: as written, the pair compiles and reports nothing. */
    @Test
    void asWrittenNothingIsReported() {
        assertTrue(codes(IMPORTED).isEmpty(), codes(IMPORTED).toString());
    }
}
