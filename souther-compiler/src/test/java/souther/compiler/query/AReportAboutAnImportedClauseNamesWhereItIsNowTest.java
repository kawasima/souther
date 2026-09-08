package souther.compiler.query;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.Located;
import souther.compiler.diag.Primary;
import souther.compiler.meta.ModulePath;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A report that names a clause of another module names where that clause is now.
 *
 * <p>A body constructing a value of an imported type is judged against the invariant that type
 * declares, and what it is told points at the clause — in the declaring module's source, not in its
 * own. So the reader is one module's and the place is another's, and nothing about the reader
 * changed when the clause moved.
 *
 * <p>Held against a compile of the same text rather than against a line number written down here. A
 * number would say where the clause is in this fixture and would be edited whenever the fixture was;
 * what is being asked is whether a store that absorbed the edit says what a store that never saw the
 * old text says, which is a question about the two and not about either.
 */
class AReportAboutAnImportedClauseNamesWhereItIsNowTest {

    private static final String DECLARING = """
            module limits exposing ( Small )

            data Small = String
                invariant String.length(value) <= 3
            """;

    /** Constructs the imported value, which is what makes its clause something to be judged by. */
    private static final String CONSTRUCTING = """
            module app

            import limits ( Small )

            behavior make : (s: String) -> Small
                constructs Small

            let make (s) = Small(s)
            """;

    /** The same, with the declaration a line further down. */
    private static final String MOVED = DECLARING.replace("data Small",
            "// what an author types while reading their own model back\ndata Small");

    private static Map<String, String> workspace(String declaring) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("limits.sou", declaring);
        byId.put("app.sou", CONSTRUCTING);
        return byId;
    }

    private static Compilation answered(Map<String, String> sources) {
        Compilation compilation = Compilation.ofDocuments(sources, Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        return compilation;
    }

    /**
     * Every place a report of one source points into another, as text.
     *
     * <p>Read off what a reader is sent to rather than off the message: the message says what the
     * finding is, and where it sends somebody is the thing that goes out of date.
     */
    private static List<String> placesElsewhere(Compilation compilation) {
        List<String> said = new ArrayList<>();
        for (Map.Entry<SourceId, List<Diagnostic>> at
                : Located.diagnosticsOf(compilation.diagnostics()).entrySet()) {
            for (Diagnostic diagnostic : at.getValue()) {
                List<DiagnosticPlace> places = new ArrayList<>();
                if (diagnostic.primary() instanceof Primary.InSource here) {
                    places.add(here.place());
                }
                diagnostic.secondary().forEach(label -> places.add(label.place()));
                for (DiagnosticPlace place : places) {
                    if (place instanceof DiagnosticPlace.InSource there
                            && !there.source().equals(at.getKey())) {
                        said.add(at.getKey() + " " + diagnostic.code() + " points at "
                                + there.source() + " " + there.region().start());
                    }
                }
            }
        }
        said.sort(null);
        return said;
    }

    /**
     * The fixture reports about the other module at all, and the edit moves what it points at.
     *
     * <p>Asked first and of two compiles, because the claim below is that two answers agree: two
     * that agreed by both saying nothing would pass it, and so would two that agreed because the
     * edit reached nothing.
     */
    @Test
    void theEditMovesWhatAReportOfTheOtherModulePointsAt() {
        List<String> before = placesElsewhere(answered(workspace(DECLARING)));
        List<String> after = placesElsewhere(answered(workspace(MOVED)));

        assertEquals(1, before.size(),
                () -> "one report of the constructing module points into the declaring one: "
                        + before);
        assertNotEquals(before, after, "and the edit moves the place it points at");
    }

    /** And a store that absorbed the edit says what a compile of the same text says. */
    @Test
    void aStoreThatAbsorbedTheEditSaysWhereTheClauseIsNow() {
        Compilation absorbed = answered(workspace(DECLARING));
        absorbed.update(workspace(MOVED), Set.of());
        absorbed.answerEverything();

        assertEquals(placesElsewhere(answered(workspace(MOVED))), placesElsewhere(absorbed),
                "the store names where the clause is, not where it was");
    }
}
