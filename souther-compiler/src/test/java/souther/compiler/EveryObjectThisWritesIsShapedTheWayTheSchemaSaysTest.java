package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every object this writes has the keys the shipped schema declares for it, and all of them.
 *
 * <p>The schema and the writer are two accounts of one document, and nothing made them agree except
 * somebody remembering to change both. What held them together was a check naming the objects it
 * looked at — {@code findings} and {@code partition.unanswered}, the two a single earlier defect
 * happened to touch. An object nobody had named was a place the two could disagree in silence, and
 * they did: issue #953 added {@code status} and {@code weakening} to ten objects and made four
 * fields conditional, and every document this compiler wrote was refused by the schema shipped
 * beside it for a year's worth of readers before anybody looked.
 *
 * <p><b>So this names nothing.</b> It walks the document and the schema together, from the root, and
 * reaches whatever the two of them reach. A key moved to another object is caught wherever it lands,
 * and an object added later is checked without being told about.
 *
 * <p><b>It is not a validator, and it refuses to look like one.</b> The walk is
 * {@link DocumentShape}, which fails on a keyword it does not understand rather than stepping over
 * it — a walk that skips what it does not know is a check that stops saying anything the moment the
 * schema grows, which is how the check it replaces came to pass over a document with twenty-seven
 * violations in it.
 */
