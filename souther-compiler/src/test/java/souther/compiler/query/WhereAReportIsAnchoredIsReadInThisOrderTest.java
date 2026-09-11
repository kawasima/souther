package souther.compiler.query;

import souther.compiler.diag.Placement;
import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.NameMessage;


import souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two things can say which file a report is anchored in — filed under, and quoted from — and they
 * are asked in one order. This states that order, so it is a contract rather than whatever the
 * implementation happens to do.
 *
 * <p>A key says which input was asked about. A position says where the caret sits — and the line
 * under the caret is quoted out of the file the report is filed under, so where the two disagree,
 * answering with the key's shows a reader a line they did not write. A question asked about a module
 * whose rows were written in an attached {@code examples for} file is where the two do disagree: it
 * can only answer with the module's own file, and the rows are somewhere else. So the position comes
 * first.
 *
 * <p>Two and not three. A report could once name its own file as well, ahead of both — and the two
 * sites that did read that name off a value that held it beside the place, so what they named was
 * the position's answer written somewhere else. A third tier that can only agree with the second is
 * a tier that can disagree with it.
 *
 * <p>Only the anchor. Whether the problem is also written in some other file is a claim about the
 * regions the report points at, which the check that found it makes and neither of these can —
 * {@link AFindingIsSaidWhereItBelongsNotWhereItPointsTest} is where that is stated.
 */
class WhereAReportIsAnchoredIsReadInThisOrderTest {

    /** A report pointing into {@code positionsFile}, or into a text nothing names where it is none,
     *  found by a key naming {@code keysFile}. */
    private static Db.Found found(SourceId positionsFile, SourceId keysFile) {
        Placement in = positionsFile == null ? Placement.aTextWithNoIdentity()
                : Placement.aFileOfThisCompile(positionsFile);
        Diagnostic d = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(in.at(3, 3), 4).build();
        return new Db.Found("m", keysFile, Report.of(d));
    }

    @Test
    void aPrimaryPositionOverridesTheSourceTheQuestionWasAskedAbout() {
        Db.Found f = found(new SourceId("rows.sou"), new SourceId("model.sou"));

        assertEquals(new SourceId("rows.sou"), f.claimedSourceId(),
                "the line under the caret is quoted from here, so this is the file");
    }

    @Test
    void theQuestionsSourceAnswersWhenThePositionNamesNone() {
        Db.Found f = found(null, new SourceId("model.sou"));

        assertEquals(new SourceId("model.sou"), f.claimedSourceId(),
                "a synthesized position says nothing, so what asked the question answers");
    }

    @Test
    void nothingSaysWhenNeitherDoes() {
        Db.Found f = found(null, null);

        assertEquals(null, f.claimedSourceId(),
                "the module's own source is the last word, and only a caller holding the layout "
                        + "can apply it");
    }
}
