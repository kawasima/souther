package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import org.junit.jupiter.api.Test;

import souther.compiler.Compiler;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.Located;
import souther.compiler.diag.Messages;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.diag.DeclaringCode;
import souther.compiler.diag.Placement;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.WrittenAtMessage;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One helper, moved between modules, and what a report about the code inside it does with the move.
 *
 * <p>The helper builds a data, and an invariant that calls it is refused for reaching a construction.
 * Which module the helper is declared in is not part of that rule, so what the report says about it
 * must not turn on the move either — and it did: a helper of another module was treated as shipped
 * source and reported at the call, with the caret sized for a construction in a file the caret was
 * not in.
 *
 * <p>What the move may change is one thing only: whether this compile can show the reader the place.
 * A module compiled alongside is a file the reader holds; one read back off the module path is not,
 * and there the report says where the code is rather than pointing somewhere it is not.
 */
class AHelperSaysTheSameThingWhereverItIsDeclaredTest {

    /** The helper, and the construction inside it a report has to send the reader to. */
    private static final String HELPER =
            "let atLeastZero (x: Int): Bool = Yen(0).value <= x";

    private static final String ONE_MODULE = """
            module m
            data Yen = Int invariant value >= 0
            %s
            data Table = Int
                invariant ok = atLeastZero(value)
            """.formatted(HELPER);

    private static final String DECLARING = """
            module up exposing ( Yen, atLeastZero )
            data Yen = Int invariant value >= 0
            %s
            """.formatted(HELPER);

    private static final String CALLING = """
            module down
            import up ( atLeastZero )
            data Table = Int
                invariant ok = atLeastZero(value)
            """;

    // --- the two spellings a compile can show both halves of -----------------------------------