class EveryObjectThisWritesIsShapedTheWayTheSchemaSaysTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A model whose report reaches a measure of every kind, made in full, in part, and not at all. */
    private static final String MODEL = """
            module m

            data R = { a: Int }
            data Found
            data Missing

            behavior f : (r: R) -> Found | Missing
                ensures Found -> r.a <= Int.min(20, 30)
                ensures Found -> r.a >= Int.min(40, 50)
            let f (r) = if r.a >= 30 * 2 then Found else Missing

            behavior unwritten : (r: R) -> Found | Missing

            behavior named : (r: R) -> Found | Missing
            let named (r) = if r.a == 7 then Found else Missing

            // Rules about the strings at a position, which a document names the way it names a
            // comparison. Both places a behavior states one, because the identity a document
            // carries says which of the two wrote it — and each tells nothing apart, which is what
            // gets the rule named in the report rather than only measured.
            behavior sorting : (code: String) -> Found | Missing
                ensures Found -> String.startsWith("", code)
            let sorting (code) = if String.startsWith("", code) then Found else Missing

            example f
                | "one" : (R { a = 1 }) -> Missing

            example named
                | "seven" : (R { a = 7 }) -> Found
            """;

    @Test
    void aDocumentThisWritesIsShapedTheWayTheSchemaSays() {
        DocumentShape.Read read = DocumentShape.of(document());
        assertTrue(read.objects() > 20,
                () -> "the model reaches the objects of the document: " + read.objects());
        // And the conditions the schema states were put to it, which is what tells a document this
        // walk agrees with from one that holds none of the shapes they are about.
        assertTrue(read.conditions() > 0,
                () -> "the model reaches what the schema states conditions about: "
                        + read.conditions());
        assertEquals(List.of(), read.wrong(), "what the schema shipped beside this refuses");
    }

    /**
     * The same of any document, for a test that can make one this cannot.
     *
     * <p>What a measure comes to where it runs out of what it may spend is written in shapes this
     * model never reaches: every default is set with room over anything in this repository, so
     * saying what the budget is is the only way there — and that knob is deliberately reachable
     * only from where the measures live. So the walk is offered rather than the document asked for,
     * and the check stays one check over one schema.
     */
    public static void assertShapedLikeTheSchema(JsonNode document) {
        assertEquals(List.of(), DocumentShape.of(document).wrong(),
                "what the schema shipped beside this refuses");
    }

    /**
     * A point owing no row is closed, rather than listing what it may not have.
     *
     * <p>The list was the keys of the owed side written a second time, with nothing holding the two
     * in step, and it fell behind twice: `weakening` was never added to it, and `writableBecause`
     * was added to the schema and not to it, so the schema took a document naming grounds for a
     * point nobody is owed a row at. Closed, there is no list to fall behind — a key added to the
     * item is forbidden here by not having been named.
     *
     * <p>Which is what this holds: not that some set of keys is forbidden, but that the branch is
     * still the kind of thing that forbids by default. Written the other way round, the check would
     * be the list a second time.
     */
    @Test
    void aPointOwingNoRowIsClosedAndNotAListOfWhatItMayNotHave() {
        JsonNode item = schema().get("$defs").get("partition").get("properties").get("boundaries")
                .get("items").get("properties").get("items").get("items");
        JsonNode notOwed = null;
        for (JsonNode branch : item.get("oneOf")) {
            if (branch.has("required") && branch.get("required").get(0).asString().equals("notOwed")) {
                notOwed = branch;
            }
        }
        assertNotNull(notOwed, "the item has a branch for a point owing no row");
        assertTrue(notOwed.has("additionalProperties")
                        && !notOwed.get("additionalProperties").asBoolean(),
                "the branch forbids what it does not name, rather than naming what it forbids");
        Set<String> named = new java.util.LinkedHashSet<>();
        notOwed.get("properties").propertyNames().forEach(named::add);
        assertEquals(Set.of("point", "location", "notOwed"), named,
                "and what it names is which point of the border it is, where on the quantity it is,"
                        + " and why no row is owed there");
    }

    /**
     * A document written before a key was added is still a document of this version.
     *
     * <p>Held on the key added last, which is {@code writableBecause}. The preamble says a key added
     * since does not raise the version, and what that promises is this: the same schema takes a
     * document with the key and a document without it. So an absent array is a producer that predates
     * the field and never a point with no grounds — that is written as an empty array, and a reader
     * that took the two for one answer would read every older document as a corpus nothing shows
     * anything about.
     */
    @Test
    void aDocumentWrittenBeforeAKeyWasAddedIsStillOfThisVersion() {
        JsonNode without = document();
        int taken = strip(without, "writableBecause");
        assertTrue(taken > 0, "the document carries the key, or this strips nothing");

        assertEquals(List.of(), DocumentShape.of(without).wrong(),
                "the shipped schema refuses a document written before the key existed");
    }

    /** Every occurrence of {@code key} taken out, and how many there were. */
    private static int strip(JsonNode node, String key) {
        int taken = 0;
        if (node instanceof tools.jackson.databind.node.ObjectNode object) {
            if (object.has(key)) {
                object.remove(key);
                taken++;
            }
            List<String> names = new ArrayList<>();
            object.propertyNames().forEach(names::add);
            for (String name : names) {
                taken += strip(object.get(name), key);
            }
        } else if (node.isArray()) {
            for (JsonNode each : node) {
                taken += strip(each, key);
            }
        }
        return taken;
    }

    /**
     * And so is every document checked in as an answer.
     *
     * <p>Those are what a reader of this project meets first, and they are rewritten by a flag
     * rather than by hand — so a change that moved the shape moved them with it, and nothing on the
     * way said the shape had stopped matching what ships beside them.
     */
    @Test
    void soIsEveryAnswerCheckedIn() throws IOException {
        Path corpus = Path.of("src/test/resources/souther/compiler/conformance");
        assertTrue(Files.isDirectory(corpus), () -> "the corpus is at " + corpus.toAbsolutePath());
        List<Path> answers;
        try (Stream<Path> files = Files.walk(corpus)) {
            answers = files.filter(each -> each.getFileName().toString().endsWith(".report.json"))
                    .sorted().toList();
        }
        assertTrue(!answers.isEmpty(), "there is an answer checked in");

        List<String> wrong = new ArrayList<>();
        for (Path each : answers) {
            wrong.addAll(DocumentShape.of(JSON.readTree(read(each))).wrong());
        }
        assertEquals(List.of(), wrong, "an answer the schema shipped beside it refuses");
    }

    private static JsonNode schema() {
        return DocumentShape.schema();
    }

    private static String read(Path at) {
        try {
            return Files.readString(at, StandardCharsets.UTF_8);
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(cannotRead);
        }
    }

    private static JsonNode document() {
        return reportOf(Compilation.ofSource(MODEL, "Main"));
    }

    private static JsonNode reportOf(Compilation compilation) {
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
    }
}
