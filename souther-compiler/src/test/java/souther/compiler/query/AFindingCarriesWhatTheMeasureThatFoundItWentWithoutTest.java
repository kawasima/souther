package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a build refuses over is what <em>that</em> measure established, and not what the measure
 * beside it managed.
 *
 * <p>A finding's {@code weakenedBy} decides whether a build may refuse over it: a kind the bar
 * refuses, from a measurement nothing weakened, is a gap; the same kind from one that went without
 * something is undecided. So handing a finding the wrong measurement's account is not a reporting
 * slip — it is a build no longer refusing a gap it established.
 *
 * <p>Which is what happened. The findings about a signature were all handed the signature's own
 * weakening, and a signature is the union of its output's and every input's. One input with a row
 * nobody could classify made every output case undecided, against what the specification says
 * E1913 is for: a missing output case is established exactly when the <em>output case</em>
 * measurement was made in full.
 */
class AFindingCarriesWhatTheMeasureThatFoundItWentWithoutTest {

    /**
     * A model that compiles and runs, with a sum at an input and a sum at an output.
     *
     * <p>What it is for is the walk below, which holds every finding against the leaf it is about.
     * It does not reach a signature whose leaves went without different things — no source in reach
     * does — so the state that tells a right answer from a wrong one is built rather than compiled,
     * and handed to the same producer.
     */
    private static final String MODEL = """
            module demo

            data Ok

            data Approved = { id: Int }
            data Rejected = { why: String }
            data Decision = Approved | Rejected

            behavior weigh : (decision: Decision) -> Ok
            let weigh (decision) = Ok

            behavior makes : (id: Int) -> Decision
                constructs Decision, Approved
            let makes (id) = Approved { id = id }

            example weigh
                | (Approved { id = 1 }) -> Ok

            example makes
                | (1) -> Approved { id = 1 }
            """;

    private static List<Adequacy.Finding> findings(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation.db().ask(new Adequacy.Findings("demo")).value().stream()
                .filter(each -> each.subject().isBehavior(behavior)).toList();
    }

    private static Adequacy.SignatureEvidence signature(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation.db().ask(new Adequacy.Witnesses("demo")).value().get(behavior);
    }

    /**
     * Every finding about the cases of a signature carries the measurement that found it.
     *
     * <p>Held by comparing each finding's account against the leaf it is about, rather than against
     * a word: a finding about the output carries what the output's measurement went without, and one
     * about an input carries that input's. Where the two differ, handing over either would be a
     * value somebody could defend and only one of them is the measure that found it.
     */
    @Test
    void aFindingAboutOneLeafCarriesThatLeafsAccountAndNotItsSiblings() {
        Adequacy.SignatureEvidence signature = signature("weigh");
        List<Adequacy.Finding> findings = findings("weigh");
        assertFalse(findings.isEmpty(), "the model produces findings about its cases");

        int about = 0;
        for (Adequacy.Finding each : findings) {
            WeakeningSet owed = switch (each.about()) {
                case About.ACaseNoRowExpects _, About.ACaseNothingWasSeenToProduce _ ->
                        signature.output().cases().weakening();
                case About.ACaseNoRowAppliesItTo(var at, var _, var _) ->
                        signature.positions().get(at.at()).cases().weakening();
                default -> null;
            };
            if (owed == null) {
                continue;
            }
            about++;
            assertEquals(owed, each.weakenedBy(),
                    () -> "a finding about one measure carrying another's account: " + each);
        }
        assertTrue(about > 0, () -> "the model reaches a finding about a case: " + findings);
    }