    @Test
    void aHelperOfThisModuleIsReportedAtTheConstruction() {
        Diagnostic one = only(diagnosticsOf(Map.of("m.sou", ONE_MODULE)), "m.sou");

        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, one.said());
        assertEquals("Yen", quoted(ONE_MODULE, ((Primary.InSource) one.primary()).place().region()),
                "the caret is on the construction, which is what the rule is about");
        assertFalse(((Primary.InSource) one.primary()).place().region().start().wasCopiedHere(),
                "the construction is in a file this compile has, so the caret is at the code");
    }

    /**
     * The same helper, declared next door. The report is the one above with the construction in
     * another file, which is where the reader has to go.
     */
    @Test
    void aHelperOfAnotherModuleCompiledAlongsideIsReportedAtTheConstructionToo() {
        Diagnostic one = only(diagnosticsOf(Map.of("up.sou", DECLARING, "down.sou", CALLING)),
                "down.sou");

        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, one.said());
        assertEquals("Yen", quoted(DECLARING, ((Primary.InSource) one.primary()).place().region()),
                "the caret is on the construction, in the file the helper is written in");
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(new SourceId("up.sou")),
                ((Primary.InSource) one.primary()).place().region().start().quotedFrom());
        assertFalse(((Primary.InSource) one.primary()).place().region().start().wasCopiedHere());
    }

    /**
     * And the clause is labelled in both, because what makes the construction wrong is written where
     * the data is and not where the helper is.
     */
    @Test
    void theClauseThatReachesItIsLabelledWhicheverModuleTheHelperIsIn() {
        LabeledRegion own = onlyLabel(only(diagnosticsOf(Map.of("m.sou", ONE_MODULE)), "m.sou"));
        LabeledRegion across = onlyLabel(
                only(diagnosticsOf(Map.of("up.sou", DECLARING, "down.sou", CALLING)), "down.sou"));

        assertInstanceOf(InvariantMessage.TheClauseReachesThatConstruction.class, own.said());
        assertInstanceOf(InvariantMessage.TheClauseReachesThatConstruction.class, across.said());
        assertEquals("invariant ok = atLeastZero(value)", quoted(ONE_MODULE, ((souther.compiler.diag.DiagnosticPlace.InSource) own.place()).region()));
        assertEquals("invariant ok = atLeastZero(value)", quoted(CALLING, ((souther.compiler.diag.DiagnosticPlace.InSource) across.place()).region()));
    }

    /**
     * And both files are told, because the problem is written in both. The construction is in
     * {@code up} and what forbids it is in {@code down}, and neither reads as the whole of it: an
     * author sent only to {@code up} is looking at a helper that is fine to call from anywhere else,
     * and an author sent only to {@code down} is looking at a call that constructs nothing.
     *
     * <p>Said once each, anchored at what that file holds. The entry carries the source its primary
     * region is in whichever file it was published to, which is what a reader turns into a caret in
     * one file and a link into the other.
     */
    @Test
    void bothFilesAreToldAndNeitherIsSilent() {
        Map<SourceId, List<Located>> byFile = diagnosticsOf(Map.of("up.sou", DECLARING,
                "down.sou", CALLING));

        assertEquals(1, byFile.get(new SourceId("up.sou")).size(), () -> "" + byFile.get(new SourceId("up.sou")));
        assertEquals(1, byFile.get(new SourceId("down.sou")).size(), () -> "" + byFile.get(new SourceId("down.sou")));
        assertEquals(new SourceId("up.sou"), byFile.get(new SourceId("down.sou")).get(0).context().filedUnder().orElse(null),
                "the caret is on the construction wherever the report is published");
        assertEquals(byFile.get(new SourceId("up.sou")).get(0).diagnostic().identity(),
                byFile.get(new SourceId("down.sou")).get(0).diagnostic().identity(),
                "one problem, said in the two files it is written in");
    }

    // --- the spelling a compile can show only one half of --------------------------------------

    /**
     * The helper read back off the module path. Its body is spliced in the same way and the rule is
     * broken in the same way; what is different is that this compile has no source to quote it from,
     * so the caret is where the code was reached from — and says so, rather than reading as the place
     * the construction is written.
     */
    @Test
    void aHelperReadOffTheModulePathIsSaidToBeOutOfSight() {
        Diagnostic one = only(diagnosticsOf(Map.of("down.sou", CALLING), published(DECLARING)),
                "down.sou");

        assertInstanceOf(InvariantMessage.TheNamedClauseConstructsAData.class, one.said(),
                "the rule broken is the same rule");
        assertEquals(Placement.aFileOfThisCompile(new SourceId("down.sou")).at(1, 1)
                        .standingInFor(new DeclaringCode(
                                new SourceProvenance.APublishedModule("up", "up.atLeastZero")))
                        .placement(),
                ((Primary.InSource) one.primary()).place().region().start().placement(),
                "the caret is in the file the reader is compiling, since there is no other, and it"
                        + " says the code it names is written where that file cannot show");
    }

    /** And it claims no width there. The width a report about a construction measures is the
     *  construction's, and the caret is on a call whose text is its own length — three columns sized
     *  for `Yen` landing on `atL`. */
    @Test
    void aCaretThatOnlyStandsInClaimsNoWidth() {
        Diagnostic one = only(diagnosticsOf(Map.of("down.sou", CALLING), published(DECLARING)),
                "down.sou");

        assertEquals(((Primary.InSource) one.primary()).place().region().start(), ((Primary.InSource) one.primary()).place().region().end(),
                "a stand-in points, it does not underline text it did not measure");
    }

    /** And every reader is told, because the sentence is said off the caret rather than by the site
     *  that reported the rule. */
    @Test
    void whatTheCaretStandsInForIsInTheBodyEveryRendererReads() {
        Diagnostic one = only(diagnosticsOf(Map.of("down.sou", CALLING), published(DECLARING)),
                "down.sou");

        String body = DiagnosticRenderer.body(one, Locale.ENGLISH);
        String about = Messages.render(
                new WrittenAtMessage.TheCodeIsWrittenOutOfSight("up.atLeastZero"), Locale.ENGLISH);
        assertTrue(body.contains(about), () -> body);
    }

    /** The one it is held against: with the source there, nothing is said about sight, because there
     *  is nothing to say. */
    @Test
    void nothingIsSaidAboutSightWhereTheSourceIsThere() {
        Diagnostic one = only(diagnosticsOf(Map.of("up.sou", DECLARING, "down.sou", CALLING)),
                "down.sou");

        String body = DiagnosticRenderer.body(one, Locale.ENGLISH);
        String about = Messages.render(
                new WrittenAtMessage.TheCodeIsWrittenOutOfSight("up.atLeastZero"), Locale.ENGLISH);
        assertTrue(!body.contains(about), () -> body);
    }

    // --- the fixtures --------------------------------------------------------------------------

    private static Map<SourceId, List<Located>> diagnosticsOf(Map<String, String> sources) {
        return diagnosticsOf(sources, ModulePath.EMPTY);
    }

    private static Map<SourceId, List<Located>> diagnosticsOf(Map<String, String> sources,
                                                            ModulePath path) {
        return Compiler.diagnoseModules(sources, Set.of(), path);
    }

    /** The declaring module as another project built it: classes alone, no source. */
    private static ModulePath published(String source) {
        Map<String, ClassFileImage> classes = Compiler.compileModules(List.of(source));
        return ModulePath.of(classes);
    }

    private static Diagnostic only(Map<SourceId, List<Located>> byFile, String sourceId) {
        List<Located> here = byFile.get(new SourceId(sourceId));
        assertNotNull(here, () -> "no entry for " + sourceId + " in " + byFile.keySet());
        assertEquals(1, here.size(), () -> "expected one diagnostic, got " + here);
        return here.get(0).diagnostic();
    }

    private static LabeledRegion onlyLabel(Diagnostic d) {
        assertEquals(1, d.secondary().size(), () -> "expected one label, got " + d.secondary());
        return d.secondary().get(0);
    }

    /** The characters {@code at} covers, cut out of the source it names — what the reader is shown
     *  under the caret, which is the claim a marker makes. */
    private static String quoted(String source, Region at) {
        assertNotNull(at, "a report that points at nothing quotes nothing");
        List<String> lines = List.of(source.split("\n", -1));
        String line = lines.get(WhereItSits.in(source, at).start().line() - 1);
        int from = WhereItSits.in(source, at).start().column() - 1;
        int to = WhereItSits.in(source, at).end().line() == WhereItSits.in(source, at).start().line() ? WhereItSits.in(source, at).end().column() - 1 : line.length();
        return line.substring(from, Math.min(to, line.length()));
    }
}
