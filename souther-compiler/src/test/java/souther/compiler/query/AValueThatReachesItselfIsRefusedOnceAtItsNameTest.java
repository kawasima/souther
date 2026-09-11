package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.diag.PhysicalRegion;
import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.Compiler;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value defined in terms of itself is refused once, by one owner, at the name it was declared under.
 *
 * <p>Which value reaches which is a fact about the module, asked and answered once. It used to be
 * worked out again by everything that built an expansion table — a body being lowered, a signature
 * being read, a module being checked — and each of those threw the refusal from wherever it happened
 * to be standing. So which question reported it, and how many of them did, was decided by the order
 * the questions happened to run in rather than by anything about the module.
 *
 * <p>Both halves are stated here because either can regress on its own. A second, unrelated mistake
 * must not change the first report: if the cycle is refused from inside a question the second mistake
 * also reaches, the two are answered as one and the reader is told about the wrong one.
 */
class AValueThatReachesItselfIsRefusedOnceAtItsNameTest {

    private static final String CYCLE = """
            module demo

            data In = { n: Int }
            data Out = { n: Int }

            let step = step

            behavior go : (i: In) -> Out
                constructs Out
            let go (i) = Out { n = i.n + step }
            """;

    /** The same module with a mistake of its own beside the cycle: a helper multiplying by a string. */
    private static final String CYCLE_AND_ONE_MORE = """
            module demo

            data In = { n: Int }
            data Out = { n: Int }

            let step = step

            let doubled (n: Int) : Int = n * "two"

            behavior go : (i: In) -> Out
                constructs Out
            let go (i) = Out { n = doubled(i.n) + step }
            """;

    private static List<Diagnostic> diagnose(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", source);
        return Located.diagnosticsOf(Compiler.diagnoseModules(byId, Set.of())).get(new SourceId("a.sou"));
    }

    private static Diagnostic cycleIn(List<Diagnostic> found) {
        List<Diagnostic> cycles = found.stream()
                .filter(d -> d.said() instanceof NameMessage.AValueReachesItself)
                .toList();
        assertEquals(1, cycles.size(), "one value reaching itself, one refusal: " + found);
        return cycles.get(0);
    }

    @Test
    void aValueReachingItselfIsOneDiagnostic() {
        List<Diagnostic> found = diagnose(CYCLE);

        assertEquals(1, found.size(), "one mistake, one report: " + found);
        assertInstanceOf(NameMessage.AValueReachesItself.class, found.get(0).said());
    }

    @Test
    void aSecondMistakeBesideItChangesNothingAboutTheFirst() {
        Diagnostic alone = cycleIn(diagnose(CYCLE));
        Diagnostic beside = cycleIn(diagnose(CYCLE_AND_ONE_MORE));

        assertEquals(alone.said(), beside.said());
        assertEquals(List.copyOf(alone.values().values()), List.copyOf(beside.values().values()));
        assertEquals(((Primary.InSource) alone.primary()).place().region(), ((Primary.InSource) beside.primary()).place().region(),
                "the refusal is about `step`, and where `step` is written did not move");
    }

    /**
     * A cycle is refused before anything about the module's bodies is worked out, and it stays one
     * report when it is.
     *
     * <p>It has to be refused first: a value is substituted at each of its references, so one that
     * reaches itself is substituted into itself and there is no body to reach the end of. Nothing
     * below can be said about a module with one, and the reader is told the one thing that can be.
     * What this pins is the count — a refusal thrown from wherever an expansion table happened to be
     * built is thrown once per table, and there are eleven of those.
     */
    @Test
    void nothingElseIsSaidAboutAModuleWithOne() {
        List<Diagnostic> found = diagnose(CYCLE_AND_ONE_MORE);

        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.AValueReachesItself),
                "the cycle: " + found);
        assertEquals(1, found.size(), "one refusal, said once: " + found);
    }

    /**
     * The underline covers the name and only the name. A refusal about {@code let step} is about
     * {@code step}; starting it at the {@code let} four columns earlier underlines a keyword the
     * author did not get wrong, and measuring it in the name rather than in what was written stops it
     * short wherever a spelling is wider than the name it denotes.
     */
    @Test
    void theRefusalUnderlinesTheNameAndNotTheKeywordInFrontOfIt() {
        PhysicalRegion region = WhereItSits.in(CYCLE,
                ((Primary.InSource) cycleIn(diagnose(CYCLE)).primary()).place().region());

        assertEquals(region.start().line(), region.end().line(), "a name is one line's worth");
        assertEquals("let step = step".indexOf("step") + 1, region.start().column(),
                "the underline starts at the name");
        assertEquals("step".length(), region.end().column() - region.start().column(),
                "the underline is as wide as the name");
    }
}
