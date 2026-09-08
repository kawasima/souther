package souther.compiler.check;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A report about a clause points at the clause.
 *
 * <p>A name is not the only way to send a reader to one. A clause an author wrote no name on is
 * judged like any other, and the warning about it used to say "its invariant" and stop — which left
 * the reader the type's declaration to read and the clauses in it to work through, for a check that
 * knew exactly which one it had left standing.
 *
 * <p>What is pointed at follows the classification and not the message: E2011 is about the clauses
 * the guards did not establish, E2010 about the ones the value fails. Whether a clause can be named
 * and whether a reader can be sent to it are separate questions, so neither of them decides the
 * other here.
 */
class AWarningAboutAClauseSendsAReaderToItTest {

    /** Two clauses left standing, one named and one not. */
    private static final String TWO_UNSETTLED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant ordered = low <= high
                invariant high <= 100

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    /** One clause the value fails, one it merely leaves standing. */
    private static final String REFUTED_AND_UNKNOWN = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant lowNonNegative = low >= 0
                invariant ordered = low <= high

            behavior f : (high: Int) -> Bound
                constructs Bound

            let f (high) = Bound { low = -1, high = high }
            """;

    /** Three clauses, none named, all left standing. */
    private static final String THREE_UNNAMED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high
                invariant high <= 100
                invariant low >= 0

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    // --- what E2011 points at ---------------------------------------------------------------

    /** Both kinds of clause the guards did not establish, whether or not either was named. */
    @Test
    void aWarningPointsAtEveryClauseTheGuardsDidNotEstablish() {
        assertEquals(List.of(7, 8), lines(warning(TWO_UNSETTLED)),
                "the named clause and the unnamed one, both left standing");
        assertTrue(warning(TWO_UNSETTLED).secondary().stream().allMatch(
                        one -> one.said() instanceof InvariantMessage.ThisClauseIsNotEstablishedHere),
                "and neither is said to be more than that");
    }

    /**
     * Including the one the value fails. E2011 is raised where nothing was refuted, so this is the
     * unknown kind either way — but what it points at is read off {@code unsettled}, which is both
     * kinds, rather than off the kind that happens to be there.
     */
    @Test
    void aWarningAboutAnUnnamedClauseSaysWhereItIs() {
        Diagnostic warning = warning(NEITHER_NAMED);

        assertInstanceOf(InvariantMessage.NothingKnownHereEstablishesTheInvariant.class, warning.said(),
                "the clause has no name, so the sentence has none to write");
        assertEquals(List.of(7), lines(warning),
                "and the reader is sent to it anyway");
    }

    private static final String NEITHER_NAMED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    // --- what E2010 points at ---------------------------------------------------------------

    /** The clauses the value fails, and not the ones left standing beside them. */
    @Test
    void anErrorPointsAtWhatTheValueFailsAndNotAtWhatMerelyStands() {
        Diagnostic error = error(REFUTED_AND_UNKNOWN);

        assertEquals(List.of(7), lines(error),
                "`lowNonNegative` on line 7 is failed; `ordered` on line 8 is not decided");
        assertTrue(error.secondary().stream().allMatch(
                        one -> one.said() instanceof InvariantMessage.ThisClauseRejectsThisValue),
                "and the label says the stronger thing the set allows");
    }

    // --- the two questions stay apart --------------------------------------------------------

    /** Naming a clause does not decide whether a reader is sent to it. */
    @Test
    void aNamedClauseIsPointedAtLikeAnyOther() {
        assertEquals(lines(warning(NEITHER_NAMED)).size(), lines(warning(ONE_NAMED)).size());
        assertInstanceOf(InvariantMessage.NothingKnownHereEstablishes.class, warning(ONE_NAMED).said(),
                "the sentence names it, and the region points at it too");
    }

    private static final String ONE_NAMED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant ordered = low <= high

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    // --- order and count ----------------------------------------------------------------------

    /**
     * Every clause, in the order the declaration writes them. There is no bound on how many: what
     * bounds them is what the author wrote, and dropping the rest after some number of them would
     * read as a warning about the ones that were kept.
     */
    @Test
    void everyClauseIsPointedAtInTheOrderItWasDeclared() {
        assertEquals(List.of(7, 8, 9), lines(warning(THREE_UNNAMED)));
    }

    // --- where the clause is written ----------------------------------------------------------

    /** A clause written in another file is quoted from that file, not from the one the construction
     *  is in at the same numbers. */
    @Test
    void aClauseInAnotherFileNamesThatFile() {
        List<Diagnostic> warnings = Compiler.compileModulesWithWarnings(List.of(DECLARING, USING))
                .warnings().stream().filter(d -> "E2011".equals(d.code())).toList();
        assertEquals(1, warnings.size(), warnings.toString());

        LabeledRegion one = warnings.get(0).secondary().get(0);
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(new SourceId("1")),
                ((Primary.InSource) warnings.get(0).primary()).place().region().start().quotedFrom(),
                "the construction is in the second source");
        assertEquals(new SourceId("0"),
                ((souther.compiler.diag.DiagnosticPlace.InSource) one.place()).source(),
                "and the clause is in the first, which the label says rather than leaving the"
                        + " renderer to read the declaration's line out of the wrong file");
    }

    private static final String DECLARING = """
            module model exposing ( Bound )

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high
            """;

    private static final String USING = """
            module app
            import model ( Bound )

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    /**
     * A clause of a module read off the module path has nowhere this compile can send a reader.
     *
     * <p>Its declaration comes back as text reassembled from what the module carries, so the region
     * says a line and a column of a file no reader holds. Pointing at it anyway would quote whatever
     * sits at those numbers in the file the reader is looking at.
     *
     * <p>So it is said instead of pointed at. The label carries where the code came from
     * ({@code DiagnosticPlace.Unavailable}) and the reader is told which module it is in — where
     * before, having nothing to point at, the clause reader dropped the label and the reader was
     * told nothing at all.
     *
     * <p>What the sentence says is unchanged. The clause has no name here as it had none there, and
     * that — not this — is what decides the spelling.
     */
    @Test
    void aClauseOfAPublishedModuleIsNotPointedAt() {
        ModulePath path = ModulePath.of(Compiler.compileModules(List.of(DECLARING)));
        List<InvariantChecker.Said> said = java.util.Collections.synchronizedList(
                new java.util.ArrayList<>());
        List<Diagnostic> warnings;
        InvariantChecker.WATCHING = said;
        try {
            warnings = Compiler.compileModulesWithWarnings(List.of(USING), path)
                    .warnings().stream().filter(d -> "E2011".equals(d.code())).toList();
        } finally {
            InvariantChecker.WATCHING = null;
        }
        assertEquals(1, warnings.size(), warnings.toString());

        InvariantChecker.Judgment judgment = said.stream()
                .filter(one -> one.type().equals("Bound")).findFirst().orElseThrow().judgment();
        assertEquals(1, judgment.unsettled().size(),
                "the clause is read and judged like any other: " + judgment.found());
        assertEquals(List.of(new souther.compiler.diag.DiagnosticPlace.Unavailable(
                        new souther.compiler.diag.SourceProvenance.APublishedModule("model"))),
                warnings.get(0).secondary().stream().map(label -> label.place()).toList(),
                "written where this compile has no file, and saying which module that is");

        assertInstanceOf(InvariantMessage.NothingKnownHereEstablishesTheInvariant.class,
                warnings.get(0).said(), "the clause was written without a name");
        assertEquals(List.of(), lines(warnings.get(0)),
                "and there is no source here to send the reader to");
        assertEquals(1, warnings.get(0).secondary().size(),
                "the label is there all the same, saying what it can");
        assertTrue(new HumanRenderer(false)
                        .render(warnings.get(0), new SourceContext("app.sou", USING),
                                java.util.Locale.ENGLISH)
                        .contains("`model`"),
                "and a reader is told which module the clause is written in");
    }

    // --- reading the diagnostics --------------------------------------------------------------

    /** The lines the secondary regions point at, in the order they were written. */
    private static List<Integer> lines(Diagnostic d) {
        return d.secondary().stream().map(l -> l.place()).filter(pl -> pl instanceof souther.compiler.diag.DiagnosticPlace.InSource)
                .map(pl -> ((souther.compiler.diag.DiagnosticPlace.InSource) pl).region())
                .map(Region::start).map(pos -> pos.line()).toList();
    }

    private static Diagnostic warning(String source) {
        List<Diagnostic> found = Compiler.compileWithWarnings(source).warnings().stream()
                .filter(d -> "E2011".equals(d.code()))
                .toList();
        assertEquals(1, found.size(), "expected one E2011 to hold: " + found);
        return found.get(0);
    }

    private static Diagnostic error(String source) {
        CompileException thrown = assertThrows(CompileException.class,
                () -> Compiler.compileWithWarnings(source));
        assertEquals("E2010", thrown.diagnostic().code(), thrown.getMessage());
        return thrown.diagnostic();
    }
}
