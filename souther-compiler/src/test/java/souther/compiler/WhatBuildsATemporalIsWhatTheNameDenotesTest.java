package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.runtime.Behavior;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which applications build a temporal, and which only answer one.
 *
 * <p>{@code Date("2026-01-01")} builds a date, and the checker settles that where it resolves the
 * name: the callee denotes the library namespace, which constructs. Nothing else does — a behavior a
 * model declares of its own named {@code Date} is a behavior, and a behavior answering a {@code Date}
 * over a written string is a behavior too, whatever it is spelled and whatever it answers.
 *
 * <p>Readers downstream of that used to decide it for themselves, and each took what was in reach:
 * the emitter compared the callee's spelling against the four temporals, and the readings that draw
 * lines read the type the call answered. So a model's own {@code Date} was compiled as
 * {@code LocalDate.parse} with its injected implementation never called, and a line was drawn at a
 * date wherever a behavior answering one was applied to a literal. What the source wrote is a value
 * of its own now, so none of them has anything left to decide.
 */
class WhatBuildsATemporalIsWhatTheNameDenotesTest {

    /** The library's namespace applied: this one builds. */
    private static final String CONSTRUCTED = """
            module demo

            data Stale
            data Fresh

            behavior freshness : (on: Date) -> Stale | Fresh
            let freshness (on) = if on < Date("2026-01-01") then Stale else Fresh
            """;

    /** A behavior of the model's own, spelled like the namespace. */
    private static final String SPELLED_THE_SAME = """
            module demo

            behavior Date : (s: String) -> String

            behavior g : (n: Int) -> String
                depends on Date

            let g (n, Date) = Date("2026-08-01")
            """;

    /** A behavior answering a temporal, applied to a written string. */
    private static final String ANSWERS_ONE = """
            module demo

            data Stale
            data Fresh

            behavior asOf : (s: String) -> Date

            behavior freshness : (on: Date) -> Stale | Fresh
                depends on asOf
            let freshness (on, asOf) = if on < asOf("2026-01-01") then Stale else Fresh
            """;

    private static List<Core> nodes(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(),
                compilation.db().allReports().stream().map(Object::toString).toList(),
                "the model compiles");
        List<Core> out = new ArrayList<>();
        collect(compilation.db().ask(new Bodies.CheckedBehavior("demo", behavior)).value().body(), out);
        return out;
    }

    private static void collect(Core e, List<Core> out) {
        if (e == null) {
            return;
        }
        out.add(e);
        Core.forEachChild(e, child -> collect(child, out));
    }

    private static <T> T only(List<Core> nodes, Class<T> kind) {
        List<Core> found = nodes.stream().filter(kind::isInstance).toList();
        assertEquals(1, found.size(), () -> "one " + kind.getSimpleName() + " in " + nodes);
        return kind.cast(found.get(0));
    }

    /** The namespace applied is the value it builds, with the text the source wrote on it. */
    @Test
    void theLibraryNamespaceAppliedIsAWrittenTemporal() {
        Core.Temporal written = only(nodes(CONSTRUCTED, "freshness"), Core.Temporal.class);

        assertEquals(souther.compiler.types.Type.Prim.DATE, written.kind());
        assertEquals("2026-01-01", written.text());
    }

    /** A behavior of the model's own is a call, however it is spelled. */
    @Test
    void aBehaviorSpelledLikeTheNamespaceIsACall() {
        List<Core> nodes = nodes(SPELLED_THE_SAME, "g");

        assertTrue(nodes.stream().noneMatch(Core.Temporal.class::isInstance), nodes.toString());
        Core.Call call = only(nodes, Core.Call.class);
        assertInstanceOf(souther.compiler.types.ValueName.Behavior.class,
                assertInstanceOf(Core.Reached.class, call.fn()).denotes());
    }

    /** And it is what runs: the implementation the model was handed, not a parse of the text. */
    @Test
    void thatBehaviorsOwnImplementationIsWhatRuns() throws Exception {
        BytesClassLoader loader =
                new BytesClassLoader(Compiler.compile(SPELLED_THE_SAME), getClass().getClassLoader());
        Behavior<Object, Object> supplied = s -> "supplied " + s;

        Object g = Emitted.behavior(loader, "demo", "g")
                .getConstructors()[0].newInstance(supplied);

        assertEquals("supplied 2026-08-01", Codecs.apply(g, 1L));
    }

    /** A behavior answering a temporal is a call too, and answers no place a line could be at. */
    @Test
    void aBehaviorAnsweringATemporalIsACall() {
        List<Core> nodes = nodes(ANSWERS_ONE, "freshness");

        assertTrue(nodes.stream().noneMatch(Core.Temporal.class::isInstance), nodes.toString());
        Core.Call call = only(nodes, Core.Call.class);
        assertInstanceOf(souther.compiler.types.ValueName.Behavior.class,
                assertInstanceOf(Core.Reached.class, call.fn()).denotes());
    }

    /** The measurement says the same: a line stands where the source wrote a date, and nowhere
     *  a behavior was asked for one. */
    @Test
    void aLineIsDrawnOnlyWhereADateIsWritten() {
        assertTrue(report(CONSTRUCTED).contains("border      borders 1   obligations 0/4"),
                report(CONSTRUCTED));
        assertFalse(report(ANSWERS_ONE).contains("obligations 0/4"), report(ANSWERS_ONE));
        assertTrue(report(ANSWERS_ONE).contains(
                        "written in a form this compiler does not read, about `on`"),
                report(ANSWERS_ONE));
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
