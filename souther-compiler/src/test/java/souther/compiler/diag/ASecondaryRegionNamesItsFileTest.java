package souther.compiler.diag;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;


import souther.compiler.diag.msg.NameMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A diagnostic points at as many files as it has regions. What each region is quoted from, and what
 * a reader is told about where it is, is settled here rather than by whichever file the diagnostic
 * as a whole was filed under.
 */
class ASecondaryRegionNamesItsFileTest {

    private static final String ROW_FILE = """
            module m
            example f
              | "one" : (1) -> 2
            """;

    private static final String FAKE_FILE = """
            module n
            fake f
              | (1) -> 3
            """;

    private static final SourceLayout ROWS = SourceLayout.of(ROW_FILE, new SourceId("rows"));

    private static final SourceLayout FAKES = SourceLayout.of(FAKE_FILE, new SourceId("fakes"));

    /** The same rows, as a text nobody has named — a buffer an editor is holding. */
    private static final SourceLayout UNNAMED = SourceLayout.of(ROW_FILE);

    /** The place at line {@code line} column {@code column} of {@code laidOut}'s text. */
    private static SourcePos at(SourceLayout laidOut, int line, int column) {
        return laidOut.placeAt(laidOut.lines().offsetOf(line - 1, column - 1));
    }

    private static SourceContext rows() {
        return new SourceContext("rows.sou", ROW_FILE, ROWS);
    }

    private static SourceContext fakes() {
        return new SourceContext("fakes.sou", FAKE_FILE, FAKES);
    }

    /** A resolver over named sources, recording what it was asked for. */
    private record Asked(List<SourceId> ids, SourceContextResolver resolver) {}

    private static Asked resolver() {
        List<SourceId> ids = new ArrayList<>();
        SourceContextResolver base = id -> {
            ids.add(id);
            if ("rows".equals(id.value())) {
                return rows();
            }
            return "fakes".equals(id.value()) ? fakes() : null;
        };
        return new Asked(ids, SourceContextResolver.memoized(base::sourceOf));
    }

