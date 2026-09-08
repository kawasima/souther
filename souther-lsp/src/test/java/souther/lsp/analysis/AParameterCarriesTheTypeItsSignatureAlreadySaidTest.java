package souther.lsp.analysis;

import org.junit.jupiter.api.Test;
import souther.lsp.protocol.InlayHint;
import souther.lsp.protocol.Position;
import souther.lsp.protocol.Range;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A signature is written once, on the {@code behavior} line, and the implementation repeats none of
 * it: {@code let submit (request, submittedAt) = ...}. The line that says what those are may be far
 * up the file or in another one, so it is carried to where the author is working. Nothing here is
 * inferred — it is the declaration they already wrote.
 */
class AParameterCarriesTheTypeItsSignatureAlreadySaidTest {

    private static final String URI = "file:///m.sou";

    private static final String MODEL = """
            module m

            data Draft = { plannedCost: Int }
            data Stamp = { at: Int }
                invariant positive = at > 0

            behavior submit : (request: Draft, submittedAt: Stamp) -> Int
            let submit (request, submittedAt) = request.plannedCost
            """;

    @Test
    void eachParameterIsShownTheTypeTheSignatureSaidArrives() {
        assertEquals(List.of(": Draft", ": Stamp"), labelsOf(hints(MODEL)),
                "the `let` names them and the `behavior` line says what they are");
    }

    @Test
    void aHintStandsAfterTheNameItIsAbout() {
        List<InlayHint> hints = hints(MODEL);
        String letLine = MODEL.lines().toList().get(7);

        assertEquals(letLine.indexOf("request") + "request".length(),
                hints.getFirst().position().character(),
                "the hint goes where a type annotation would have been written");
        assertEquals(7, hints.getFirst().position().line());
    }

    /** A type held to a rule of its own is not any record of those fields, and that is what a reader
     *  asks for rather than what they take in without looking. */
    @Test
    void thatATypeIsHeldToARuleIsThereToBeAskedFor() {
        List<InlayHint> hints = hints(MODEL);

        assertNull(hints.getFirst().tooltip(), "`Draft` states nothing of its own");
        assertNotNull(hints.get(1).tooltip(), "`Stamp` has an invariant");
        assertTrue(hints.get(1).tooltip().contains("Stamp"));
    }

    /**
     * And a rule reaches a type through a spread.
     *
     * <p>A spread flattens the fields of what it brings in and inherits its invariants with them, so
     * a data that writes no clause of its own is held to whatever it spread in. A reading that
     * looked at the clauses written on the declaration would say a value of it is held to nothing
     * while the compiler refuses one that breaks the rule.
     */
    @Test
    void aRuleInheritedThroughASpreadIsOneTheTypeIsHeldTo() {
        List<InlayHint> hints = hints("""
                module m

                data Positive = { value: Int }
                    invariant positive = value > 0

                data Amount = { ...Positive }

                behavior use : (amount: Amount) -> Int
                let use (amount) = amount.value
                """);

        assertEquals(List.of(": Amount"), labelsOf(hints));
        assertNotNull(hints.getFirst().tooltip(),
                "`Amount` writes no clause of its own and is held to `Positive`'s");
    }

    /**
     * And the hints stand while the body does not.
     *
     * <p>What arrives is the signature's to say. A body that will not check is a body, and a reader
     * working on it is the reader who most needs to be told what they were given.
     */
    @Test
    void aBodyThatDoesNotCheckStillHasItsParametersNamed() {
        String broken = """
                module m

                data Draft = { plannedCost: Int }

                behavior submit : (request: Draft) -> Int
                let submit (request) = request.plannedCost + "not a number"
                """;

        assertEquals(List.of(": Draft"), labelsOf(hints(broken)));
    }

    /**
     * And a behavior that is handed what it depends on is hinted like any other.
     *
     * <p>Its {@code let} takes those behaviors beside the inputs, so it always writes more
     * parameters than the signature has input types. Told apart by comparing those two lengths, the
     * whole definition was left out — including the inputs the signature does declare, which are the
     * ones a hint exists for.
     */
    @Test
    void aBehaviorThatIsHandedWhatItDependsOnHasItsInputsHinted() {
        assertEquals(List.of(": Draft"), labelsOf(hints("""
                module m

                data Draft = { plannedCost: Int }

                behavior price : (request: Draft) -> Int

                behavior submit : (request: Draft) -> Int
                    depends on price

                let submit (request, price) = price(request)
                """)),
                "`request` is what the signature types; `price` is a behavior it was handed");
    }

    @Test
    void onlyWhatTheClientAskedToSee() {
        Range firstLineOnly = new Range(new Position(0, 0), new Position(6, 0));

        assertTrue(hints(MODEL, firstLineOnly).isEmpty(),
                "the `let` is below the range, so nothing is drawn in it");
    }

    private static List<String> labelsOf(List<InlayHint> hints) {
        List<String> labels = new ArrayList<>();
        for (InlayHint hint : hints) {
            labels.add(hint.label());
        }
        return labels;
    }

    private static List<InlayHint> hints(String text) {
        return hints(text, new Range(new Position(0, 0), new Position(999, 0)));
    }

    private static List<InlayHint> hints(String text, Range within) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(URI, text);
        ModuleGraph graph = ModuleGraph.of(sources);
        Analyzer analyzer = new Analyzer();
        analyzer.diagnostics(graph);
        return analyzer.inlayHints(URI, within, graph);
    }
}
