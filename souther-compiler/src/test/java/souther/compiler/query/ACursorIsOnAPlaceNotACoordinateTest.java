package souther.compiler.query;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import souther.compiler.ast.WrittenName;
import souther.compiler.check.Resolve;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a module is made of is not all written in one file. An attached {@code examples for} file's
 * rows, tables and values join the module they are for, and a question asked of the module reads all
 * of them — so one line and column is two places, and an editor asking about one of them was
 * answered about whichever the merge happened to reach first.
 *
 * <p>The consequence in an editor is that go-to-definition, hover and rename describe a name the
 * cursor is not on. Same defect as issue #309, one layer over: a coordinate treated as a place.
 *
 * <p>The two files here are built so that line 8 column 14 is a name in each, and the two names
 * denote different values — otherwise a wrong answer would still look right.
 */
class ACursorIsOnAPlaceNotACoordinateTest {

    private static final SourceId MODEL_ID = new SourceId("m.sou");
    private static final SourceId ATTACHED_ID = new SourceId("m.examples.sou");

    /** `もと` is read by the row on line 8, at column 14. */
    private static final String MODEL = """
            module m

            data D = { v: Int }
            behavior f : (d: D) -> D
            let f (d) = d

            example f
                | "a" : (もと) -> もと
            """;

    /** `べつ` is read on line 8, at column 14 — the same coordinate, another file, another value. */
    private static final String ATTACHED = """
            examples for m

            let もと = D { v = 1 }
            let べつ = D { v = 2 }

            let ほか = D
                {
                     v = べつ.v
                }
            """;

    private static Compilation compiled() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(MODEL_ID.value(), MODEL);
        byId.put(ATTACHED_ID.value(), ATTACHED);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }

    /**
     * What the compiler says is under a cursor at line 8 column 14 of {@code inFile}.
     *
     * <p>A cursor is a line and a column — it is where a reader put it — and the place that is has
     * to be read off the text it is in. Which is the whole of what this test is about: the two
     * files write a name at the same line and column and the two places are not the same place.
     */
    private static Resolve.ValueUse under(SourceId inFile) {
        String text = MODEL_ID.equals(inFile) ? MODEL : ATTACHED;
        SourceLayout laidOut = SourceLayout.of(text, inFile);
        return compiled().db().ask(new Names.ValueDenotedAt(
                laidOut.placeAt(laidOut.lines().offsetOf(7, 13)))).value();
    }

    @Test
    void bothFilesReallyDoWriteANameAtThatCoordinate() {
        assertNotNull(under(MODEL_ID), "the model's row reads a name at 8:14");
        assertNotNull(under(ATTACHED_ID), "and so does the attached file's value");
    }

    @Test
    void aCursorInTheModelFileIsOnTheNameTheModelFileWrote() {
        Resolve.ValueUse use = under(MODEL_ID);

        assertEquals("もと", use.written().canonical());
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(MODEL_ID), use.pos().quotedFrom());
    }

    @Test
    void aCursorInTheAttachedFileIsOnTheNameThatFileWrote() {
        Resolve.ValueUse use = under(ATTACHED_ID);

        assertEquals("べつ", use.written().canonical());
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(ATTACHED_ID), use.pos().quotedFrom());
    }

    @Test
    void theTwoAnswersAreNotTheSameValue() {
        assertEquals("m.もと", under(MODEL_ID).denotes().toString());
        assertEquals("m.べつ", under(ATTACHED_ID).denotes().toString());
    }

    /**
     * A question that names no file names no place. It used to be answered by line and column
     * alone, for callers that had only those to give; the module a question is answered about is
     * now read off the file it names, so a question without one names no module either.
     */
    @Test
    void aCursorThatNamesNoFileIsOnNothing() {
        Resolve.ValueUse use = compiled().db()
                .ask(new Names.ValueDenotedAt(new SourcePos(8, 14))).value();

        assertNull(use, "a line and a column are not a place");
    }

    // --- a name is somewhere, and a question is about somewhere ------------------------------------

    @Test
    void aQuestionThatNamesNoFileIsNotAnsweredWithANameFromOne() {
        assertFalse(Names.spans(WrittenName.of("name", new SourcePos(8, 14, MODEL_ID)),
                new SourcePos(8, 14)));
    }

    /**
     * A name that names no source was read from no source of this compile — a synthesized node, or
     * a module read off the module path. It is under nothing a reader has open, so a question about
     * an open file is not answered with it.
     */
    @Test
    void aQuestionAboutAFileIsNotAnsweredWithANameFromNoFile() {
        assertFalse(Names.spans(WrittenName.of("name", new SourcePos(8, 14)),
                new SourcePos(8, 14, MODEL_ID)));
    }

    @Test
    void aNameInAnotherFileIsNotUnderTheCursorHoweverTheLinesLineUp() {
        assertFalse(Names.spans(WrittenName.of("name", new SourcePos(8, 14, ATTACHED_ID)),
                new SourcePos(8, 14, MODEL_ID)));
    }
}
