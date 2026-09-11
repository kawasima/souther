package souther.compiler.check;

import souther.compiler.source.SourceId;

import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.diag.DeclaringCode;
import souther.compiler.diag.Placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a region is, asked once, before anything points at it.
 *
 * <p>Four answers and not two. A place a reader can be sent to. A place this compile has no file for
 * — a declaration read back out of a published module was written in a text nobody holds — which is
 * an answer and not the absence of one: a report about it says where the code came from. A region
 * that is not one place at all, which is the compiler's model contradicting itself and is refused.
 * And a region nobody placed, which used to mean "read it in whichever file this ends up filed
 * under" and is now a caller that has not finished answering.
 *
 * <p>The middle two used to be one. Both were a null source, so every reader had to guess which it
 * had, and each of them guessed differently: a renderer read it as the diagnostic's own file, and
 * the three sites that knew better dropped the label instead.
 */
class WhetherAPlaceCanBeQuotedIsAskedBeforeItIsPointedAtTest {

    /** A place somebody spells by hand in the file {@code file}. */
    private static SourcePos at(String file, int token, int within) {
        return Placement.aFileOfThisCompile(new SourceId(file)).at(token, within);
    }

    /** A region in {@code sourceId}, or in a text nothing names where it is none. */
    private static Region in(SourceId sourceId) {
        Placement text = sourceId == null ? Placement.aTextWithNoIdentity()
                : Placement.aFileOfThisCompile(sourceId);
        return new Region(text.at(3, 5), text.at(3, 20));
    }

    /** A region read from a text put back together out of what a module published. */
    private static Region outOfSight() {
        Placement lib = Placement.whatAModulePublished(
                new SourceProvenance.APublishedModule("lib.rule"));
        return new Region(lib.at(3, 5), lib.at(3, 20));
    }

    @Test
    void aRegionReadOffASourceIsSomewhereAReaderIsSent() {
        DiagnosticPlace place = DiagnosticPlace.of(in(new SourceId("model.sou")));

        DiagnosticPlace.InSource sent = assertInstanceOf(DiagnosticPlace.InSource.class, place);
        assertEquals(new SourceId("model.sou"), sent.source(),
                "the region names the source, which is what makes it one to point at");
        assertEquals(in(new SourceId("model.sou")), sent.region(),
                "the region is carried as it was, not measured again");
    }

    /** What a published module's declarations come back as: written somewhere, quotable nowhere,
     *  and saying which somewhere rather than saying nothing. */
    @Test
    void aRegionOutOfSightSaysWhereTheCodeCameFrom() {
        DiagnosticPlace place = DiagnosticPlace.of(outOfSight());

        assertEquals(new SourceProvenance.APublishedModule("lib.rule"),
                assertInstanceOf(DiagnosticPlace.Unavailable.class, place).provenance());
        assertTrue(!(place instanceof souther.compiler.diag.DiagnosticPlace.InSource), "and there is nowhere to send a reader");
    }

    /**
     * A region nobody placed is refused rather than answered.
     *
     * <p>It reads exactly like the one above — a null source — and it is a different thing. Reading
     * the two as one is what put a label about a clause of {@code lib.rule} on a line of the file
     * the caller was compiling.
     */
    @Test
    void aRegionNamingNoSourceAndClaimingToBeThePlaceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DiagnosticPlace.of(in(null)));
    }

    @Test
    void aRegionRunningBetweenTwoSourcesIsNotAPlaceAtAll() {
        DiagnosticPlace.NotOnePlace refused = assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> DiagnosticPlace.of(new Region(at("model.sou", 3, 5),
                        at("other.sou", 3, 20))));

        assertTrue(refused.getMessage().contains("model.sou"), refused.getMessage());
        assertTrue(refused.getMessage().contains("other.sou"), refused.getMessage());
    }

    /**
     * And the same of the other half of where a coordinate is: a region running from a copy of one
     * module to a copy of another is two places as much as one running between two files.
     *
     * <p>Read off the start alone the second end's answer goes unrecorded, and the region comes back
     * as a report about the first with nothing anywhere saying otherwise. Measured before this was
     * written: no region in the compiler's own corpus has ends that disagree about provenance.
     */
    @Test
    void aRegionWhoseEndsCameFromTwoModulesIsNotOnePlaceEither() {
        SourcePos fromA = Placement.whatAModulePublished(
                new SourceProvenance.APublishedModule("lib.a")).at(3, 5);
        SourcePos fromB = Placement.whatAModulePublished(
                new SourceProvenance.APublishedModule("lib.b")).at(3, 20);

        assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> DiagnosticPlace.of(new Region(fromA, fromB)));
        assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> DiagnosticPlace.of(new Region(at("model.sou", 3, 5),
                        at("model.sou", 3, 20).standingInFor(
                                new DeclaringCode(
                                        new SourceProvenance.APublishedModule("lib.a"))))));
    }

    /** The same either way round: one end knowing its source and the other not is the same broken
     *  region as two ends naming two. */
    @Test
    void aRegionWithOneEndInASourceIsRefusedToo() {
        assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> DiagnosticPlace.of(new Region(at("model.sou", 3, 5),
                        new SourcePos(3, 20))));
        assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> DiagnosticPlace.of(new Region(new SourcePos(3, 5),
                        at("model.sou", 3, 20))));
    }
}
