package souther.compiler;

import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import souther.compiler.diag.Citation;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Region;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.Located;
import souther.compiler.diag.Placement;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.diag.DiagnosticPlace;
import souther.compiler.diag.Primary;
import souther.compiler.diag.WhereCodeIsWritten;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.meta.ModuleReadback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A module read off the class path is read from text this compile put back together out of what the
 * module published, so a coordinate in it is a line and a column of a file nobody holds. A report
 * found there is said at the nearest place on the way to that module a source of this compilation
 * writes, and names where the code is rather than pointing at it.
 *
 * <p>Both halves matter. Quoted as it stands, the coordinate lands wherever those numbers happen to
 * fall in the file the author is looking at — a compile of one source has no way to tell an untagged
 * report from one about no file at all — and filed as it stands it lands nowhere, which left a
 * failing compile answering no classes and no diagnostics.
 */
class AReportAboutAModuleOffThePathIsSaidWhereItWasReachedTest {

    /** A module compiled against {@code path}, as its own project's build produced it. */
    private static Map<String, ClassFileImage> built(String source, Map<String, ClassFileImage> path) {
        return Compiler.compileModules(List.of(source), ModulePath.of(path));
    }

    private static Map<String, ClassFileImage> and(Map<String, ClassFileImage> one, Map<String, ClassFileImage> other) {
        Map<String, ClassFileImage> both = new java.util.LinkedHashMap<>(one);
        both.putAll(other);
        return both;
    }

