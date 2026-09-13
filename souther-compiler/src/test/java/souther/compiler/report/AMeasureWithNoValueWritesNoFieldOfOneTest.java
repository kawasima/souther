package souther.compiler.report;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.DocumentShape;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the JSON writes where a measure has no value, and what it goes on writing beside it.
 *
 * <p>The specification says a measure with no number writes none — not a nought, not an empty set —
 * and two writers went on writing both: {@code branch} put {@code arms: 0} beside a status saying no
 * measurement was made, and a border's point put {@code hit: false} at a place nothing had looked at
 * (issue #997). A consumer dividing {@code covered} by {@code arms} got a measurement out of a
 * measure that does not apply.
 *
 * <p><b>Both directions, because the change is what absence means.</b> Making a key disappear is
 * only worth anything if the key is still there when the measurement is: absence would otherwise be
 * the answer to every question, and a reader could not tell a document that stopped writing
 * {@code hit} from one whose measure came back with nothing. So each case here is asserted against
 * its opposite in the same document.
 *
 * <p><b>And what stays.</b> {@code knownWritable} is not this measurement's answer — a row at the
 * point, a value built through the module's decoders and the rules proving the point inhabited each
 * settle it, and only the first is coverage — so it is written whether or not anybody measured. A
 * point nobody measured whose rules prove it inhabited carries {@code true} beside a status of
 * {@code unavailable}, and the two say different things about different questions.
 */
class AMeasureWithNoValueWritesNoFieldOfOneTest {

    /**
     * A behavior whose model draws two lines and whose body is never made.
     *
     * <p>The bound of {@code Amount} is a line a row meets by writing the value, so it is measured
     * without the arms. The {@code guard} draws one at 100 that a row meets by reaching the
     * comparison, so at a level that does not run instrumented rows it is not. The two live in one
     * document, which is what lets the same run show a key present and absent for the same reason.
     */
    private static final String MODEL = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Draft = { cost: Amount }
            data Submitted = { cost: Amount }
            data Waiting = { cost: Amount }

            behavior submit : (request: Draft) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (request) = {
                guard request.cost.value <= 100 else Waiting { cost = request.cost }
                Submitted { cost = request.cost }
            }

            example submit
                | (Draft { cost = Amount(50) }) -> Submitted
            """;

    /** The same model with a behavior the outside world implements, which has no body for the arm
     *  measure to be about. */
    private static final String INJECTED = MODEL + """

            data Receipt = { cost: Amount }

            behavior record : (of: Submitted) -> Receipt
            """;

    /**
     * A report holding an arm a row goes through, an arm nothing goes through, and an arm nobody
     * could decide.
     *
     * <p>The third takes a row that does not come back, so this model is measured with one said to
     * overrun. The first two are the same behavior's, which is what makes the three fit in one
     * document: a reading that stopped leaves the arm it lit alight.
     */
    private static JsonNode armAccounts() {
        Compilation compilation = Compilation.ofSource("""
                module example.arms

                data Draft = { n: Int }
                data Done = { n: Int }
                data Small = { n: Int }

                partial let spin (n: Int): Int = spin(n)

                behavior go : (request: Draft) -> Done | Small
                    constructs Done, Small

                let go (request) = {
                    guard request.n <= 0 else Done { n = spin(request.n) }
                    Small { n = request.n }
                }

                behavior gate : (request: Draft) -> Done | Small
                    constructs Done, Small

                let gate (request) = {
                    guard request.n <= 0 else Done { n = 1 }
                    Small { n = request.n }
                }

                example go
                    | (Draft { n = 1 }) -> Done { n = 1 }

                example gate
                    | (Draft { n = 0 }) -> Small { n = 0 }
                """, "Main");
        compilation.withJvmExampleDeadlines(souther.compiler.DoesNotComeBack.overrunningOn(
                souther.compiler.DoesNotComeBack.everythingAboutRowsOf("go")));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JsonMapper.builder().build().readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
    }

    private static JsonNode reportOf(String source, Adequacy.Level level) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.reportOnly(level));
        compilation.answerEverything();
        JsonNode root = JsonMapper.builder().build().readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())));
        assertNotNull(root, "the model under test compiles");
        return root;
    }

    private static JsonNode behavior(JsonNode report, String name) {
        for (JsonNode module : report.get("modules")) {
            for (JsonNode each : module.get("behaviors")) {
                if (name.equals(each.get("name").asString())) {
                    return each;
                }
            }
        }
        throw new AssertionError("no behavior " + name + " in " + report);
    }

    /** The point of one of the borders, whichever role, that has the status asked for. */
    private static JsonNode pointWithStatus(JsonNode behavior, String status) {
        for (JsonNode border : behavior.get("partition").get("boundaries")) {
            for (JsonNode point : border.get("items")) {
                if (point.has("status") && status.equals(point.get("status").asString())) {
                    return point;
                }
            }
        }
        throw new AssertionError("no point at " + status + " in " + behavior.get("partition"));
    }

    /**
     * A point nothing looked at says so, and says nothing about whether a row is there.
     *
     * <p>What is left is the two halves of what the point asks for — the relation and what it is
     * against, which the model states — and the status saying why nobody answered it.
     */
    @Test
    void aPointNobodyMeasuredWritesNoHit() {
        JsonNode point = pointWithStatus(behavior(reportOf(MODEL, Adequacy.Level.WITNESS), "submit"),
                "unavailable");

        assertNull(point.get("hit"),
                () -> "no measurement, so no answer about a row being here: " + point);
        assertEquals("arms_not_asked", point.get("reason").asString());
        assertTrue(point.has("relation") && point.has("against"),
                () -> "what the model says about the point stands: " + point);
    }

    /**
     * And a point that was measured and missed writes {@code false}.
     *
     * <p>The half of this change that keeps the other half worth anything. If a measured miss also
     * went unwritten, the key would say nothing at all — present for a hit and absent for everything
     * else — and every consumer would be back to inferring a miss from a silence.
     */
    @Test
    void aPointMeasuredAndMissedWritesHitFalse() {
        JsonNode behavior = behavior(reportOf(MODEL, Adequacy.Level.WITNESS), "submit");
        boolean missed = false;
        boolean met = false;
        for (JsonNode border : behavior.get("partition").get("boundaries")) {
            for (JsonNode point : border.get("items")) {
                if (point.has("hit")) {
                    assertEquals("complete", point.get("status").asString(),
                            () -> "a hit is written where the measurement has a value: " + point);
                    missed |= !point.get("hit").asBoolean();
                    met |= point.get("hit").asBoolean();
                }
            }
        }
        assertTrue(missed, () -> "the one row writes 50, so the bound at 0 is missed: " + behavior);
        assertTrue(met, () -> "and the class it is in is reached: " + behavior);
    }

    /**
     * A point nobody measured whose rules prove it inhabited is still known to be writable.
     *
     * <p>The two questions kept apart, in the one document where mixing them would show. Coverage
     * asks whether a row was seen at the point and had no answer; writability asks whether one can
     * be written there and has three ways to say yes, only one of which is a row. Read as one, this
     * point would either lose the answer it has or claim the one it does not.
     */
    @Test
    void aPointNobodyMeasuredStillSaysWhetherARowCanBeWritten() {
        JsonNode point = pointWithStatus(behavior(reportOf(MODEL, Adequacy.Level.WITNESS), "submit"),
                "unavailable");

        assertTrue(point.has("knownWritable"),
                () -> "settled by the rules, which no measurement was needed for: " + point);
        assertTrue(point.get("knownWritable").asBoolean(),
                () -> "and the projection reaches this point: " + point);
    }

    /**
     * A behavior with no body writes no arms, and nothing that is read beside them.
     *
     * <p>{@code denominatorSettled} goes with them although it is the account's answer about the
     * set rather than about any arm: what it qualifies is the arms, and a word beside no arms is an
     * answer standing in for a measurement nobody made.
     */
    @Test
    void aBehaviorWithNoBodyWritesNoArmCount() {
        JsonNode branch = behavior(reportOf(INJECTED, Adequacy.Level.ALL), "record").get("branch");

        assertEquals("unavailable", branch.get("status").asString());
        assertEquals("no_body", branch.get("reason").asString());
        for (String key : new String[] {"obligations", "denominatorSettled"}) {
            assertNull(branch.get(key), () -> key + " is a measurement nobody made: " + branch);
        }
    }

    /** And a behavior whose arms were counted writes each of them, with what it came to. */
    @Test
    void aBehaviorWhoseArmsWereCountedWritesThem() {
        JsonNode branch = behavior(reportOf(INJECTED, Adequacy.Level.ALL), "submit").get("branch");

        assertEquals("complete", branch.get("status").asString());
        assertEquals(2, branch.get("obligations").size());
        assertTrue(branch.get("denominatorSettled").asBoolean(),
                () -> "nothing has shown the arms to be short of one: " + branch);
        List<String> dispositions = new java.util.ArrayList<>();
        branch.get("obligations")
                .forEach(arm -> dispositions.add(arm.get("disposition").asString()));
        assertEquals(List.of("met", "unmet"), dispositions,
                () -> "the one row takes the guard's continued arm and not its else: " + branch);
    }

    /**
     * What an arm of the document is made of, and nothing else.
     *
     * <p>Four things and no fifth: what tells the arm from every other, what a reader is shown of
     * it, where it stands, and what left it open. A key that is none of those is one of them said a
     * second time — which is what the two this change removed were. {@code status} and {@code hit}
     * determined the disposition and sat beside it with nothing holding them in step, so a
     * schema-valid document could say an arm was met by a row that was never seen, and a consumer
     * could reimplement the account rather than read it.
     *
     * <p>The vocabulary and not a condition on it. Where each key may appear is the schema's own
     * business and is held beside this; that a key exists at all is the decision, and one added on
     * both sides of the writer and the schema together is exactly the return this is here to stop.
     * So the set is written out: there is nothing to derive it from, because it is the thing being
     * decided.
     */
    @Test
    void anArmOfTheDocumentIsIdentityDisplayStateAndProvenance() {
        Set<String> identity = Set.of("obligationId");
        Set<String> shown = Set.of("label", "kind", "construct", "at");
        Set<String> state = Set.of("disposition");
        Set<String> provenance = Set.of("weakening", "notCountedBecause");

        Set<String> vocabulary = new java.util.LinkedHashSet<>();
        vocabulary.addAll(identity);
        vocabulary.addAll(shown);
        vocabulary.addAll(state);
        vocabulary.addAll(provenance);

        JsonNode arms = schema().get("$defs").get("branch").get("properties").get("obligations")
                .get("items");
        Set<String> written = new java.util.LinkedHashSet<>();
        arms.get("properties").propertyNames().forEach(written::add);

        assertEquals(vocabulary, written,
                "an arm says what it is, what it is shown as, where it stands, and what left it"
                        + " open");
    }

    /**
     * And what the shipped schema says about all this is true of what the writer writes.
     *
     * <p>Its own test because nothing else checks it. The walk that holds the schema and the writer
     * together reads a composition's branches for the keys they allow and never for what they
     * require, and says so — so an {@code if}/{@code then} added to the schema is a sentence nothing
     * enforces, and a wrong one would sit there being read by consumers. The two conditions this
     * change added are read out of the file and applied here, over a document holding both sides of
     * each of them.
     *
     * <p>Read from the schema rather than restated. A condition written here as well would be the
     * same decision in two files, which is what the shipped schema and this compiler's writers were
     * before anybody walked them together.
     */
    @Test
    void theConditionsTheSchemaStatesAreTrueOfWhatIsWritten() {
        JsonNode report = reportOf(INJECTED, Adequacy.Level.WITNESS);

        // Every condition of every object, put to the document by the walk that holds the two
        // together. Read out of the schema there rather than here: applied by whoever happened to
        // remember, a condition is enforced over the objects somebody wrote a loop for and is prose
        // everywhere else, which is what these were.
        DocumentShape.Read walked = DocumentShape.of(report);
        assertTrue(walked.conditions() > 0,
                () -> "the report reaches what the schema states conditions about: "
                        + walked.conditions());
        assertEquals(List.of(), walked.wrong(), "what the schema shipped beside this refuses");

        int checked = 0;
        for (JsonNode module : report.get("modules")) {
            for (JsonNode each : module.get("behaviors")) {
                if (each.has("branch")) {
                    checked++;
                }
            }
        }
        assertTrue(checked >= 2, "both a measured branch and an unmeasured one are in this report");

        // And that the arms the conditions are about are all reached. The conditions here are what
        // keeps the document's normal form the account's: an arm says where it stands once, and
        // beside it whatever left it open — so `undecided` carries what the reading went without
        // and the two settled states carry nothing. Applied over a report holding all three.
        JsonNode arms = armAccounts();
        assertEquals(List.of(), DocumentShape.of(arms).wrong(),
                "what the schema refuses of a report holding every state an arm reaches");
        Set<String> reached = new java.util.LinkedHashSet<>();
        for (JsonNode module : arms.get("modules")) {
            for (JsonNode each : module.get("behaviors")) {
                if (!each.get("branch").has("obligations")) {
                    continue;
                }
                for (JsonNode arm : each.get("branch").get("obligations")) {
                    reached.add(arm.get("disposition").asString());
                }
            }
        }
        // An arm out of the count is not among them: a fork whose rule nothing worked out has no
        // rows to read at all, so no model reaches one. What the document says of such an arm is
        // held where the account is made.
        assertEquals(Set.of("met", "unmet", "undecided"), reached,
                "every state a model reaches is in the document this was applied to");

        int measured = 0;
        int not = 0;
        for (JsonNode module : report.get("modules")) {
            for (JsonNode each : module.get("behaviors")) {
                if (!each.has("partition")) {
                    continue;
                }
                for (JsonNode border : each.get("partition").get("boundaries")) {
                    for (JsonNode point : border.get("items")) {
                        if (point.has("status")) {
                            boolean withAValue = List.of("complete", "partial")
                                    .contains(point.get("status").asString());
                            measured += withAValue ? 1 : 0;
                            not += withAValue ? 0 : 1;
                        }
                    }
                }
            }
        }
        // Both sides of the condition, in the one document. The corpus checked in beside this has no
        // point at a status with no value, so a check run over that alone would be a check on the
        // half of the condition that was already true (issue #997).
        int withAValue = measured;
        int without = not;
        assertTrue(withAValue > 0 && without > 0,
                () -> "the level was chosen so that both arms occur: " + withAValue + " measured, "
                        + without + " not");
    }

    private static JsonNode schema() {
        try (java.io.InputStream in = AdequacyReport.class
                .getResourceAsStream(AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, AdequacyReport.SCHEMA_RESOURCE + " ships beside the compiler");
            return JsonMapper.builder().build().readTree(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
