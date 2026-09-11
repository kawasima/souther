package souther.compiler.query;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;
import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A behavior whose boundary could not be worked out is measured, and what it comes to is that
 * nobody could measure it.
 *
 * <p>Three absences meet on one behavior and they are three different things. The signature is an
 * analysis saying what it could not work out, and it is right to have no entry. The measures of
 * that behavior are asked for and started and cannot finish, so each of them is a measurement with
 * no number that says what it went without. And the reading of what the model divides the behavior
 * into is never asked for, because the measure that would ask has already answered.
 *
 * <p>Read as one they are not distinguishable: a behavior left out of a measure's map reads to one
 * reader as this compiler disagreeing with itself and to another as a measure nobody asked for, and
 * a measure nobody asked for goes without nothing — so a behavior nothing could be established
 * about is held to no bar at all and says nothing about it.
 */
class ABehaviorWithNoBoundaryIsMeasuredAsOneNobodyCouldMeasureTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * A parameter naming a type from a module that did not resolve, and a composition over the
     * behavior that takes it.
     *
     * <p>The composition is the point of the second half. A {@code >->} has no positions of its own
     * — its partition measure answers that whatever else is wrong — so it is the one behavior whose
     * account of the boundary can come from the signature measure and from nothing else.
     */
    private static final String UNRESOLVED = """
            module probe.unresolved
            import shared.money ( Amount )

            data Invoice = { amount: Amount }
            data Receipt = { n: Int }

            behavior issue : (a: Amount) -> Invoice
                constructs Invoice
            let issue (a) = Invoice { amount = a }

            behavior receipt : (i: Invoice) -> Receipt
                constructs Receipt
            let receipt (i) = Receipt { n = 1 }

            behavior whole = issue >-> receipt
            """;

    private static Compilation measured() {
        Compilation compilation = Compilation.ofSource(UNRESOLVED, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    private static JsonNode behaviorOf(AdequacyReport report, String name) {
        JsonNode root = JSON.readTree(
                report.json(souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        for (JsonNode each : root.get("modules").get(0).get("behaviors")) {
            if (name.equals(each.get("name").asString())) {
                return each;
            }
        }
        throw new AssertionError("no behavior `" + name + "` in the document");
    }

    private static List<String> weakeningOf(JsonNode behavior) {
        JsonNode said = behavior.get("weakening");
        return said == null ? List.of()
                : said.valueStream().map(JsonNode::asString).toList();
    }

    /** The analysis has no signature to publish, and that is not a measurement. */
    @Test
    void theSignatureIsAbsentFromTheAnalysis() {
        Map<String, souther.compiler.check.Sig> sigs =
                measured().db().ask(new Bodies.Signatures("probe.unresolved")).value();
        assertNotNull(sigs, "the signatures answered");
        assertFalse(sigs.containsKey("issue"), "no boundary was built for `issue`");
        assertFalse(sigs.containsKey("whole"), "nor for the composition over it");
        assertTrue(sigs.containsKey("receipt"), "and the behavior beside them keeps its own");
    }

    /**
     * And what the model divides it into is never asked for.
     *
     * <p>The stronger half of the same fact. A reading nobody asked for cannot have answered
     * wrongly, and the measure that would have asked has an answer of its own — so the partiality
     * of the analysis stops at the measure and does not reach past it.
     */
    @Test
    void whatTheModelDividesItIntoIsNotAsked() {
        Compilation compilation = measured();
        assertNotNull(compilation.db().ask(new Adequacy.Coverage("probe.unresolved")).value(),
                "the coverage answered");
        assertEquals(null,
                compilation.db().ask(new Adequacy.Divided("probe.unresolved", "issue")).value(),
                "nothing derived what the model divides `issue` into");
    }

    /** Each measure of it says it could not be made, and says the same thing. */
    @Test
    void everyMeasureThatReadsABoundarySaysItHadNone() {
        Compilation compilation = measured();
        Adequacy.SignatureEvidence signature = compilation.db()
                .ask(new Adequacy.Witnesses("probe.unresolved")).value().get("issue");
        PartitionEvidence partition = compilation.db()
                .ask(new Adequacy.Coverage("probe.unresolved")).value().get("issue");
        assertNotNull(signature, "the signature measure answered for `issue`");
        assertNotNull(partition, "and so did the partition measure");

        assertEquals(BoundaryForMeasurement.NotDerived.BEHAVIOR_BOUNDARY_NOT_DERIVED,
                signature.counted().why());
        assertEquals(BoundaryForMeasurement.NotDerived.BEHAVIOR_BOUNDARY_NOT_DERIVED,
                partition.partitioned().why());
        assertEquals(BoundaryForMeasurement.NotDerived.BEHAVIOR_BOUNDARY_NOT_DERIVED,
                compilation.db().ask(new Adequacy.BodyBorders("probe.unresolved")).value()
                        .get("issue").why());

        // The arms are measured off the bodies and not off the boundary, so they say what happened
        // to them. Two measures short of two different things is two sentences, and a behavior
        // whose every measure gave one reason would be this deciding what the others found.
        Adequacy.BranchEvidence branch = compilation.db()
                .ask(new Adequacy.BranchCoverage("probe.unresolved")).value().get("issue");
        assertEquals(Adequacy.BranchEvidence.Unelaborated.BODIES_NOT_ELABORATED,
                branch.measured().why());
    }

    /**
     * How many positions it has is unknown for a composition, and known for a declared behavior.
     *
     * <p>Which is why the positions are a measurement. Both come out with no case counted anywhere,
     * and only one of them can say how many places there were to count at: a {@code >->} takes what
     * its first stage takes, and that is the boundary that did not work out.
     *
     * <p><b>Two states and not three.</b> The positions of a declared behavior are read off the
     * declaration and nothing about them fell short — what could not be read is the cases at each
     * of them, which each position answers for itself. A measurement saying the list itself was
     * weakened would be a shortfall in a thing that has none, and the two states this measure
     * exists to tell apart would be three.
     */
    @Test
    void thePositionsAreKnownOnlyWhereTheDeclarationWritesThem() {
        Map<String, Adequacy.SignatureEvidence> witnesses =
                measured().db().ask(new Adequacy.Witnesses("probe.unresolved")).value();

        assertInstanceOf(Measurement.Complete.class, witnesses.get("issue").inputs(),
                "`issue` writes one parameter, whatever its type resolved to");
        assertEquals(1, witnesses.get("issue").positions().size());
        assertEquals(BoundaryForMeasurement.NotDerived.BEHAVIOR_BOUNDARY_NOT_DERIVED,
                witnesses.get("issue").positions().get(0).cases().why(),
                "and the cases at it are what could not be read");

        assertInstanceOf(Measurement.FailedToMeasure.class, witnesses.get("whole").inputs(),
                "a composition's positions are its first stage's, and that is what was not read");
    }

    /**
     * The behavior carries the fact once, and the composition carries it from the signature measure
     * alone.
     *
     * <p>Which makes the composition the row that holds this. Its partition measure answers that it
     * has no subject and goes without nothing, so a signature measure that went back to leaving the
     * behavior out would leave nothing anywhere saying this behavior could not be measured — while
     * the declared behavior beside it would go on carrying the fact through the partition measure
     * and look unchanged.
     */
    @Test
    void theFactIsCarriedOncePerBehavior() {
        Compilation compilation = measured();
        AdequacyReport report = AdequacyReport.of(compilation);
        for (AdequacyReport.ModuleReport module : report.modules()) {
            for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
                // One fact per behavior, and which of the two it is follows from which half of the
                // boundary was missing. `receipt` has a signature and an input nothing read, so it
                // carries the other word — and it carries one, not none: a behavior in a module
                // with a hole in it was not measured over an input it could read.
                Class<? extends Weakening> owed = "receipt".equals(behavior.name())
                        ? Weakening.InputNotRead.class : Weakening.BoundaryNotDerived.class;
                long said = behavior.evidence().weakening().causes().stream()
                        .filter(owed::isInstance)
                        .count();
                assertEquals(1, said,
                        () -> "what " + behavior.name() + " went without: "
                                + behavior.evidence().weakening());
            }
        }

        PartitionEvidence composed = compilation.db()
                .ask(new Adequacy.Coverage("probe.unresolved")).value().get("whole");
        assertEquals(PartitionEvidence.NONE, composed,
                "a composition is measured at its stages, boundary or no boundary");
        assertTrue(composed.weakening().isEmpty(),
                "so its partition measure is short of nothing, and cannot be where the fact came"
                        + " from");
    }

    /**
     * The document says what could not be read, in the words it promises, and leaves out the
     * sections it has nothing to fill in.
     *
     * <p>Two words and not one, because a reader acts on them differently. {@code issue} has a name
     * in its own declaration that resolved to nothing, and that is where the author looks;
     * {@code receipt}'s declaration is whole and the hole is elsewhere in the module, so an author
     * sent to {@code receipt} would find nothing wrong with it.
     */
    @Test
    void theDocumentLeavesOutWhatItCouldNotRead() {
        AdequacyReport report = AdequacyReport.of(measured());
        for (String name : List.of("issue", "whole")) {
            JsonNode behavior = behaviorOf(report, name);
            assertFalse(behavior.has("signature"),
                    () -> name + " has no signature to read, so the document writes none");
            assertEquals(1, weakeningOf(behavior).stream()
                            .filter("behavior_boundary_not_derived"::equals).count(),
                    () -> "what " + name + " went without: " + weakeningOf(behavior));
        }
        JsonNode receipt = behaviorOf(report, "receipt");
        assertFalse(receipt.has("signature"),
                "the boundary of `receipt` was worked out and its input was not read, so the cases"
                        + " at its positions were never seen");
        assertEquals(1, weakeningOf(receipt).stream()
                        .filter("behavior_input_not_read"::equals).count(),
                () -> "what receipt went without: " + weakeningOf(receipt));

        // The three states apart, in the document. `issue` has no section because nothing derived
        // what the model divides it into — the axes come off the boundary and no declaration gives
        // them, so an empty array would say the model divides none of its positions; `whole` has
        // one saying the measure has no subject, which is true of a composition whatever else is
        // wrong with it; `receipt` has none either, and for the other reason.
        assertFalse(behaviorOf(report, "issue").has("partition"),
                "no section where nothing derived what the model divides it into");
        assertEquals("no_subject", behaviorOf(report, "whole")
                        .get("partition").get("axesMeasure").get("reason").asString(),
                "a composition is measured at its stages, and the document says so");
        assertFalse(receipt.has("partition"),
                "nor where the input the model would divide was never read");
    }

    /**
     * And the line a person reads says which of the ways it has no number, rather than the words
     * of another.
     *
     * <p>Held per behavior and not over the report. The two states reach this line from one
     * behavior each, so a sentence found anywhere in the text is a sentence some behavior may not
     * have been given: the report said the signature could not be read under {@code receipt}, whose
     * signature was read, and the assertion that the sentence appears passed on {@code issue}.
     *
     * <p>The last assertion holds the other half. The sentence a reason is given is worth nothing on
     * its own — the line said {@code no row names this behavior} for every reason nobody had written
     * a word for, which is a sentence about a state none of these behaviors is in.
     */
    @Test
    void theLineSaysWhichOfTheWaysItHasNoNumber() {
        String text = AdequacyReport.of(measured())
                .human(souther.compiler.diag.SourceRendering.namedByIdentity(SourceLayouts.NONE));
        assertEquals("not measured (this behavior's signature could not be read)",
                signatureLineOf(text, "issue"),
                () -> "a name in its own declaration resolved to nothing:\n" + text);
        assertEquals("not measured (what this behavior takes was not read)",
                signatureLineOf(text, "receipt"),
                () -> "its signature was read and its input was not, so the author is not sent"
                        + " back to this declaration:\n" + text);
        assertFalse(text.contains("no row names this behavior"),
                () -> "and neither is the state of a behavior nobody wrote a row for:\n" + text);
    }

    /**
     * What the signature line of one behavior says, which is not what the report says somewhere.
     *
     * <p>The behavior's own block: a measure is printed under the behavior it is of, so a sentence
     * is that behavior's only where it is found under its name.
     */
    private static String signatureLineOf(String report, String behavior) {
        String opening = "    signature   ";
        List<String> lines = List.of(report.split("\n"));
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("  " + behavior + " ")) {
                continue;
            }
            for (int j = i + 1; j < lines.size() && lines.get(j).startsWith("    "); j++) {
                if (lines.get(j).startsWith(opening)) {
                    return lines.get(j).substring(opening.length());
                }
            }
            throw new AssertionError("`" + behavior + "` says nothing about its signature:\n"
                    + report);
        }
        throw new AssertionError("no behavior `" + behavior + "` in:\n" + report);
    }

    /** And no measure the bar rests on could be finished, so the verdict is undetermined. Not a
     *  build that asked for nothing: every one of them was asked for and started. */
    @Test
    void theVerdictIsUndetermined() {
        assertEquals(AdequacyReport.AdequacyStatus.UNDETERMINED,
                AdequacyReport.of(measured()).adequacy());
    }
}
