package souther.compiler;

import souther.compiler.diag.SourceRendering;
import souther.compiler.cst.SourceLayout;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.JsonRenderer;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One compile, every place it names, and each of them saying whether it is where the code is.
 *
 * <p>A body spliced in from out of sight is read against the caller's file, so everything a report
 * says about code inside it points at a call. The warning a build reads said so; the report a person
 * reads, the report a build reads and the JSON a diagnostic is read from printed the place and left
 * it at that. Nothing held them against each other, and each was written believing the place was the
 * place.
 *
 * <p>Two subjects rather than one, because there were two. An unreached arm is what was reported
 * — of {@code List.filter}, in the model this was found on. A line a guard drew is the other, and it
 * was found by looking for the first one's shape somewhere else: the same compile said
 * {@code comparison@7:22} two lines above an arm of the same body saying where it was written.
 *
 * <p>The two are no longer both out of sight. A caller answers for the lines a caller wrote, so a
 * comparison inside a library operation draws none here; the arms of that operation are still in the
 * tree that runs, so a place standing in for code out of sight is still a thing a report writes. The
 * second subject is held to where the reader can see it — this file, and another source of the same
 * compile — and out of sight is held to drawing nothing.
 *
 * <p>Of one compile, and every rendering held to the same declaration. Renderings read from separate
 * runs are compared by way of the compiler answering the same thing twice, which is a different
 * claim; and four tests each pinned to the same name agree by construction, so a fifth comparing
 * them to one another could not fail on its own.
 *
 * <p>No module path is needed. The standard library is out of sight of every compile, so this is what
 * any model that calls into it looks like.
 */
