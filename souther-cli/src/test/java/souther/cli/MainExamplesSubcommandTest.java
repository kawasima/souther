package souther.cli;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.report.AdequacyReport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code souther examples}: what a migration reads while it is working.
 *
 * <p>The JSON is checked against the schema shipped beside it, because a build reads this and a build
 * cannot notice a key that quietly changed name.
 */
class MainExamplesSubcommandTest {

    private static final String MODEL = """
            module example.trip
            import String ( length )

            data MemberId = String
                invariant length(value) > 0

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Rejected = { reason: String }
            data Found = { id: MemberId }
            data Missing = { reason: String }

            behavior findMember : (id: MemberId) -> Found | Missing

            behavior submit : (request: Draft) -> Submitted | Rejected
                constructs Submitted, Rejected

            let submit (request) = {
                guard request.cost.value <= 100 else Rejected { reason = "over" }
                Submitted { cost = request.cost }
            }

            example findMember
                | "known"   : (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                | "unknown" : (MemberId("m-9")) -> Missing { reason = "none" }

            example submit
                | "within" : (Draft { cost = Amount(50) }) -> Submitted
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String run(String... extraArgs) throws Exception {
        return both(extraArgs).out();
    }

    /** What the command wrote, kept apart: a build reads one of these and a person reads the other. */
    private record Streams(String out, String err) {}

    private static Streams both(String... extraArgs) throws Exception {
        Path file = Files.createTempDirectory("souther-examples").resolve("trip.sou");
        Files.writeString(file, MODEL);
        List<String> args = new ArrayList<>(List.of("examples", file.toString()));
        args.addAll(List.of(extraArgs));

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            Main.main(args.toArray(String[]::new));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Streams(out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void theReportSaysWhichBehaviorsAreStillWaiting() throws Exception {
        String out = run();

        assertTrue(out.contains("findMember"), out);
        assertTrue(out.contains("injected"), out);
        assertTrue(out.contains("implemented"), out);
        assertTrue(out.contains("2 behaviors: 1 implemented, 0 unimplemented, 1 injected; 2 rows waiting for a `let`."),
                out);
    }

    @Test
    void behaviorFiltersDownToOne() throws Exception {
        String out = run("--behavior", "submit");

        assertTrue(out.contains("submit"), out);
        assertFalse(out.contains("findMember"), out);
        assertTrue(out.contains("1 behavior: 1 implemented, 0 unimplemented, 0 injected; 0 rows waiting"), out);
    }

    /**
     * The rows are something the flag asks for, and they arrive after the report rather than inside it.
     *
     * <p>Stated as what the output is made of rather than as text to match: without the flag the
     * command prints the report and stops, and with it the same report is followed by something more.
     * A build that parses the report has to keep working when somebody adds the flag.
     */
    @Test
    void theRowsAreAskedForAndComeAfterTheReport() throws Exception {
        String report = run("--behavior", "submit");
        String withRows = run("--generate", "--behavior", "submit");

        assertTrue(withRows.startsWith(report), withRows);
        assertFalse(withRows.substring(report.length()).isBlank(), "the flag added rows");
    }

    /** Rows are source, and source in the middle of a JSON document is not a document. Asked for
     * together, the document stays a document and the rows go beside it. */
    @Test
    void rowsGoBesideTheJsonRatherThanIntoIt() throws Exception {
        Streams streams = both("--generate", "--behavior", "submit", "--format", "json");

        assertNotNull(JSON.readTree(streams.out()), "the document is still a document");
        assertFalse(streams.err().isBlank(), "the rows are still written");
    }

    /**
     * An edge nothing was written at is offered by the request a class nothing covers is: one block
     * holds everything the behavior is owed.
     *
     * <p>Read off the rows rather than off the report above them. The report names the points
     * either way — what this is about is the block an author pastes, which held the classes and
     * left the points to a flag.
     */
    @Test
    void theBlockHoldsTheEdgesBesideTheClasses() throws Exception {
        String report = run("--behavior", "submit");
        String rows = run("--generate", "--behavior", "submit").substring(report.length());

        assertTrue(rows.contains("Amount(100)"),
                "the row at the line the guard drew is one of these:\n" + rows);
    }

    @Test
    void theJsonCarriesTheNumbersABuildReads() throws Exception {
        JsonNode root = JSON.readTree(run("--format", "json"));

        assertEquals(AdequacyReport.SCHEMA_VERSION, root.get("schemaVersion").asInt());
        assertEquals("complete", root.get("status").asString());
        assertNotNull(root.get("compilerVersion"));

        JsonNode module = root.get("modules").get(0);
        assertEquals("example.trip", module.get("module").asString());
        JsonNode findMember = module.get("behaviors").get(0);
        assertEquals("findMember", findMember.get("name").asString());
        assertEquals("injected", findMember.get("implementation").asString());
        assertEquals(2, findMember.get("rows").asInt());
        assertEquals(2, findMember.get("pending").asInt());

        JsonNode submit = module.get("behaviors").get(1);
        assertEquals("implemented", submit.get("implementation").asString());
        assertEquals(0, submit.get("pending").asInt());
    }

    /**
     * The shape: every key the schema requires is present, and no key is emitted that the schema does
     * not declare.
     *
     * <p>Named for what it does. It is not a validation — that would want a library — and the words a
     * field is allowed to take are not checked here at all; a name promising more than that is how a
     * schema came to allow a word the compiler had stopped writing while this went on passing.
     * {@link EverySchemaWordIsAccountedForTest} holds the vocabularies.
     */
    @Test
    void theEmittedJsonHasTheShippedSchemaShape() throws Exception {
        JsonNode schema;
        try (var in = Main.class.getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, AdequacyReport.SCHEMA_RESOURCE + " ships beside the compiler");
            schema = JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        JsonNode root = JSON.readTree(run("--format", "json"));

        assertEquals(schema.get("properties").get("schemaVersion").get("const").asInt(),
                root.get("schemaVersion").asInt(), "a shape change raises the version");
        agrees(root, schema.get("properties"), schema.get("required"));

        JsonNode moduleDef = schema.get("$defs").get("module");
        JsonNode module = root.get("modules").get(0);
        agrees(module, moduleDef.get("properties"), moduleDef.get("required"));

        JsonNode behaviorDef = schema.get("$defs").get("behavior");
        agrees(module.get("behaviors").get(0), behaviorDef.get("properties"),
                behaviorDef.get("required"));

        // Down to where the measures are. What each of them says about itself is the part that has
        // grown, and a check that stopped at the behavior would not have seen any of it arrive.
        int borders = 0;
        for (JsonNode behavior : module.get("behaviors")) {
            for (String measure : List.of("signature", "partition", "branch")) {
                if (behavior.has(measure)) {
                    JsonNode def = schema.get("$defs").get(measure);
                    agrees(behavior.get(measure), def.get("properties"), def.get("required"));
                }
            }
            if (!behavior.has("partition")) {
                continue;
            }
            JsonNode partitionDef = schema.get("$defs").get("partition").get("properties");
            JsonNode partition = behavior.get("partition");
            for (String each : List.of("axes", "boundaries")) {
                JsonNode itemDef = partitionDef.get(each).get("items");
                for (JsonNode item : partition.get(each)) {
                    agrees(item, itemDef.get("properties"), itemDef.get("required"));
                }
            }
            // And into a border's own points, which is where the shape this version was raised for
            // lives. Stopped at the border, the check saw the array arrive and nothing about what
            // was in it — and a schema is only what a document is held to where something holds one
            // to it.
            JsonNode pointDef = partitionDef.get("boundaries").get("items")
                    .get("properties").get("items").get("items");
            for (JsonNode border : partition.get("boundaries")) {
                everyPointOfOneBorder(border.get("items"), pointDef);
                borders++;
            }
            JsonNode pairsDef = partitionDef.get("pairs");
            agrees(partition.get("pairs"), pairsDef.get("properties"), pairsDef.get("required"));
        }
        // The walk above says nothing where it walked nothing, and a border is the one thing here
        // whose own shape this version changed.
        assertTrue(borders > 0, "the model under test draws a line somewhere");
    }

    /**
     * A border writes each of its four points once, and each as owed or as not owed and never both.
     *
     * <p>The invariant the Java side holds at its constructors, held against what is actually
     * emitted. Two of these four used to be entries of `boundaries` and the other two were written
     * nowhere, so a consumer working to a coverage criterion had no way to tell a border short of an
     * item from one that owes fewer — which is the whole of what raising the version bought, and it
     * is worth nothing if a document can be short of one and still read as this version's.
     */
    private static void everyPointOfOneBorder(JsonNode points, JsonNode pointDef) {
        assertNotNull(points, "a border writes its points");
        List<String> roles = new java.util.ArrayList<>();
        points.forEach(point -> roles.add(point.get("point").asString()));
        assertEquals(List.of("on", "off", "in", "out"), roles,
                "each of the four roles once: " + points);
        for (JsonNode point : points) {
            agrees(point, pointDef.get("properties"), pointDef.get("required"));
            // Owed or not, and the document says which by which keys it carries. A point the rules
            // refuse that also said a row is at it is the state the Java side cannot build, and a
            // document that carried both would be the one place it could be written down.
            boolean owed = !point.has("notOwed");
            for (String measured : List.of("relation", "against", "hit", "knownWritable", "status")) {
                assertEquals(owed, point.has(measured),
                        measured + " is written exactly where a row is owed: " + point);
            }
        }
    }

    /**
     * A measure with no number says why, in the emitted document and not only in the evidence.
     *
     * <p>Both directions. A reader told the arms are unavailable and left to work out whether that is
     * a behavior with none or a run that failed is the reader this field exists for, and a reason
     * printed beside a number would say a value is missing and give it in the same breath.
     */
    @Test
    void everyUnavailableMeasureInTheJsonSaysWhy() throws Exception {
        JsonNode root = JSON.readTree(run("--format", "json"));
        int seen = 0;
        for (JsonNode node : root.findParents("status")) {
            if (!node.has("reason") && !"unavailable".equals(node.get("status").asString())) {
                continue;
            }
            seen++;
            assertEquals("unavailable", node.get("status").asString(),
                    "a reason is given beside a number: " + node);
            assertTrue(node.has("reason"), "no number and no reason: " + node);
        }
        assertTrue(seen > 0, "the model under test has a measure with no number: " + root);
    }

    /**
     * A key added to this version is optional, so a document written before it existed is still one.
     *
     * <p>Which is one direction and not both. This schema validates a document written before the key
     * existed, because nothing demands it — that is what is checked here. It does not follow that a
     * reader holding the older copy of this schema accepts a document written now: every object here
     * is {@code additionalProperties: false}, so an older validator refuses a key added since. The
     * version says what was taken away, not what was added, and a reader that must accept newer
     * documents needs the newer schema rather than a higher number.
     */
    @Test
    void aKeyAddedSinceIsNotDemandedOfADocumentWrittenBeforeIt() throws Exception {
        JsonNode schema;
        try (java.io.InputStream in =
                     Main.class.getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in);
            schema = JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        JsonNode axis = schema.get("$defs").get("partition")
                .get("properties").get("axes").get("items");
        JsonNode input = schema.get("$defs").get("signature")
                .get("properties").get("inputs").get("items");

        for (JsonNode where : List.of(axis, input)) {
            assertTrue(where.get("properties").has("excluded"), "the key is declared");
            assertFalse(required(where).contains("excluded"),
                    "and not demanded, or a document written before it would stop being valid");
        }

        JsonNode emitted = JSON.readTree(run("--format", "json"))
                .get("modules").get(0).get("behaviors").get(1);
        assertTrue(emitted.get("signature").get("inputs").get(0).has("excluded"),
                "what is written now carries it");
    }

    private static List<String> required(JsonNode of) {
        List<String> names = new ArrayList<>();
        of.get("required").forEach(each -> names.add(each.asString()));
        return names;
    }

    private static void agrees(JsonNode emitted, JsonNode properties, JsonNode required) {
        for (JsonNode key : required) {
            assertTrue(emitted.has(key.asString()),
                    "the schema requires `" + key.asString() + "` and it is not emitted");
        }
        for (String name : emitted.propertyNames()) {
            assertTrue(properties.has(name),
                    "`" + name + "` is emitted and the schema does not declare it");
        }
    }
}
