package souther.compiler;

import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.ReportContext;

import souther.compiler.source.SourceId;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.Located;
import souther.compiler.diag.Placement;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.query.Compilation;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Db;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A label carries where it is, and no reader works that out from where the label ended up.
 *
 * <p>The two used to be the same thing. A label naming no source was read in whichever file the
 * diagnostic was filed under, which is right for a position somebody made by hand and wrong for one
 * read out of a text this compile has no file for — and from the null alone nothing could tell them
 * apart. So a label about a clause of a published module was a sentence about a line of the file the
 * caller was compiling, and the three readers that noticed dropped it instead of saying it.
 *
 * <p>Measured on the same rule reported against the same construction, once with the declaration in
 * this compile and once with it off the module path. Which of the two the reader gets is the only
 * difference, and what changes between them is what this is about.
 */
class ALabelSaysWhereItIsWithoutBeingToldWhereItIsShownTest {

    private static final String CONSTRUCTING = """
            module app.uses exposing ( mk )
            import lib.rule ( Code )

            behavior mk : (n: Int) -> Code
                constructs Code
            let mk (n) = Code(n)
            """;

    private static final String DECLARING = """
            module lib.rule exposing ( Code )

            data Code = Int
                invariant atLeastOne = value >= 1
            """;

    /** The same, with two clauses — two things to say and one place to say them about. */
    private static final String DECLARING_TWO = """
            module lib.rule exposing ( Code )

            data Code = Int
                invariant atLeastOne = value >= 1
                invariant atMostTen = value <= 10
            """;

    private static final String IN_ONE_COMPILE = """
            module app.own exposing ( mk )

            data Code = Int
                invariant atLeastOne = value >= 1

            behavior mk : (n: Int) -> Code
                constructs Code
            let mk (n) = Code(n)
            """;

    // --- acceptance 1: a clause out of sight is not read in the file it was reached from ---------

    @Test
    void aClauseOfAModuleOffThePathIsSaidRatherThanPointedAt() {
        LabeledRegion label = onlyLabel(offThePath());

        DiagnosticPlace.Unavailable place =
                assertInstanceOf(DiagnosticPlace.Unavailable.class, label.place());
        assertEquals(new SourceProvenance.APublishedModule("lib.rule"), place.provenance());
        assertFalse(label.place() instanceof DiagnosticPlace.InSource,
                "there is no line of the caller's file this belongs on");
    }

    /**
     * And the reader is told which module, rather than told nothing.
     *
     * <p>This is what the drop cost. The same warning about the same rule showed the clause when the
     * declaration was in this project and showed nothing at all when it came off the module path —
     * leaving a reader with a clause name and no way to find what it says.
     */
    @Test
    void theReaderIsToldWhichModuleTheClauseIsIn() {
        String out = rendered(offThePath(), CONSTRUCTING);

        assertTrue(out.contains("`lib.rule`"), () -> "the module is named: " + out);
        assertFalse(out.contains("where it was reached from"),
                () -> "and not explained as a caret, there being none: " + out);
    }

    /** The control: with the declaration in this compile, the clause is quoted as it always was. */
    @Test
    void aClauseThisCompileHoldsIsStillQuoted() {
        LabeledRegion label = onlyLabel(inOneCompile());
        String out = rendered(inOneCompile(), IN_ONE_COMPILE);

        assertInstanceOf(DiagnosticPlace.InSource.class, label.place());
        assertEquals(4, WhereItSits.in(IN_ONE_COMPILE,
                ((souther.compiler.diag.DiagnosticPlace.InSource) label.place()).region())
                .start().line());
        assertTrue(out.contains("invariant atLeastOne = value >= 1"),
                () -> "the clause is quoted: " + out);
    }