    private static Compilation reading(String source, Map<String, ClassFileImage> path) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.of(path));
        compilation.answerEverything();
        return compilation;
    }

    /** A module holding one thing out of another, as its own project's build produced it. */
    private static Map<String, ClassFileImage> holding(String name, String type, String from,
                                               String imported, Map<String, ClassFileImage> path) {
        return built("""
                module %s exposing ( %s )
                import %s ( %s )

                data %s = { x: %s }
                """.formatted(name, type, from, imported, type, imported), path);
    }

    /** The classes of {@code lib.deep}, which every path here leaves out. */
    private static Map<String, ClassFileImage> deep() {
        return Compiler.compile("""
                module lib.deep exposing ( Deep )
                data Deep = String
                """);
    }

    /** The declaring project's build of {@code lib.held}, which needs {@code lib.deep}. */
    private static Map<String, ClassFileImage> held() {
        return built("""
                module lib.held exposing ( Held )
                import lib.deep ( Deep )

                data Held = { deep: Deep }
                """, deep());
    }

    @Test
    void itIsSaidAtTheImportThatNamedTheModule() {
        // `lib.held` is on the path and the module it needs is not, so reading it back has something
        // to report about a declaration written where this compile has no file.
        String stands = """
                module app.uses




                import lib.held ( Held )

                data Page = { held: Held }
                """;
        Compilation compilation = reading(stands, held());

        SourcePos said = whereTheReportAboutIsSaid(compilation, "lib.held");
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(new SourceId("0")), said.quotedFrom(),
                "a reader can only be sent to a file this compile holds");
        assertEquals(6, WhereItSits.in(stands, said).line(), "the import line naming the module, not a line of the module");
        assertEquals(1, WhereItSits.in(stands, said).column());
    }

    /**
     * The position says the code is elsewhere, so no surface reads it as the place — and it says
     * this compile met it somewhere a reader holds, which is what the anchoring put there.
     */
    @Test
    void theCoordinateStandsInForCodeWrittenInThatModule() {
        Compilation compilation = reading("""
                module app.uses
                import lib.held ( Held )

                data Page = { held: Held }
                """, held());

        Citation citation = Citation.of(whereTheReportAboutIsSaid(compilation, "lib.held"));

        Citation.Reached reached = assertInstanceOf(Citation.Reached.class, citation);
        assertEquals("lib.held", reached.provenance().reachedBy());
        assertInstanceOf(QuotedFrom.ASourceThisCompileHolds.class, reached.at().quotedFrom(),
                "a citation offering a place offers one in a file this compilation holds");
    }

    /**
     * A module reached only through another off the path inherits that one's place. There is no
     * import line naming it anywhere a reader here can look, and the one naming the dependency that
     * led there is the nearest thing there is.
     */
    @Test
    void oneReachedThroughAnotherIsSaidAtTheImportThatLedThere() {
        Map<String, ClassFileImage> held = held();
        String stands = """
                module lib.front exposing ( Front )
                import lib.held ( Held )

                data Front = { held: Held }
                """;
        Map<String, ClassFileImage> front = built(stands, and(held, deep()));

        Compilation compilation = reading("""
                module app.uses


                import lib.front ( Front )

                data Page = { front: Front }
                """, and(held, front));

        SourcePos said = whereTheReportAboutIsSaid(compilation, "lib.held");
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(new SourceId("0")), said.quotedFrom());
        assertEquals(4, WhereItSits.in(stands, said).line(), "the import of the dependency that led to it");
    }

    /** The route this was found on. The report is about a module the caller has no file for, and it
     *  still reaches the file the caller does have. */
    @Test
    void itIsPublishedOnTheSourceThatReachedIt() {
        Compilation compilation = reading("""
                module app.uses
                import lib.held ( Held )

                data Page = { held: Held }
                """, held());

        List<Located> onTheSource = compilation.diagnostics().get(new SourceId("0"));
        assertNotNull(onTheSource);
        assertFalse(onTheSource.isEmpty(),
                "a compile that emitted nothing said nothing about why");
    }

    /**
     * Every error reaches a file. What the command line stops for and what an editor marks are the
     * same set, and a report that reached neither is one the author is never told about — the
     * walk over the path is one question about the whole compilation and names no module of its
     * own, so what it finds has to say where it is said.
     *
     * <p>Measured on the reports rather than on the classes. A compile that emits nothing and says
     * nothing is what this looked like, but which of those is the defect is the second one: what a
     * build produces is its own question, and one that legitimately produces no classes would make
     * the first reading say this had been fixed.
     */
    @Test
    void noErrorIsLeftWithNowhereToBeSaid() {
        Compilation compilation = reading("""
                module app.uses
                import lib.held ( Held )

                data Page = { held: Held }
                """, held());

        List<String> unreachable = new ArrayList<>();
        for (Db.Found found : compilation.reports()) {
            if (found.report().isError() && compilation.publishSourceIdsOf(found).isEmpty()) {
                unreachable.add(found.report().diagnostic().code() + " about " + found.module());
            }
        }
        assertEquals(List.of(), unreachable, "an error the author is never shown");
    }

    /**
     * A dependency two files import is reached by both, and both authors are told.
     *
     * <p>Neither import is the premise the other is measured by, and neither author has anything
     * different to do about it. Marking one of them leaves the other looking at a file that is fine
     * while the build fails — and which of the two it would be is the order the sources happened to
     * arrive in.
     */
    @Test
    void everySourceThatReachesItIsTold() {
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.a exposing ( A )
                import lib.held ( Held )

                data A = { held: Held }
                """, """
                module app.b exposing ( B )
                import lib.held ( Held )

                data B = { held: Held }
                """), ModulePath.of(held()));
        compilation.answerEverything();

        Map<SourceId, List<Located>> byId = compilation.diagnostics();
        assertFalse(byId.get(new SourceId("0")).isEmpty(), "the source that was reached first");
        assertFalse(byId.get(new SourceId("1")).isEmpty(), "the one that reaches it just as much");
    }

    /**
     * A route found after the module it leads to was read counts as much as the one found before.
     *
     * <p>The walk reads each module once, and a place written down when it was read is the places it
     * had by then. Here one source names the shared dependency outright and the other reaches it
     * through a dependency of its own, so the second route arrives after the shared module has been
     * read and everything under it settled — and the author of the second file was told nothing
     * about a missing dependency their import reaches. Which file that is comes down to nothing
     * either author wrote.
     */
    @Test
    void aRouteFoundAfterTheModuleWasReadCountsToo() {
        Map<String, ClassFileImage> shared = built("""
                module lib.shared exposing ( Shared )
                import lib.deep ( Deep )

                data Shared = { deep: Deep }
                """, deep());
        Map<String, ClassFileImage> between = built("""
                module lib.between exposing ( Between )
                import lib.shared ( Shared )

                data Between = { shared: Shared }
                """, and(shared, deep()));

        Compilation compilation = Compilation.ofSources(List.of("""
                module app.a exposing ( A )
                import lib.shared ( Shared )

                data A = { shared: Shared }
                """, """
                module app.b exposing ( B )
                import lib.between ( Between )

                data B = { between: Between }
                """), ModulePath.of(and(shared, between)));
        compilation.answerEverything();

        Map<SourceId, List<Located>> byId = compilation.diagnostics();
        assertEquals(byId.get(new SourceId("0")).size(), byId.get(new SourceId("1")).size(),
                "the file that reaches it the long way round is told the same things");
    }

    /**
     * Two routes of different lengths meeting at one module carry both origins on down.
     *
     * <p>{@code app.a} reaches the meeting point through one module and {@code app.b} through two,
     * so the meeting point is read while only the short route is known and everything under it is
     * settled from that. What the long route adds arrives afterwards and has to travel the rest of
     * the way — reaching a module is reaching everything it reaches, and where the two routes happen
     * to be the same length is not what decides it.
     */
    @Test
    void routesOfDifferentLengthsBothReachWhatTheyMeetOver() {
        Map<String, ClassFileImage> leaf = Compiler.compile("""
                module lib.leaf exposing ( Leaf )
                data Leaf = String
                """);
        Map<String, ClassFileImage> meeting = holding("lib.meeting", "Meeting", "lib.leaf", "Leaf", leaf);
        Map<String, ClassFileImage> shortWay = holding("lib.short", "Short", "lib.meeting", "Meeting",
                and(meeting, leaf));
        Map<String, ClassFileImage> middle = holding("lib.middle", "Middle", "lib.meeting", "Meeting",
                and(meeting, leaf));
        Map<String, ClassFileImage> longWay = holding("lib.long", "Long", "lib.middle", "Middle",
                and(middle, and(meeting, leaf)));

        // everything but the leaf, so what is missing is under the module the two routes meet at
        Compilation compilation = Compilation.ofSources(List.of("""
                module app.a exposing ( A )
                import lib.short ( Short )

                data A = { x: Short }
                """, """
                module app.b exposing ( B )
                import lib.long ( Long )

                data B = { x: Long }
                """), ModulePath.of(and(and(meeting, shortWay), and(middle, longWay))));
        compilation.answerEverything();

        Map<SourceId, List<Located>> byId = compilation.diagnostics();
        assertEquals(byId.get(new SourceId("0")).size(), byId.get(new SourceId("1")).size(),
                "the file that reaches the meeting point the long way round is told the same things");
    }

    /**
     * Two modules each needing the same absent one are two things to be told.
     *
     * <p>They are missing from two places. An author who reaches one of them has not reached the
     * other, so one report pointing at both imports would say the code is written in a module that
     * import never arrives at — a statement about a place, and false. What is absent is the edge and
     * not the module.
     */
    @Test
    void twoModulesNeedingTheSameAbsentOneAreTwoFindings() {
        Map<String, ClassFileImage> left = holding("lib.left", "Left", "lib.deep", "Deep", deep());
        Map<String, ClassFileImage> right = holding("lib.right", "Right", "lib.deep", "Deep", deep());

        Compilation compilation = Compilation.ofSources(List.of("""
                module app.a exposing ( A )
                import lib.left ( Left )

                data A = { x: Left }
                """, """
                module app.b exposing ( B )
                import lib.right ( Right )

                data B = { x: Right }
                """), ModulePath.of(and(left, right)));
        compilation.answerEverything();

        Set<String> saidAbout = new LinkedHashSet<>();
        for (Db.Found found : compilation.reports()) {
            SourcePos at = ((Primary.InSource) found.report().diagnostic().primary()).place().region().start();
            if (at == null || !(Citation.of(at) instanceof Citation.Elsewhere elsewhere)) {
                continue;
            }
            String stands = elsewhere.provenance().reachedBy();
            saidAbout.add(stands);
            // Every file it is said in, and not only the one the caret is in: a second place is
            // said as a labelled region, and claiming a file that never reaches this module is
            // exactly what putting both imports on one report would do.
            assertEquals(List.of(new SourceId("lib.left".equals(stands) ? "0" : "1")),
                    compilation.publishSourceIdsOf(found),
                    "`" + stands + "` is not reached from the other file at all");
        }
        assertEquals(Set.of("lib.left", "lib.right"), saidAbout,
                "each of them needs it, and each of them is a thing to be told");
    }

    /**
     * A module on the path that took a name no module may take is refused, and the refusal is said
     * where the module was reached from.
     *
     * <p>The other thing this walk finds on its own. It is refused rather than read, so it has no
     * declarations for anything else to trip over and nothing later says it again — which is what
     * left it as the one report here still carrying a coordinate in a file nobody holds.
     */
    @Test
    void aModuleOnThePathThatTookAReservedNameIsRefusedWhereItWasReached() {
        Compilation core = Compilation.ofCoreSource("""
                module souther.taken exposing ( X )
                data X = Int
                """);
        core.answerEverything();

        Compilation compilation = reading("""
                module app.uses
                import souther.taken ( X )

                data A = { x: X }
                """, core.classes());

        List<Located> onTheSource = compilation.diagnostics().get(new SourceId("0"));
        assertNotNull(onTheSource);
        // That one, and only that one. A module the path holds and this compilation refuses is
        // still a module it has heard of, so an importer of it is not also told there is no such
        // thing — two reports that cannot both be true.
        assertEquals(List.of("E1502"),
                onTheSource.stream().map(said -> said.diagnostic().code()).toList());
    }

    /**
     * The same unavailable problem is told once.
     *
     * <p>A finding inside a module's published text has nowhere to point ({@link Primary.Unavailable}),
     * so what told two of them apart was a line of a text nobody holds. Two findings agreeing on the
     * rule, on the values and on the module are then the same diagnostic, and the store keeps one:
     * there is nothing left for an author to tell them apart by, at the caret or in the sentence.
     *
     * <p>Which is a claim about identity and not about the walk. That both fields were read is a
     * separate question with a fixture of its own
     * ({@link #everyFieldOfAPublishedModuleIsRead}), because a walk that stopped at the first field
     * would leave this one green.
     */
    @Test
    void theSameUnavailableProblemIsToldOnce() {
        String stands = """
                module app.uses
                import lib.twice ( Twice )

                data Page = { twice: Twice }
                """;
        Compilation compilation = reading(stands, twiceNaming("lib.deep.Deep", "lib.deep.Deep"));

        List<Db.Found> found = compilation.db().allReports();
        assertEquals(List.of("E1506"),
                found.stream().map(f -> f.report().diagnostic().code()).toList(),
                "one problem, however many places the module writes the name");

        Diagnostic asFound = found.get(0).report().diagnostic();
        assertInstanceOf(Primary.Unavailable.class, asFound.primary(),
                "found inside the module's own text, so there is nowhere to send a reader");
        assertEquals(new WhereCodeIsWritten.Elsewhere(ModuleReadback.provenanceOf("lib.twice")),
                asFound.whereItsCodeIsWritten(),
                "the code is in the module that was read back, not in the file it is said on");

        Diagnostic said = whereTheReportAboutIsSaidIn(compilation, "lib.twice");
        Citation.Reached reached = assertInstanceOf(Citation.Reached.class,
                Citation.of(((Primary.InSource) said.primary()).place().region().start()), "read for where it may be said, the caret is the import");
        assertEquals(2, WhereItSits.in(stands, reached.at()).line(), "the import line naming it");

        assertEquals(1, compilation.diagnostics().get(new SourceId("0")).size(),
                "one thing to be told, said once");
    }

    /**
     * Every field of a published module is read, not the first.
     *
     * <p>The other half of what {@link #theSameUnavailableProblemIsToldOnce} used to assert, with a
     * fixture that can carry it: the two fields name two missing types, so the two findings differ
     * in the values they are about. Nothing downstream may merge them — the store tells reports
     * apart by what they say — so this stays a claim about the walk however identity is decided.
     */
    @Test
    void everyFieldOfAPublishedModuleIsRead() {
        Compilation compilation = reading("""
                module app.uses
                import lib.twice ( Twice )

                data Page = { twice: Twice }
                """, twiceNaming("lib.deep.Deep", "lib.deep.Other"));

        assertEquals(List.of("Deep", "Other"),
                compilation.db().allReports().stream()
                        .map(f -> String.valueOf(f.report().diagnostic().values().get("name")))
                        .toList(),
                "both fields were read, and each says which name it could not find");
    }

    /**
     * A published module whose two fields name {@code first} and {@code second}, built against a
     * dependency that had them and put on the path beside one that no longer does.
     */
    private static Map<String, ClassFileImage> twiceNaming(String first, String second) {
        Map<String, ClassFileImage> withThem = Compiler.compile("""
                module lib.deep exposing ( Deep, Other )
                data Deep = String
                data Other = String
                """);
        Map<String, ClassFileImage> twice = built("""
                module lib.twice exposing ( Twice )
                data Twice = { a: %s, b: %s }
                """.formatted(first, second), withThem);
        // the dependency is rebuilt without either, so both field types fail to resolve
        Map<String, ClassFileImage> withoutThem = Compiler.compile("""
                module lib.deep exposing ( Gone )
                data Gone = String
                """);
        return and(withoutThem, twice);
    }

    /**
     * Moving the caret takes nothing away from the labels the report already had.
     *
     * <p>Each of them says where it is on its own ({@code DiagnosticPlace}), so none of them meant
     * anything different while the caret was somewhere else. They used to be filtered here, because
     * one naming no source was read in whichever file the diagnostic ended up filed under — and
     * that reading is what made a label about a clause nobody holds a file for land on a line of
     * the caller's. A label that cannot be pointed at is now said in words instead of dropped, so
     * there is nothing left for moving a caret to invalidate.
     */
    @Test
    void movingTheCaretKeepsTheLabelsTheReportAlreadyHad() {
        // Built the way a report about a module off the path is: pointing into that module's own
        // text. One pointing into a file the reader holds says its code is there, and moving such a
        // report is not something to ask for — a caret moving is not the code moving.
        Diagnostic said = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(Placement.whatAModulePublished(
                        new SourceProvenance.APublishedModule("lib.held")).at(1, 1))
                .secondary(Region.ofWidth(new SourcePos(3, 3, new SourceId("0")), 4),
                        new NameMessage.WriteItOnItsOwn("x"))
                .build();

        assertEquals(1, said.secondary().size(), "the label is there to begin with");

        Diagnostic moved = said.reachedFrom(List.of(new SourcePos(2, 1, new SourceId("0"))),
                new SourceProvenance.APublishedModule("lib.held"),
                new ModuleMessage.ItIsReachedFromHereToo());

        assertEquals(1, moved.secondary().size(), "and survives the move, saying what it said");
        assertEquals(said.secondary().get(0).place(), moved.secondary().get(0).place(),
                "in the place it named for itself");
    }

    /** A region nobody placed is refused rather than read against wherever it is shown. */
    @Test
    void aLabelOverARegionNamingNoSourceIsRefused() {
        Diagnostic.Builder building = Diagnostic.say(new NameMessage.NoValueOfThatNameInScope("x"))
                .at(new SourcePos(1, 1, new SourceId("0")));

        assertThrows(IllegalArgumentException.class,
                () -> building.secondary(Region.ofWidth(new SourcePos(3, 3), 4),
                        new NameMessage.WriteItOnItsOwn("x")));
    }

    /** The report about {@code module}, read for where it may be said. */
    private static Diagnostic whereTheReportAboutIsSaidIn(Compilation compilation, String module) {
        for (Db.Found found : compilation.reports()) {
            if (module.equals(found.module())) {
                return found.report().diagnostic();
            }
        }
        throw new AssertionError("nothing was reported about " + module);
    }

    /** Where the report about {@code module} sends a reader, of the reports that send one at all.
     *  A report about a module off the path is anchored before it is read back here, so a run in
     *  which none of them points anywhere is a fixture that stopped reaching the module. */
    private static SourcePos whereTheReportAboutIsSaid(Compilation compilation, String module) {
        List<SourcePos> said = new ArrayList<>();
        for (Db.Found found : compilation.reports()) {
            if (module.equals(found.module())
                    && found.report().diagnostic().primary()
                            instanceof Primary.InSource(DiagnosticPlace.InSource place)) {
                said.add(place.region().start());
            }
        }
        assertFalse(said.isEmpty(), "nothing was reported about " + module);
        return said.get(0);
    }
}