    /** A diagnostic whose secondary is in {@code secondarySourceId} — which the region says, being
     *  the only thing that says it. Its own place is in the row file, which its region says too:
     *  what these are about is where a second region is read, and a primary that named no source
     *  would leave the caller's answer doing that work as well. */
    private static Diagnostic withSecondary(String secondarySourceId) {
        return Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(at(ROWS, 3, 3), 5)
                .secondary(Region.ofWidth(at("rows".equals(secondarySourceId) ? ROWS : FAKES, 3, 3), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .build();
    }

    /** The source a spot shown here is in. Every one of them is in a source: what a text nobody
     *  named looks like is {@link #aDiagnosticInATextTheCallerNamesIsQuotedFromIt}. */
    private static SourceId sourceOf(Shown shown) {
        return ((Spot.InSource) shown.spot()).place().source();
    }

    // --- what is quoted -------------------------------------------------------------------------

    @Test
    void aSecondaryInTheDiagnosticsOwnFileIsQuotedFromItAndNotNamed() {
        Asked asked = resolver();
        String out = new HumanRenderer(false).render(
                new Located(withSecondary("rows"), ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertEquals(2, count(out, "  | \"one\" : (1) -> 2"),
                "the primary and the secondary both quote the row file: " + out);
        assertFalse(out.contains("fakes.sou"), out);
        assertEquals(1, count(out, "rows.sou"), "named once, in the title bar: " + out);
    }

    @Test
    void aSecondaryInAnotherFileIsQuotedFromThatFileAndNamed() {
        Asked asked = resolver();
        String out = new HumanRenderer(false).render(
                new Located(withSecondary("fakes"), ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertTrue(out.contains("  | \"one\" : (1) -> 2"), "the primary quotes the row: " + out);
        assertTrue(out.contains("  | (1) -> 3"), "the secondary quotes the fake: " + out);
        assertTrue(out.contains("fakes.sou:3:3"), "and says where it is: " + out);
    }

    /**
     * A report in a text this compilation cannot name is quoted from the text the caller says it is
     * reading.
     *
     * <p>Which the caller hands over, rather than answering for every id a resolver is asked about.
     * The line and the column are real — somebody wrote that code and a parser read it — and which
     * text they are of is the one thing the report cannot say, so the surface says it.
     */
    @Test
    void aDiagnosticInATextTheCallerNamesIsQuotedFromIt() {
        Diagnostic inABufferNobodyNamed = Diagnostic
                .say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(at(UNNAMED, 3, 3), 5)
                .build();

        String out = new HumanRenderer(false).render(
                new Located(inABufferNobodyNamed, ReportContext.ofTheTextItself(rows())),
                SourceContextResolver.none(), Locale.ENGLISH);

        assertEquals(1, count(out, "  | \"one\" : (1) -> 2"), out);
        assertFalse(out.contains("null"), out);
    }

    /**
     * And where the caller says nothing, nothing is quoted rather than something guessed at.
     *
     * <p>The other half of the one above, and the reason the context is a value: a resolver that
     * answered for whatever it was asked would quote this from the file the reader happened to have
     * in mind, at numbers that are not of it.
     */
    @Test
    void aDiagnosticInATextNobodyNamedIsNotQuotedFromWhateverIsToHand() {
        Diagnostic inABufferNobodyNamed = Diagnostic
                .say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(at(UNNAMED, 3, 3), 5)
                .build();

        String out = new HumanRenderer(false).render(
                new Located(inABufferNobodyNamed, ReportContext.NONE),
                id -> rows(), Locale.ENGLISH);

        assertEquals(0, count(out, "  | \"one\" : (1) -> 2"),
                "nothing to quote it from, so no line is quoted: " + out);
        assertFalse(out.contains("3:3"), "and no numbers against a file nobody named: " + out);
    }

    @Test
    void aSourceIsReadOnceHoweverManyRegionsAreInIt() {
        Asked asked = resolver();
        Diagnostic d = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(at(UNNAMED, 3, 3), 5)
                .secondary(Region.ofWidth(at(FAKES, 3, 3), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .secondary(Region.ofWidth(at(FAKES, 2, 1), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .build();

        new HumanRenderer(false).render(new Located(d, ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertEquals(1, count(asked.ids(), "fakes"),
                "two regions in one file, one read: " + asked.ids());
    }

    // --- JSON -----------------------------------------------------------------------------------

    @Test
    void jsonNamesNoFileForASecondaryInTheDiagnosticsOwnFile() {
        Asked asked = resolver();
        String out = new JsonRenderer().render(
                new Located(withSecondary("rows"), ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertEquals(1, count(out, "\"file\""), "only the top-level file: " + out);
    }

    @Test
    void jsonNamesTheFileOfASecondaryWrittenInAnother() {
        Asked asked = resolver();
        String out = new JsonRenderer().render(
                new Located(withSecondary("fakes"), ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertTrue(out.contains("\"file\":\"rows.sou\""), out);
        assertTrue(out.contains("\"file\":\"fakes.sou\""), out);
    }

    // --- which region a file reads the diagnostic at ---------------------------------------------

    @Test
    void theFileHoldingTheSecondaryReadsItAsTheAnchorAndThePrimaryAsElsewhere() {
        Diagnostic d = withSecondary("fakes");

        DiagnosticView own = DiagnosticView.of(d, ReportContext.inFile(new SourceId("rows")));
        assertEquals(new SourceId("rows"), sourceOf(own.anchor().orElseThrow()));
        assertInstanceOf(Shown.ItsSubject.class, own.anchor().orElseThrow(),
                "what the message is about carries no note of its own");
        assertEquals(List.of(new SourceId("fakes")),
                own.others().stream().map(ASecondaryRegionNamesItsFileTest::sourceOf).toList());

        DiagnosticView other = DiagnosticView.of(d, ReportContext.of(new SourceId("rows"), new SourceId("fakes")));
        assertEquals(new SourceId("fakes"), sourceOf(other.anchor().orElseThrow()),
                "the two change places");
        assertInstanceOf(Shown.ALabel.class, other.anchor().orElseThrow());
        assertEquals(List.of(new SourceId("rows")),
                other.others().stream().map(ASecondaryRegionNamesItsFileTest::sourceOf).toList());
    }

    /** A secondary in the diagnostic's own file is one spot among the others, named the same way
     *  every other is — by its region, not by being the one that said nothing. */
    @Test
    void aSecondaryInTheDiagnosticsOwnFileIsNamedLikeAnyOther() {
        DiagnosticView view = DiagnosticView.of(withSecondary("rows"), ReportContext.inFile(new SourceId("rows")));

        assertEquals(List.of(new SourceId("rows")),
                view.others().stream().map(ASecondaryRegionNamesItsFileTest::sourceOf).toList());
        assertEquals(new SourceId("rows"), ((DiagnosticPlace.InSource)
                        withSecondary("rows").secondary().get(0).place()).source());
    }

    @Test
    void aFileNoRegionIsInIsRefusedRatherThanAnchoredSomewhereElse() {
        Diagnostic d = withSecondary("fakes");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> DiagnosticView.of(d,
                        ReportContext.of(new SourceId("rows"), new SourceId("elsewhere"))));

        assertTrue(refused.getMessage().contains("elsewhere"), refused.getMessage());
    }

    @Test
    void whereOneFileHoldsSeveralRegionsTheFirstWrittenIsTheAnchor() {
        Diagnostic d = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(at(UNNAMED, 3, 3), 5)
                .secondary(Region.ofWidth(at(FAKES, 3, 3), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .secondary(Region.ofWidth(at(FAKES, 2, 1), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .build();

        DiagnosticView view = DiagnosticView.of(d, ReportContext.of(new SourceId("rows"), new SourceId("fakes")));

        assertEquals(at(UNNAMED, 3, 3),
                view.anchor().orElseThrow().spot().region().start(),
                "the primary, in declaration order, and not the earlier place beside it");
        assertEquals(2, view.others().size());
    }

    // --- what a label says about where it is ----------------------------------------------------

    /**
     * A label is in the source its region was read from, and nothing else says which that is.
     *
     * <p>There used to be two things that could: what the label was given and what the region
     * carried. A reader took the first, so the file a marker went into and the file its line was
     * quoted from could come apart — which is the shape of mistake this whole area is about. The
     * second one is gone rather than checked for agreement.
     */
    @Test
    void aLabelIsInTheSourceItsRegionWasReadFrom() {
        LabeledRegion label = new LabeledRegion(Region.ofWidth(at(FAKES, 3, 3), 4),
                new NameMessage.WriteItOnItsOwn("x"));

        assertEquals(new DiagnosticPlace.InSource(Region.ofWidth(at(FAKES, 3, 3), 4)),
                label.place());
        assertEquals(new SourceId("fakes"),
                assertInstanceOf(DiagnosticPlace.InSource.class, label.place()).source(),
                "which it reads off the region rather than holding beside it");
    }

    /**
     * And a place cannot be built claiming a source its region was not read from.
     *
     * <p>Held as a pair the two could disagree, and the rule that they do not would be a habit of
     * whoever built one. It is the same defect as the one this whole area is about, and a new type
     * is not an exemption from it — the source is one component's to answer.
     */
    @Test
    void aPlaceCannotClaimASourceItsRegionWasNotReadFrom() {
        assertThrows(DiagnosticPlace.NotOnePlace.class,
                () -> new DiagnosticPlace.InSource(new Region(at(ROWS, 3, 3),
                        new SourcePos(3, 7, new SourceId("fakes")))));
        assertThrows(DiagnosticPlace.NotAPlace.class,
                () -> new DiagnosticPlace.InSource(Region.ofWidth(new SourcePos(3, 3), 4)));
    }

    /** And that survives being read: what the renderer resolves a source for is the label's own,
     *  whatever file the diagnostic as a whole is filed under. */
    @Test
    void theSourceALabelNamesReachesTheRenderer() {
        Asked asked = resolver();
        new HumanRenderer(false).render(
                new Located(withSecondary("fakes"), ReportContext.inFile(new SourceId("rows"))), asked.resolver(), Locale.ENGLISH);

        assertTrue(asked.ids().contains(new SourceId("fakes")),
                () -> "the label's own source is what was read: " + asked.ids());
    }

    /**
     * A region nobody placed is refused, rather than read against wherever the label ends up.
     *
     * <p>It used to mean "the diagnostic's file", which is right for a position made by hand and
     * wrong for one read out of a text this compile has no file for — and nothing downstream could
     * tell the two apart, because both of them are a null. So the reading is gone, and the position
     * that carried it is a caller that has a place to name and has not named it.
     */
    @Test
    void aRegionNamingNoSourceIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new LabeledRegion(Region.ofWidth(new SourcePos(3, 3), 4),
                        new NameMessage.WriteItOnItsOwn("x")));
    }

    /**
     * Unless the position says why it names none: a clause of a module this compile holds no file
     * for is somewhere, and the label says where in words rather than pointing.
     */
    @Test
    void aRegionOutOfSightBecomesALabelWithNothingToPointAt() {
        SourcePos there = Placement.whatAModulePublished(
                new SourceProvenance.APublishedModule("lib.rule")).at(3, 3);
        LabeledRegion label = new LabeledRegion(Region.ofWidth(there, 4),
                new NameMessage.WriteItOnItsOwn("x"));

        assertEquals(new DiagnosticPlace.Unavailable(
                        new SourceProvenance.APublishedModule("lib.rule")),
                label.place());
        assertFalse(label.place() instanceof DiagnosticPlace.InSource,
                "there is nowhere to send a reader");
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    private static int count(List<SourceId> asked, String id) {
        return (int) asked.stream().filter(each -> id.equals(each.value())).count();
    }
}
