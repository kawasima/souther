package souther.compiler.diag;

import souther.compiler.cst.SourceLayout;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.source.SourceId;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a report with nowhere to point tells a reader, on every surface that shows it.
 *
 * <p>A citation tells five states apart and a diagnostic's primary now carries the one that has no
 * place and does know where the code is. Carrying it is only half: a value nothing reads is a value
 * nobody has, and the surfaces read the primary's <em>region</em> — which such a report has none of.
 * So the terminal said the sentence with no account of why there was no caret, and the JSON a build
 * reads said nothing at all.
 *
 * <p>Both of those are the drop this whole change is about, arriving at the last boundary: a fact the
 * compiler knows, thrown away, and reconstructed downstream from whatever is to hand.
 */
class AReportWithNowhereToPointStillSaysWhereItsCodeIsTest {

    private static final SourceProvenance THE_CODE =
            new SourceProvenance.APublishedModule("lib.rule", "lib.rule.atLeast");

    /** A place in the importing file, where a report with nowhere to point is moved to. */
    private static final SourcePos AN_IMPORT_LINE = inTheImport(1);

    /** The place {@code within} units into the second thing written in that file. */
    private static SourcePos inTheImport(int within) {
        return Placement.aFileOfThisCompile(new SourceId("app.sou")).at(2, within);
    }

    private static Diagnostic nowhereToPoint() {
        return Diagnostic.atCodeWrittenOutOfSight(THE_CODE)
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();
    }

    /** The sentence a person is shown says where the code is, the way a label with nothing to point
     *  at says it — one wording for the same situation, whichever part of a report is in it. */
    @Test
    void theSentenceAPersonReadsSaysWhereTheCodeIs() {
        String body = DiagnosticRenderer.body(nowhereToPoint(), Locale.ENGLISH);

        assertTrue(body.contains("lib.rule.atLeast"),
                () -> "a report with no caret says which code it is about: " + body);
    }

    /** And what a build reads says it as a value rather than inside a sentence, in the words this
     *  document already uses for code out of sight. */
    @Test
    void whatABuildReadsSaysItAsAValue() {
        String json = new JsonRenderer().render(
                new Located(nowhereToPoint(), ReportContext.inFile(new SourceId("app.sou"))),
                _ -> null, Locale.ENGLISH);

        assertTrue(json.contains("\"writtenAt\""),
                () -> "a tool reads where the code is rather than parsing the message: " + json);
        assertTrue(json.contains("lib.rule.atLeast"), () -> json);
        assertFalse(json.contains("\"region\""),
                () -> "and there is no region, because there is nowhere to point: " + json);
    }

    /**
     * Moving such a report somewhere a reader can be sent does not re-decide what it is about.
     *
     * <p>A caller says where the code is because a report with nothing pointed at has no answer to
     * read; one that has an answer is not asking. Handing it a different one is somebody working it
     * out again from what was to hand, which is the whole shape of this defect.
     */
    @Test
    void movingItSomewhereReadableDoesNotRedecideWhatItIsAbout() {
        assertThrows(Diagnostic.MovedSomewhereElsesCode.class,
                () -> nowhereToPoint().reachedFrom(
                        java.util.List.of(AN_IMPORT_LINE),
                        new SourceProvenance.APublishedModule("lib.other"),
                        new ModuleMessage.ItIsReachedFromHereToo()));
    }

    /**
     * And the same holds for a report that says it through the position it points at.
     *
     * <p>The other way a report knows. A position still inside a module's published text has never
     * been turned into a place, so the report has a region — and that region says which module wrote
     * the code as surely as a report with no region does. Guarding one and not the other leaves the
     * hole in the shape it was found in: two states with the same answer, one of them read.
     */
    @Test
    void aReportPointingIntoAPublishedTextIsNotMovedAsSomebodyElsesCodeEither() {
        Diagnostic said = Diagnostic.at(Placement.whatAModulePublished(THE_CODE).at(4, 20))
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();

        assertEquals(new WhereCodeIsWritten.Elsewhere(THE_CODE.asDeclared()),
                said.whereItsCodeIsWritten(),
                "it says where its code is, through what it points at");
        assertThrows(Diagnostic.MovedSomewhereElsesCode.class,
                () -> said.reachedFrom(
                        java.util.List.of(AN_IMPORT_LINE),
                        new SourceProvenance.APublishedModule("lib.other"),
                        new ModuleMessage.ItIsReachedFromHereToo()));
    }

