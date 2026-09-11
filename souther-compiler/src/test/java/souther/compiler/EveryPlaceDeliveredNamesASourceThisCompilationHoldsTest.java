package souther.compiler;

import souther.compiler.diag.Primary;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.report.AdequacyReport;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.source.SourceId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every place this compilation hands a reader is one of its own sources.
 *
 * <p>The types say this: a citation that offers a position offers one whose placement names a source,
 * and a placement that names none has no position to offer. This says it from the other end — over a
 * compile that reaches out of sight, with the reports and the adequacy document read the way a
 * terminal and a build read them, counting the places that come out and checking each against the
 * sources the compile was handed.
 *
 * <p>Which is not the same claim as the type's. A type says a value cannot be built; this says the
 * values that were built are the ones that reach a reader, and that nothing on the way turned a
 * position that named a source into a report about a file nobody has. The count is what #762 measured
 * of itself and what this issue asked for: delivered places claiming somewhere this compilation does
 * not hold, zero.
 *
 * <p>An assertion over what came out is green when nothing came out. So the walk is measured first —
 * how many places it saw, and that some of them are about code out of sight — and the check is on the
 * same numbers. A fixture that stopped reaching a published module would fail here rather than pass
 * quietly.
 */
class EveryPlaceDeliveredNamesASourceThisCompilationHoldsTest {

    /** A module this compile has no file for, built as its own project's build would — and needing
     *  one this compile does not put on the path, so reading it back has something to report about a
     *  declaration written where there is no file. */
    private static Map<String, ClassFileImage> published() {
        Map<String, ClassFileImage> deep = Compiler.compile("""
                module lib.deep exposing ( Deep )
                data Deep = String
                """);
        return Compiler.compileModules(List.of("""
                module lib.held exposing ( Held )
                import lib.deep ( Deep )

                data Held = { deep: Deep }
                """), ModulePath.of(deep));
    }