    /**
     * The state the review named, built rather than compiled: one input measured in part, the
     * output measured in full, and a case no row expects.
     *
     * <p>Built because whether a source produces exactly this is a fact about a fixture, and what is
     * being held is a fact about the model — a signature is the union of its leaves, so its account
     * is strictly more than any one of them went without, and a finding given the union reads as
     * undecided over a measure beside the one it is about.
     *
     * <p>What that costs is not a word in a report. {@code OUTPUT_CASE_UNSPECIFIED} is a kind a
     * build is held to, so the finding stops being one a build refuses over and the work somebody is
     * owed goes unsaid (spec §e1913).
     */
    @Test
    void anInputMeasuredInPartDoesNotMakeTheOutputsGapUndecided() {
        TypeSymbol kept = symbol("example.scope.Kept");
        TypeSymbol dropped = symbol("example.scope.Dropped");
        TypeSymbol small = symbol("example.scope.Small");
        TypeSymbol large = symbol("example.scope.Large");

        // Read to the end: every row said which case it expected.
        OutputCaseEvidence output = OutputCaseEvidence.of("sort", Set.of(kept, dropped),
                new OutputCaseEvidence.Cases(Set.of(kept), Set.of(kept), Set.of(kept), 0, 1),
                true, WeakeningSet.none());
        // And one input with a row whose case could not be read.
        InputCaseEvidence unreadable = InputCaseEvidence.of("sort", 0, Set.of(small, large),
                Set.of(), new InputCaseEvidence.Cases(Set.of(small), Set.of(small), Set.of(small), 1),
                true, WeakeningSet.none());
        InputCaseEvidence read = InputCaseEvidence.of("sort", 1, Set.of(small, large), Set.of(),
                new InputCaseEvidence.Cases(Set.of(small), Set.of(small), Set.of(small), 0),
                true, WeakeningSet.none());

        Adequacy.SignatureEvidence signature =
                Adequacy.SignatureEvidence.of(output, List.of(unreadable, read));

        assertTrue(output.cases().weakening().isEmpty(), "the output was measured in full");
        assertFalse(unreadable.cases().weakening().isEmpty(), "and one input was not");
        assertFalse(signature.weakening().isEmpty(),
                "so the signature above them went without something");

        // Handed to the producer itself. Written against the rule instead, this would say what a
        // finding means and nothing about the one line that decides which measurement each finding
        // is given — which is the line that was wrong.
        List<Adequacy.Finding> found = new ArrayList<>();
        // What the declaration calls the two inputs, which is what a case of one is a class of.
        Adequacy.Findings.signatureFindings("sort", List.of("a", "b"), signature, found);
        assertFalse(found.isEmpty(), "the producer says something about these cases");

        Adequacy.AdequacyBar held = Adequacy.AdequacyBar.SIMPLIFIED_DOMAIN;
        assertEquals(Adequacy.Finding.Disposition.REFUSED,
                disposition(found, held, About.ACaseNoRowExpects.class, dropped),
                () -> "a gap the output's own measure established, read through the signature's: "
                        + found);
        assertEquals(Adequacy.Finding.Disposition.REFUSED,
                inputGap(found, held, 1, large),
                () -> "one position's unreadable row deciding another position's gap: " + found);
        assertEquals(Adequacy.Finding.Disposition.UNDECIDED,
                inputGap(found, held, 0, large),
                () -> "a gap from a measure that went without something: " + found);
    }

    /** What a build does about the one finding of {@code kind} about {@code missing}. */
    private static Adequacy.Finding.Disposition disposition(
            List<Adequacy.Finding> found, Adequacy.AdequacyBar held, Class<?> kind,
            TypeSymbol missing) {
        for (Adequacy.Finding each : found) {
            if (kind.isInstance(each.about())
                    && each.about() instanceof About.ACaseNoRowExpects(var what)
                    && what.equals(missing)) {
                return each.disposition(held);
            }
        }
        throw new AssertionError("no finding of " + kind.getSimpleName() + " about " + missing
                + " among " + found);
    }

    /** And of the one about the case {@code missing} at input {@code at}. */
    private static Adequacy.Finding.Disposition inputGap(
            List<Adequacy.Finding> found, Adequacy.AdequacyBar held, int at,
            TypeSymbol missing) {
        for (Adequacy.Finding each : found) {
            if (each.about() instanceof About.ACaseNoRowAppliesItTo(var input, var what, var _)
                    && input.at() == at && what.equals(missing)) {
                return each.disposition(held);
            }
        }
        throw new AssertionError("no finding about " + missing + " at input " + at
                + " among " + found);
    }

    /** A case, named. What it is a case of is nothing this rule turns on. */
    private static TypeSymbol symbol(String name) {
        return TypeSymbols.declared(new TypeKey("souther.decimal", name));
    }

}