    /**
     * A report about code the reader is looking at has already answered, and moving it is refused.
     *
     * <p>"There is no elsewhere to name" is an answer — it is <em>here</em> — and it was the same
     * value as "this report has not said", so a caller could tell such a report its code was in a
     * module and be believed. A caret moving is not the code moving.
     */
    @Test
    void aReportAboutCodeTheReaderIsLookingAtSaysItIsHereAndIsNotMoved() {
        Diagnostic said = Diagnostic.at(
                        AN_IMPORT_LINE)
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();

        assertEquals(new WhereCodeIsWritten.Here(), said.whereItsCodeIsWritten(),
                "the code is where it points");
        assertThrows(Diagnostic.MovedSomewhereElsesCode.class,
                () -> said.reachedFrom(
                        java.util.List.of(AN_IMPORT_LINE),
                        new SourceProvenance.APublishedModule("lib.other"),
                        new ModuleMessage.ItIsReachedFromHereToo()));
    }

    /** And one that points at nothing has not answered, so the caller's answer is taken. */
    @Test
    void aReportThatPointsAtNothingHasNotAnsweredAndTheCallerMay() {
        Diagnostic said = Diagnostic.at((SourcePos) null)
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .build();

        assertEquals(new WhereCodeIsWritten.Unstated(), said.whereItsCodeIsWritten(),
                "nothing to point at, and nothing said through it");
        assertEquals(THE_CODE, ((Citation.Reached) Citation.of(((Primary.InSource) said.reachedFrom(
                        java.util.List.of(AN_IMPORT_LINE), THE_CODE,
                        new ModuleMessage.ItIsReachedFromHereToo()).primary()).place().region().start()))
                        .provenance(),
                "so the caller says where the code is, and it is taken");
    }

    /** Moved with what it says, it becomes a report a reader can be sent to, still about the same
     *  code. */
    @Test
    void movedWithWhatItSaysItPointsSomewhereAndIsAboutTheSameCode() {
        Diagnostic moved = nowhereToPoint().reachedFrom(
                java.util.List.of(AN_IMPORT_LINE), THE_CODE,
                new ModuleMessage.ItIsReachedFromHereToo());

        Citation.Reached reached =
                (Citation.Reached) Citation.of(((Primary.InSource) moved.primary()).place().region().start());
        assertEquals(THE_CODE, reached.provenance(), "the same code");
        assertTrue(reached.at().isIn(new SourceId("app.sou")), "somewhere the reader holds");
    }

    /**
     * A label of its own does not become the place it points at.
     *
     * <p>A report with nowhere to point may still carry a label somewhere a reader can be sent — the
     * guard a boundary finding was drawn by is in this compile's own source while the body the
     * finding is about is not. Read as the thing to put the marker on, that label gives a report
     * that says it points nowhere a line and a column that came from something else, which is the
     * reading this family of types exists to stop: the header said the report was at the guard while
     * the sentence under it said there was nowhere here to point at.
     *
     * <p>Which is not the rule that lets a label be the anchor. A problem written in two files is
     * read from each of them, and on the second the label is what the marker goes on — but that is
     * two places changing places, and a report with no place of its own has nothing to change with.
     *
     * <p>And the label keeps what it says. Anchored, its sentence is not written anywhere: an anchor
     * is what the message is about, so nothing prints a note for it.
     */
    @Test
    void aLabelOfItsOwnDoesNotBecomeThePlaceItPointsAt() {
        Diagnostic said = Diagnostic.atCodeWrittenOutOfSight(THE_CODE)
                .say(new ModuleMessage.CannotReadAFieldOnASum("x", "S"))
                .secondary(Region.point(inTheImport(3)),
                        new ModuleMessage.RenameItOrDropTheDependency("lib.rule"))
                .build();
        Located located = new Located(said, ReportContext.inFile(new SourceId("app.sou")));

        DiagnosticView view = DiagnosticView.of(said, located.context());

        assertTrue(view.anchor().isEmpty(),
                () -> "nothing here is what the report is about: " + view.anchor());
        assertEquals(1, view.others().size(), "and the label is still a label");

        String out = new HumanRenderer(false).render(located,
                _ -> new SourceContext("app.sou", "line one\nline two here\n", SourceLayout.of("line one\nline two here\n")), Locale.ENGLISH);

        assertFalse(out.lines().findFirst().orElseThrow().contains("2:3"),
                () -> "the report is not at the guard's line: " + out);
        assertTrue(out.contains("Rename"), () -> "and the label still says what it says: " + out);
    }
}