class EveryPlaceAReportNamesSaysWhereTheCodeIsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * {@code Int.abs} is written in {@code souther.int}, and this compile has no source for it.
     *
     * <p>One call, two subjects. Its {@code then} arm is owed a row and the one row here is
     * positive, so nothing goes through it; the comparison it forks on draws lines on {@code n} that
     * no row is at. Both are reported at the call on line 7.
     */
    private static final String OUT_OF_SIGHT = """
            module demo

            data Size = Int

            behavior sized : (n: Int) -> Size
                constructs Size
            let sized (n) = Size(Int.abs(n))

            example sized
                | "a positive one" : (5) -> Size(5)
            """;

    /** The control: the same two gaps with the fork written in the module's own source. Whatever the
     *  renderings gain above, they must not gain it here. */
    private static final String IN_SIGHT = """
            module demo

            data Size = Int

            behavior sized : (n: Int) -> Size
                constructs Size
            let sized (n) = if n >= 10 then Size(n) else Size(0)

            example sized
                | "a big one" : (50) -> Size(50)
            """;

    /**
     * The third state: written where this compile can show it, and not in the file the section is
     * about.
     *
     * <p>{@code HERE} and {@code OutOfSight} are the two a provenance has, and they are not the two a
     * report has to tell apart. A helper another module of the same compile wrote keeps the positions
     * it was written at, so its arm is {@code HERE} — in a file the reader holds, and not this one.
     * A report that named no file sent the reader to those numbers in the wrong source, which is what
     * making places into citations did until the citation stopped being built out of two source
     * identities at once.
     */
    private static final List<String> ANOTHER_SOURCE = List.of("""
            module up exposing ( clamp )

            let clamp (n: Int): Int =
                if n >= 0 then n else 0
            """, """
            module down

            import up ( clamp )

            data Size = Int

            behavior sized : (n: Int) -> Size
                constructs Size
            let sized (n) = Size(clamp(n))

            example sized
                | "a positive one" : (5) -> Size(5)
            """);

    private static final String DECLARATION = "Int.abs";

    // --- the arm, in each of the four renderings that say where it is -------------------------------

    @Test
    void theWarningNamesTheDeclarationTheArmIsWrittenIn() {
        Said said = saidAbout(OUT_OF_SIGHT);
        assertTrue(said.warning().contains("`" + DECLARATION + "`"),
                () -> "the warning says where the arm is written: " + said.warning());
    }

    @Test
    void theReportAPersonReadsNamesIt() {
        Said said = saidAbout(OUT_OF_SIGHT);
        assertTrue(said.armLine().contains("`" + DECLARATION + "`"),
                () -> "the line says where the arm is written: " + said.armLine());
    }

    @Test
    void theReportABuildReadsNamesIt() {
        JsonNode writtenAt = saidAbout(OUT_OF_SIGHT).writtenAt();
        assertNotNull(writtenAt, "the place says whether it is where the code is");
        assertEquals("outOfSight", writtenAt.get("kind").asString(),
                "the document says the place is a stand-in");
        assertEquals(DECLARATION, writtenAt.get("declaration").asString(),
                "and what it stands in for");
    }

    /**
     * The fourth, which the other three do not reach.
     *
     * <p>A diagnostic is rendered for a tool by {@link JsonRenderer} and for a person by the human
     * one, and only the second of those goes through anything the adequacy report also uses. Left to
     * the other three, taking {@code writtenAt} back out of the JSON a diagnostic is read from
     * changed nothing anybody could see.
     */
    @Test
    void theJsonADiagnosticIsReadFromNamesIt() {
        JsonNode region = JSON.readTree(saidAbout(OUT_OF_SIGHT).diagnosticJson()).get("region");
        assertNotNull(region, "the diagnostic points somewhere");
        JsonNode writtenAt = region.get("writtenAt");
        assertNotNull(writtenAt, "and says whether that is where the code is");
        assertEquals("outOfSight", writtenAt.get("kind").asString());
        assertEquals(DECLARATION, writtenAt.get("declaration").asString());
    }

    // --- and the line the guard drew, which is the same question one measure over -------------------

    /**
     * And a guard written out of sight draws no line in a model that calls it.
     *
     * <p>{@code Int.abs} forks at nought, and a reading made over a tree with that body spliced in
     * drew a line there and owed the caller a row either side of it. The number is that operation's
     * implementation: this model says nothing about nought, and a row at it is a row owed for how
     * the library happens to be written. So the reading keeps the operation standing and the lines
     * a caller is answerable for are the ones a caller wrote.
     *
     * <p>Which leaves the arm above as this model's whole subject. The tree that runs still holds
     * the arms of the spliced body — a run either goes through them or does not — so a place that
     * stands in for code out of sight is a thing a report still writes, and the four renderings
     * above are what hold it to saying so.
     */
    @Test
    void aGuardWrittenOutOfSightDrawsNoLineInAModelThatCallsIt() {
        Said said = saidAbout(OUT_OF_SIGHT);

        assertEquals(List.of(), said.boundaryOrigins(),
                "the lines a caller answers for are the ones a caller wrote");
        assertEquals(List.of(), said.boundaryLines(), said::armLine);
        assertNull(said.edge(), "and no edge warning points at a guard out of sight");
    }

    // --- the third state, which is neither of the two a provenance has ------------------------------

    /**
     * An arm of a helper another module of this compile wrote is named with its file.
     *
     * <p>Its provenance is {@code HERE} — the copy kept the positions it was written at, because the
     * reader holds that file. What the report must not do is print those numbers bare under a section
     * about another source, which is exactly what happened while a citation was built from a walk's
     * own source paired with a position from somewhere else.
     */
    @Test
    void anArmAnotherSourceOfThisCompileWroteIsNamedWithItsFile() {
        Elsewhere said = fromAnotherSource();

        assertTrue(said.armLine().contains("up.sou:"),
                () -> "the line names the file the arm is written in: " + said.armLine());
        assertEquals("up.sou", said.sourceOfTheArm(),
                "and the document points at that source, not at the one being reported on");
        assertEquals("here", said.writtenAt().get("kind").asString(),
                "the copy kept its own positions, so the place is the place");
    }

    /**
     * And so is a line drawn by a guard that module wrote.
     *
     * <p>The same question one measure over. A guard has no name, so what identifies it is where it
     * is written — and a report that printed the numbers bare named the section's file by omission.
     */
    @Test
    void aGuardAnotherSourceOfThisCompileWroteIsNamedWithItsFile() {
        Elsewhere said = fromAnotherSource();

        assertFalse(said.boundaryLines().isEmpty(), "the comparison drew lines");
        for (String line : said.boundaryLines()) {
            assertTrue(line.contains("comparison@up.sou:"),
                    () -> "the line names the file the fork is written in: " + line);
        }
    }

    /**
     * What a document says a guard's place is, it says with an identity the document explains.
     *
     * <p>The report a person reads leaves the file out where the section already names it — a heading
     * says which module, and a reader takes it from there. A document has no heading. So a place
     * written without its source is a line and a column belonging to nothing, and where a boundary is
     * the only place a report points at there is no other entry in `sources` to guess from.
     *
     * <p>All three states, because the one that was wrong is the ordinary one. Held against
     * another-source alone, a document that wrote the identity only when the file differed passed.
     */
    @Test
    void theDocumentSaysAGuardsPlaceWithAnIdentityItExplains() {
        // The model written in this file, and the one written in another source of this compile.
        // Not the one that calls out of sight: the lines a caller answers for are the ones a caller
        // wrote, so that model draws none.
        Said said = saidAbout(IN_SIGHT);
        assertFalse(said.boundaryOrigins().isEmpty(), "lines were drawn");
        for (String origin : said.boundaryOrigins()) {
            explained(origin, said.document().get("sources"));
        }
        Elsewhere elsewhere = fromAnotherSource();
        assertFalse(elsewhere.jsonOrigins().isEmpty(), "the comparison drew lines");
        for (String origin : elsewhere.jsonOrigins()) {
            assertFalse(origin.contains("up.sou"),
                    () -> "a document says an identity, not what a reader calls it: " + origin);
            explained(origin, elsewhere.sources());
        }
    }

    /** The place at the end of an origin, and the table entry that says which source it is in. */
    private static void explained(String origin, JsonNode sources) {
        String[] parts = origin.split(":");
        assertTrue(parts.length >= 3,
                () -> "a place a document writes names its source: " + origin);
        String id = parts[parts.length - 3];
        id = id.substring(id.lastIndexOf(' ') + 1);
        id = id.substring(id.lastIndexOf('@') + 1);
        assertNotNull(sources.get(id),
                () -> "`sources` explains every identity the document writes: " + origin);
    }

    /**
     * A rule with no name gets a sentence of its own rather than a phrase built for its slot.
     *
     * <p>A type and an invariant have names, which read the same in every language. A fork of a body
     * has none, and what filled the slot for it was English assembled in Java — so the Japanese
     * warning said "a guard が線を引いているのはそこです". The words belong to the catalog, where
     * every language has its own, and which construct it was is one of them.
     */
    @Test
    void aLineAForkDrewIsSaidInEveryLanguageItIsAskedIn() {
        Diagnostic edge = saidAbout(IN_SIGHT).edge();
        assertInstanceOf(ExampleMessage.NoRowIsAtThePointOfTheLineAConstructDrew.class, edge.said(),
                "a rule with no name reports its own message");
        assertFalse(DiagnosticRenderer.body(edge, Locale.JAPANESE).contains("a guard"),
                () -> "no English is assembled into a Japanese sentence: "
                        + DiagnosticRenderer.body(edge, Locale.JAPANESE));
    }

    // --- the controls -------------------------------------------------------------------------------

    @Test
    void anArmWrittenHereGainsNoneOfIt() {
        Said said = saidAbout(IN_SIGHT);

        assertFalse(said.warning().contains("no source for"),
                () -> "nothing to qualify, the arm being in a file the reader holds: "
                        + said.warning());
        assertFalse(said.armLine().contains("reached at"),
                () -> "the place is the place, so it is printed as one: " + said.armLine());
        assertNotNull(said.writtenAt(), "the place says whether it is where the code is");
        assertEquals("here", said.writtenAt().get("kind").asString());
        assertFalse(said.writtenAt().has("declaration"),
                "there is no declaration to name where the place is the place");
        // The other document too, and not only the one above. `here` is written rather than left
        // out, so an emitter that started omitting it would be caught by something — which the
        // out-of-sight test beside this one cannot do.
        JsonNode region = JSON.readTree(said.diagnosticJson()).get("region");
        assertEquals("here", region.get("writtenAt").get("kind").asString());
        assertFalse(region.get("writtenAt").has("declaration"));
        assertFalse(said.edgeSaid().contains("no source for"),
                () -> "a second region at a place the reader holds says nothing extra: "
                        + said.edgeSaid());
    }

    @Test
    void aForkWrittenHereGainsNoneOfIt() {
        Said said = saidAbout(IN_SIGHT);
        assertFalse(said.boundaryOrigins().isEmpty(), "the comparison drew lines");
        for (String origin : said.boundaryOrigins()) {
            assertTrue(origin.startsWith("comparison@"),
                    () -> "the fork is where the report says it is: " + origin);
        }
    }

    /**
     * The document answers whichever it is, everywhere it points.
     *
     * <p>Absence is what a report written before the key existed carries, so an emitter that wrote it
     * only where the answer was interesting would put "the code is here" and "nobody asked yet" under
     * one silence.
     */
    @Test
    void everyPositionTheDocumentWritesAnswersTheQuestion() {
        for (String model : List.of(OUT_OF_SIGHT, IN_SIGHT)) {
            List<JsonNode> places = new ArrayList<>();
            collectAt(saidAbout(model).document(), places);
            assertFalse(places.isEmpty(), "the document points somewhere");
            for (JsonNode at : places) {
                assertTrue(at.has("writtenAt"),
                        () -> "every place says whether it is where the code is: " + at);
            }
        }
    }

    // --- reading one compile ------------------------------------------------------------------------

    /**
     * What one compile says about where code is, in each rendering that says it.
     *
     * @param warning        the body of the {@code E1918} it reports, as every reader of a
     *                       diagnostic reads it — the terminal, an editor and the text an exception
     *                       carries all come from here
     * @param diagnosticJson that same diagnostic as a tool reads it, which comes from somewhere else
     * @param armLine        the line the human report prints under {@code branch}
     * @param at             where the machine-readable report puts the arm
     * @param boundaryLines  the lines it prints for edges no row is at
     * @param boundaryOrigins what that document says drew each of them
     * @param document       the report whole, for what is asked of every place in it
     */
    private record Said(String warning, String diagnosticJson, String armLine, JsonNode at,
                        List<String> boundaryLines, List<String> boundaryOrigins,
                        String edgeSaid, String edgeJson, Diagnostic edge, JsonNode document) {

        /** What the document says about where the arm is written, or null where it says nothing.
         *  Read and not asserted: whether it is there at all is one of the things under test, and a
         *  reader that demanded it would fail every test in this class over one rendering. */
        JsonNode writtenAt() {
            return at.get("writtenAt");
        }
    }

    /**
     * One compile per model, whatever asks.
     *
     * <p>Held rather than recompiled because the property under test is about one compile: renderings
     * taken from separate runs are compared by way of the compiler answering the same thing twice,
     * which is a different claim and not the one that was broken.
     */
    private static final Map<String, Said> SAID = new ConcurrentHashMap<>();

    /** The entries of one behavior's arm account that no row goes through, which is what the
     *  disposition of each says. */
    private static List<JsonNode> armsNoRowGoesThrough(JsonNode behavior) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode arm : behavior.get("branch").get("obligations")) {
            if ("unmet".equals(arm.get("disposition").asString())) {
                out.add(arm);
            }
        }
        return out;
    }

    private static Said saidAbout(String model) {
        return SAID.computeIfAbsent(model,
                EveryPlaceAReportNamesSaysWhereTheCodeIsTest::readEveryRenderingOnce);
    }

    private static Said readEveryRenderingOnce(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.warningsAt(Adequacy.Level.ALL));
        compilation.answerEverything();
        AdequacyReport report = AdequacyReport.of(compilation);

        List<Diagnostic> arms = new ArrayList<>();
        List<Diagnostic> edges = new ArrayList<>();
        for (Db.Found found : compilation.db().allReports()) {
            Diagnostic d = found.report().diagnostic();
            if ("E1918".equals(d.code())) {
                arms.add(d);
            }
            if ("E1916".equals(d.code()) && !d.secondary().isEmpty()) {
                edges.add(d);
            }
        }
        assertEquals(1, arms.size(), () -> "one arm is unreached: " + arms.size());
        Diagnostic arm = arms.get(0);

        List<String> human = report.human(SourceRendering.namedByIdentity(compilation.texts())).lines()
                .map(String::strip).toList();
        List<String> armLines = human.stream()
                // The sentence and not the mark it is printed under. What a build does about a
                // finding is written down now and is somebody else's test; this one is about the
                // place the sentence names, and reading the mark here would be a second holder of it.
                .filter(line -> line.contains("no row goes through")).toList();
        assertEquals(1, armLines.size(), () -> "one arm is unreached: " + armLines);
        List<String> boundaryLines = human.stream()
                .filter(line -> line.contains("no row is at")).toList();

        JsonNode document = JSON.readTree(report.json(SourceRendering.namedByIdentity(compilation.texts())));
        JsonNode behavior = document.get("modules").get(0).get("behaviors").get(0);
        List<JsonNode> unreached = armsNoRowGoesThrough(behavior);
        assertEquals(1, unreached.size(), () -> "one arm is unreached: " + unreached);
        List<String> origins = new ArrayList<>();
        behavior.get("partition").get("boundaries")
                .forEach(each -> origins.add(each.get("origin").asString()));

        SourceContext source = new SourceContext("m.sou", model, SourceLayout.of(model));
        return new Said(
                DiagnosticRenderer.body(arm, Locale.ENGLISH),
                new JsonRenderer().render(arm, source, Locale.ENGLISH),
                armLines.get(0), unreached.get(0).get("at"),
                boundaryLines, origins,
                // A model whose lines are all drawn where the reader can see them has no edge
                // warning pointing at a stand-in, and that is a thing this class asks about rather
                // than a thing it needs. Read here and demanded by whoever is asking.
                edges.isEmpty() ? null
                        : new HumanRenderer(false).render(edges.get(0), source, Locale.ENGLISH),
                edges.isEmpty() ? null
                        : new JsonRenderer().render(edges.get(0), source, Locale.ENGLISH),
                edges.isEmpty() ? null : edges.get(0), document);
    }

    /** What the two renderings say about an arm written in another source of this compile. */
    private record Elsewhere(String armLine, String sourceOfTheArm, JsonNode writtenAt,
                             List<String> boundaryLines, List<String> jsonOrigins,
                             JsonNode sources) {}

    /**
     * A compile of two sources, so that the section a report is about and the file an arm is written
     * in are different files this compile holds.
     *
     * <p>Named by {@link SourceNameResolver}, because what a reader is shown a source as is a fact
     * about the set in front of them. The identities are the compile's own, so the document is read
     * through its {@code sources} table the way any consumer reads one.
     */
    private static Elsewhere fromAnotherSource() {
        Compilation compilation = Compilation.ofSources(ANOTHER_SOURCE,
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.warningsAt(Adequacy.Level.ALL));
        compilation.answerEverything();
        SourceRendering names = new SourceRendering(
                id -> "0".equals(id.value()) ? "up.sou" : "down.sou", compilation.texts());
        AdequacyReport report = AdequacyReport.of(compilation);

        List<String> lines = report.human(names).lines().map(String::strip)
                // The sentence and not the mark it is printed under. What a build does about a
                // finding is written down now and is somebody else's test; this one is about the
                // place the sentence names, and reading the mark here would be a second holder of it.
                .filter(line -> line.contains("no row goes through")).toList();
        assertEquals(1, lines.size(), () -> "one arm is unreached: " + lines);

        JsonNode document = JSON.readTree(report.json(names));
        JsonNode down = null;
        for (JsonNode module : document.get("modules")) {
            if ("down".equals(module.get("module").asString())) {
                down = module;
            }
        }
        assertNotNull(down, "the module the row names is reported");
        List<JsonNode> unreached = armsNoRowGoesThrough(down.get("behaviors").get(0));
        assertEquals(1, unreached.size(), () -> "one arm is unreached: " + unreached);
        JsonNode at = unreached.get(0).get("at");
        List<String> edges = report.human(names).lines().map(String::strip)
                .filter(line -> line.contains("no row is at")).toList();
        List<String> jsonOrigins = new ArrayList<>();
        down.get("behaviors").get(0).get("partition").get("boundaries")
                .forEach(each -> jsonOrigins.add(each.get("origin").asString()));
        return new Elsewhere(lines.get(0),
                document.get("sources").get(at.get("sourceId").asString()).asString(),
                at.get("writtenAt"), edges, jsonOrigins, document.get("sources"));
    }

    /** Every {@code at} the document holds, wherever it sits. */
    private static void collectAt(JsonNode node, List<JsonNode> into) {
        if (node.isObject()) {
            JsonNode at = node.get("at");
            if (at != null && at.isObject()) {
                into.add(at);
            }
            node.propertyStream().forEach(entry -> collectAt(entry.getValue(), into));
            return;
        }
        if (node.isArray()) {
            node.forEach(each -> collectAt(each, into));
        }
    }
}