    /** A compile that reaches that module, so a report about it has to be said somewhere here. */
    private static Compilation reaching() {
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.uses
                import lib.held ( Held )

                data Page = { held: Held }
                """), ModulePath.of(published()));
        compilation.answerEverything();
        return compilation;
    }

    /**
     * A compile that calls the standard library and gets it wrong, so what it reports is about code
     * written where no compile that calls it holds a file.
     */
    private static Compilation overTheLibrary() {
        Compilation compilation = Compilation.ofSource("""
                module app.counts

                let counted (ns: List<Int>): Int = List.length(List.filter(n -> n, ns))
                """, "Main");
        compilation.answerEverything();
        return compilation;
    }

    /** A model of this compile's own with a rule its rows do not reach, so the adequacy document has
     *  places to write. */
    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource("""
                module example.trip

                data Amount = Int
                    invariant value >= 0

                data Submitted = { cost: Amount }
                data Waiting = { cost: Amount }

                behavior submit : (cost: Amount) -> Submitted | Waiting
                    constructs Submitted, Waiting

                let submit (cost) = {
                    guard cost.value <= 100 else Waiting { cost = cost }
                    Submitted { cost = cost }
                }

                example submit
                    | (Amount(50)) -> Submitted { cost = Amount(50) }
                """, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Level.ALL);
        compilation.answerEverything();
        return compilation;
    }

    /** What a place a reader is offered says about which file it is in. */
    private record Delivered(String from, QuotedFrom quotedFrom) {}

    @Test
    void noPlaceADiagnosticOffersIsOutsideTheSourcesThisCompilationWasHanded() {
        Compilation compilation = reaching();
        Set<SourceId> held = new LinkedHashSet<>(compilation.sourceIds());
        List<Delivered> places = placesIn(compilation);

        assertFalse(places.isEmpty(), "the fixture reports something, or this checks nothing");
        assertEquals(List.of(), places.stream()
                        .filter(d -> !(d.quotedFrom()
                                instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file))
                                || !held.contains(file))
                        .toList(),
                "a place a reader is offered is in a source this compilation holds");
    }

    /** And the compile really does reach out of sight, so the check above is about the case it was
     *  written for rather than about a run in which nothing left the reader's own files. */
    @Test
    void andTheCompileReachesCodeItHoldsNoFileFor() {
        Compilation compilation = reaching();

        assertTrue(citations(compilation).stream().anyMatch(c -> c instanceof Citation.Elsewhere),
                "the fixture reads a module off the path, so something it reports is about code"
                        + " written where this compile has no file");
    }

    /**
     * And a report about it is offered a place, in a file this compilation holds.
     *
     * <p>The check above is satisfied by a compile that offers no places at all, which is what a
     * report about code out of sight would be if nothing anchored it — the type would keep it
     * honest and the reader would be told nothing. So this is the other half: the anchoring is still
     * happening, and what it produced is a citation that has a position and a position in a file the
     * compile was handed.
     */
    @Test
    void andAReportAboutItIsOfferedAPlaceInAFileTheReaderHolds() {
        Compilation compilation = reaching();
        Set<SourceId> held = new LinkedHashSet<>(compilation.sourceIds());

        List<Citation.Reached> reached = citations(compilation).stream()
                .filter(Citation.Reached.class::isInstance).map(Citation.Reached.class::cast)
                .toList();
        assertFalse(reached.isEmpty(),
                "a report about a module off the path is said where a source of this compile"
                        + " reaches it");
        assertEquals(List.of(), reached.stream()
                        .filter(r -> !(r.at().quotedFrom()
                                instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file))
                                || !held.contains(file))
                        .toList(),
                "and that place is one of this compilation's own sources");
    }

    /**
     * Every citation a report delivers either sends a reader to a file this compilation holds, or
     * says where the code came from.
     *
     * <p>The disjunction rather than one arm of it. A test naming a single arm is green whenever the
     * fixture stops producing that arm — which is how a walk comes to check nothing — and what is
     * actually being claimed spans them: a reader is never left with a report that points nowhere
     * and says nothing about why.
     *
     * <p>Over two compiles, because the two halves come from different places: one reaching a module
     * off the path, one reaching the standard library, which every compile reaches and none holds a
     * file for.
     */
    @Test
    void everyCitationDeliveredEitherPointsSomewhereHeldOrSaysWhereTheCodeIs() {
        for (Compilation compilation : List.of(reaching(), overTheLibrary())) {
            Set<SourceId> held = new LinkedHashSet<>(compilation.sourceIds());
            List<Citation> delivered = citations(compilation);

            assertFalse(delivered.isEmpty(), "the fixture reports something, or this checks nothing");
            assertEquals(List.of(), delivered.stream().filter(citation -> switch (citation) {
                case Citation.Written w -> !isHeld(w.at(), held);
                case Citation.Reached r -> !isHeld(r.at(), held);
                case Citation.Unplaced _ -> true;
                case Citation.UnplacedElsewhere u -> u.provenance().reachedBy().isEmpty();
                case Citation.OutOfSight out -> out.provenance().reachedBy().isEmpty();
            }).toList(), "a reader is sent to a file this compilation holds, or told where the code"
                    + " is written");
        }
    }

    private static boolean isHeld(SourcePos at, Set<SourceId> held) {
        return at.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file)
                && held.contains(file);
    }

    /** The document a build reads says the same. Every place in it is written under a source the
     *  document itself explains, which is what a consumer looks the identity up in. */
    @Test
    void everyPlaceTheAdequacyDocumentWritesIsASourceItNames() {
        Compilation compilation = measured();
        JsonNode document = JsonMapper.builder().build()
                .readTree(AdequacyReport.of(compilation)
                        .json(souther.compiler.diag.SourceRendering.namedByIdentity(compilation.texts())));

        Set<String> named = new LinkedHashSet<>();
        document.get("sources").propertyNames().forEach(named::add);
        List<String> written = new ArrayList<>();
        collectSourceIds(document, written);

        assertFalse(written.isEmpty(), "the fixture writes places, or this checks nothing");
        assertEquals(List.of(), written.stream().filter(id -> !named.contains(id)).toList(),
                "a place this document writes is one the document says what to call");
    }

    private static void collectSourceIds(JsonNode node, List<String> into) {
        if (node.isObject()) {
            JsonNode at = node.get("at");
            if (at != null && at.get("sourceId") != null) {
                into.add(at.get("sourceId").asString());
            }
            node.propertyStream().forEach(e -> collectSourceIds(e.getValue(), into));
        } else if (node.isArray()) {
            node.values().forEach(each -> collectSourceIds(each, into));
        }
    }

    /** Every place the reports offer a reader: the caret, and each label that points somewhere. */
    private static List<Delivered> placesIn(Compilation compilation) {
        List<Delivered> places = new ArrayList<>();
        for (Db.Found found : compilation.reports()) {
            Diagnostic said = found.report().diagnostic();
            // Only a caret a reader can be sent to. A report pointing at nothing, or into a text
            // this compilation cannot name, is offering nobody a place — which is what the arms
            // say, so there is nothing here to test a region for.
            if (said.primary() instanceof Primary.InSource(DiagnosticPlace.InSource place)) {
                places.add(new Delivered("caret", place.region().start().quotedFrom()));
            }
            for (LabeledRegion label : said.secondary()) {
                if (label.place() instanceof DiagnosticPlace.InSource in) {
                    places.add(new Delivered("label", in.region().start().quotedFrom()));
                }
            }
        }
        return places;
    }

    /** What every report says about where the code it is about is written. */
    private static List<Citation> citations(Compilation compilation) {
        List<Citation> citations = new ArrayList<>();
        for (Db.Found found : compilation.reports()) {
            // Of the reports that point somewhere. One that points at nothing says where its code
            // is on the primary itself, and has no position to project a citation from.
            if (found.report().diagnostic().primary()
                    instanceof Primary.InSource(DiagnosticPlace.InSource place)) {
                citations.add(Citation.of(place.region().start()));
            }
        }
        return citations;
    }
}
