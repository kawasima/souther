package souther.compiler.diag;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.source.SourceId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Where a report is said and where its repair writes come apart the moment the report is moved.
 *
 * <p>{@link Diagnostic#reachedFrom} is for a finding about code this compile has no source for: the
 * caret goes to the nearest place on the way to that module a file here writes, which is an
 * {@code import} line. The characters that answer the finding did not move — they are in the
 * module's own text, wherever that is — so the repair is what it was.
 *
 * <p>Read together with {@code Compilation.repairs}, which offers an edit in the file it writes
 * into: a repair left pointing at text nobody holds is offered nowhere, and one that followed the
 * caret would be offered on an import line, whose characters it says nothing about.
 */
class MovingAReportDoesNotMoveItsRepairTest {

    private static final SourceProvenance THE_CODE =
            new SourceProvenance.APublishedModule("lib.rule", "lib.rule.atLeast");

    @Test
    void theRepairStaysWhereItsCharactersAre() {
        Placement published = Placement.whatAModulePublished(THE_CODE);
        Region misspelling = new Region(published.at(4, 20), published.at(4, 25));
        Diagnostic said = Diagnostic.at(published.at(4, 20))
                .repair(misspelling, "atLeast")
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();

        Diagnostic moved = said.reachedFrom(
                List.of(new SourcePos(2, 1, new SourceId("app.sou"))),
                THE_CODE.asDeclared(),
                new ModuleMessage.ItIsReachedFromHereToo());

        assertNotNull(moved.repair(), "moving the caret is not dropping the edit");
        assertEquals(new Repair.AnEdit(misspelling, "atLeast"), moved.repair());
        assertNotEquals(moved.primary(), Primary.at(misspelling),
                "and the report is said at the import, which is the point of moving it");
    }

    /**
     * A borrowed position is not a place to write. An expansion's copy sits at a line of the
     * author's file and says nothing about what is there, so an edit made over it would rewrite
     * text that has nothing to do with the finding.
     *
     * <p>Refused where an edit is made, so no pass can hand one on and no reader has to check.
     */
    @Test
    void anEditCannotBeMadeOverCodeThatWasCopiedHere() {
        assertThrows(IllegalArgumentException.class,
                () -> new Repair.AnEdit(borrowed(), "atLeast"));
    }

    /**
     * And a check that knows the word still says it. What a reader is told does not depend on
     * whether a machine could act on it — the name inside the copied body is still probably a
     * misspelling of the one nearby — so the word comes out and the edit does not.
     */
    @Test
    void aWordIsStillSaidWhereThereIsNowhereToWriteIt() {
        Diagnostic said = Diagnostic.at(borrowed().start())
                .repair(borrowed(), "atLeast")
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();

        assertEquals(new Repair.AWord("atLeast"), said.repair());
    }

    private static Region borrowed() {
        SourcePos wrote = new SourcePos(4, 20, new SourceId("app.sou"));
        DeclaringCode declaring = new DeclaringCode(THE_CODE.asDeclared());
        return new Region(wrote.standingInFor(declaring), wrote.standingInFor(declaring));
    }
}