    /**
     * Two clauses of one module out of sight are one label, not the same sentence twice.
     *
     * <p>A label is a sentence about a place, and where there is nothing to point at the place is
     * the whole of what a label has: what told two of them apart was the caret. Said once per clause
     * they come out identical, which reads as a repeat rather than as two clauses. Which clauses
     * they are is in the message, which names them.
     */
    @Test
    void twoClausesOfOneModuleOutOfSightAreSaidOnce() {
        Compilation c = Compilation.ofSources(List.of(CONSTRUCTING),
                ModulePath.of(Compiler.compile(DECLARING_TWO)));
        c.answerEverything();
        Diagnostic d = theWarning(c);

        assertEquals(1, d.secondary().size(),
                () -> "one place, said once: " + d.secondary());
        assertTrue(d.values().toString().contains("atLeastOne"),
                () -> "and both clauses are named in the message: " + d.values());
        assertTrue(d.values().toString().contains("atMostTen"),
                () -> "and both clauses are named in the message: " + d.values());
    }

    // --- acceptance 3: told apart by what the label carries -------------------------------------

    /**
     * Neither reading is reached through a null, and a hand-made position cannot produce either.
     *
     * <p>A region that names no source and claims to be where the code is has not been placed by
     * anybody, and there is no file for a report to place it in on its behalf. It is refused where
     * the label is made, which is the one place that classifies.
     */
    @Test
    void aRegionNobodyPlacedIsRefusedRatherThanPlacedByTheReport() {
        Diagnostic.Builder building = Diagnostic
                .say(new souther.compiler.diag.msg.NameMessage.NoValueOfThatNameInScope("x"))
                .at(Placement.aFileOfThisCompile(new SourceId("0")).at(1, 1));

        assertThrowsIllegalArgument(() -> building.secondary(
                souther.compiler.diag.Region.ofWidth(new souther.compiler.diag.SourcePos(3, 3), 4),
                new souther.compiler.diag.msg.NameMessage.WriteItOnItsOwn("x")));
    }

    /** And a region with an end missing is refused where it is made a place, rather than reaching
     *  a renderer and failing there. */
    @Test
    void aRegionWithNoEndIsRefusedWhereItIsMadeAPlace() {
        org.junit.jupiter.api.Assertions.assertThrows(DiagnosticPlace.NotAPlace.class,
                () -> DiagnosticPlace.of(new souther.compiler.diag.Region(
                        Placement.aFileOfThisCompile(new SourceId("0")).at(3, 3), null)));
        org.junit.jupiter.api.Assertions.assertThrows(DiagnosticPlace.NotAPlace.class,
                () -> DiagnosticPlace.of(null));
    }

    /** A report is said where its labels can be read, and a label nobody can be sent to adds no
     *  file to that — there is no author of {@code lib.rule} in this compile to tell. */
    @Test
    void aLabelWithNowhereToPointPutsTheReportInFrontOfNobodyNew() {
        Compilation c = offThePath();
        for (Db.Found found : c.reports()) {
            if ("E2011".equals(found.report().diagnostic().code())) {
                assertEquals(List.of(new SourceId("0")), c.publishSourceIdsOf(found));
            }
        }
    }

    // --- the fixtures ---------------------------------------------------------------------------

    private static Compilation offThePath() {
        Map<String, ClassFileImage> path = Compiler.compile(DECLARING);
        Compilation c = Compilation.ofSources(List.of(CONSTRUCTING), ModulePath.of(path));
        c.answerEverything();
        return c;
    }

    private static Compilation inOneCompile() {
        Compilation c = Compilation.ofSources(List.of(IN_ONE_COMPILE), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }

    private static Diagnostic theWarning(Compilation c) {
        List<Diagnostic> found = new ArrayList<>();
        for (Db.Found one : c.reports()) {
            if ("E2011".equals(one.report().diagnostic().code())) {
                found.add(one.report().diagnostic());
            }
        }
        assertEquals(1, found.size(), () -> "one construction is unproven: " + found);
        return found.get(0);
    }

    private static LabeledRegion onlyLabel(Compilation c) {
        Diagnostic d = theWarning(c);
        assertEquals(1, d.secondary().size(),
                () -> "the clause is said once, whichever reading it came through: " + d.secondary());
        return d.secondary().get(0);
    }

    private static String rendered(Compilation c, String source) {
        return new HumanRenderer(false).render(new Located(theWarning(c), ReportContext.inFile(new SourceId("0"))),
                id -> new SourceContext("m.sou", source, SourceLayout.of(source)), Locale.ENGLISH);
    }

    private static void assertThrowsIllegalArgument(Runnable r) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, r::run);
    }
}
