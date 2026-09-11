package souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One rule of a model gets one mapping decision, whichever way it was written. A length floor
 * written out, the same floor reached through a helper, and the same floor written as the denial of
 * its opposite are one rule, so the decoder derived from each reports the same code with the same
 * metadata.
 *
 * <p>What a constraint decides is how a violation is reported rather than whether it is checked, so
 * the spelling moving the decision moves public boundary behaviour: the code a caller matches on and
 * the metadata a message is resolved from. An author choosing between two ways of saying one thing
 * is not choosing between two boundaries.
 *
 * <p>The last rule here is the control. The claim is that equivalent spellings are mapped alike, not
 * that every rule becomes a constraint: a rule with no exact Raoh equivalent falls back however it
 * was written, and the three spellings of it agree on that.
 */
class WhatADecoderReportsDoesNotTurnOnHowTheRuleWasSpeltTest {

    /** The sole issue a decoder for {@code demo.V} raises about {@code input}. */
    private static Issue refusalOf(String declarations, Object input) throws Exception {
        String module = """
                module demo

                %s
                """.formatted(declarations);
        ClassLoader loader = new BytesClassLoader(Compiler.compile(module),
                WhatADecoderReportsDoesNotTurnOnHowTheRuleWasSpeltTest.class.getClassLoader());
        Decoder<Object, ?> dec = Codecs.decoder(loader, "demo.V");
        Result<?> r = dec.decode(input, Path.ROOT);
        assertTrue(r instanceof Err, "the value breaks the rule, so decoding must fail");
        List<Issue> issues = ((Err<?>) r).issues().asList();
        assertEquals(1, issues.size(), "one broken rule, one issue");
        return issues.get(0);
    }

    /** What a refusal says, as the pair a caller downstream reads. */
    private record Refusal(String code, Object bound) {

        static Refusal of(Issue issue, String key) {
            return new Refusal(issue.code(), issue.meta().get(key));
        }
    }

    // --- a floor on a string's length ---

    private static final String LENGTH_FLOOR_WRITTEN_OUT = """
            data V = String
                invariant String.length(value) >= 1
            """;

    private static final String LENGTH_FLOOR_THROUGH_A_HELPER = """
            let atLeastOne (s: String) = String.length(s) >= 1

            data V = String
                invariant atLeastOne(value)
            """;

    private static final String LENGTH_FLOOR_UNDER_A_DENIAL = """
            data V = String
                invariant Bool.not(String.length(value) < 1)
            """;

    /** The positive control for the length floor: written out, the floor is Raoh's own constraint. */
    @Test
    void aLengthFloorWrittenOutIsTooShort() throws Exception {
        assertEquals(new Refusal("too_short", 1),
                Refusal.of(refusalOf(LENGTH_FLOOR_WRITTEN_OUT, ""), "min"));
    }

    @Test
    void aLengthFloorReachedThroughAHelperIsTheSameRefusal() throws Exception {
        assertEquals(Refusal.of(refusalOf(LENGTH_FLOOR_WRITTEN_OUT, ""), "min"),
                Refusal.of(refusalOf(LENGTH_FLOOR_THROUGH_A_HELPER, ""), "min"));
    }

    @Test
    void aLengthFloorWrittenAsADenialIsTheSameRefusal() throws Exception {
        assertEquals(Refusal.of(refusalOf(LENGTH_FLOOR_WRITTEN_OUT, ""), "min"),
                Refusal.of(refusalOf(LENGTH_FLOOR_UNDER_A_DENIAL, ""), "min"));
    }

    // --- a bound on a numeric newtype's own value ---

    private static final String OWN_VALUE_BOUND_WRITTEN_OUT = """
            data V = Int
                invariant value >= 3
            """;

    private static final String OWN_VALUE_BOUND_THROUGH_A_HELPER = """
            let atLeastThree (n: Int) = n >= 3

            data V = Int
                invariant atLeastThree(value)
            """;

    private static final String OWN_VALUE_BOUND_UNDER_A_DENIAL = """
            data V = Int
                invariant Bool.not(value < 3)
            """;

    /** The positive control for the numeric bound. */
    @Test
    void anOwnValueBoundWrittenOutIsOutOfRange() throws Exception {
        assertEquals(new Refusal("out_of_range", 3L),
                Refusal.of(refusalOf(OWN_VALUE_BOUND_WRITTEN_OUT, 2L), "min"));
    }

    @Test
    void anOwnValueBoundReachedThroughAHelperIsTheSameRefusal() throws Exception {
        assertEquals(Refusal.of(refusalOf(OWN_VALUE_BOUND_WRITTEN_OUT, 2L), "min"),
                Refusal.of(refusalOf(OWN_VALUE_BOUND_THROUGH_A_HELPER, 2L), "min"));
    }

    @Test
    void anOwnValueBoundWrittenAsADenialIsTheSameRefusal() throws Exception {
        assertEquals(Refusal.of(refusalOf(OWN_VALUE_BOUND_WRITTEN_OUT, 2L), "min"),
                Refusal.of(refusalOf(OWN_VALUE_BOUND_UNDER_A_DENIAL, 2L), "min"));
    }

    // --- a conjunct that states more than one rule ---

    private static final String RANGE_WRITTEN_OUT = """
            data V = Int
                invariant value >= 1 && value <= 10
            """;

    private static final String RANGE_UNDER_A_DENIAL = """
            data V = Int
                invariant Bool.not(value < 1 || value > 10)
            """;

    /**
     * A denied choice is one conjunct stating one rule per branch, and each of them reaches the
     * boundary as the constraint it is.
     *
     * <p>What the author wrote as two conjuncts and what they wrote as one denied choice are the
     * same two rules, so a value breaking either is refused in the same words. Read off the tree,
     * the denial was a shape the mapping had no word for and both rules fell back together.
     */
    @Test
    void aDeniedChoiceStatesBothOfItsRulesAtTheBoundary() throws Exception {
        assertEquals(Refusal.of(refusalOf(RANGE_WRITTEN_OUT, 0L), "min"),
                Refusal.of(refusalOf(RANGE_UNDER_A_DENIAL, 0L), "min"),
                "the rule the value breaks below the run");
        assertEquals(Refusal.of(refusalOf(RANGE_WRITTEN_OUT, 11L), "max"),
                Refusal.of(refusalOf(RANGE_UNDER_A_DENIAL, 11L), "max"),
                "and the one it breaks above it");
    }

    /**
     * A conjunct only some of whose rules a constraint states keeps its own check, all of it.
     *
     * <p>The policy the statements are read for. {@code Bool.not(value < 3 || value == 7)} states a
     * floor, which Raoh has a constraint for, and a value held away from one, which it has none
     * for — and they are one conjunct. Hoisting the floor out of it reports the value that breaks
     * the floor as {@code out_of_range} and the value that breaks the other rule as an invariant
     * violation, so one thing the author wrote breaks in two different words depending on which
     * half of it the value broke.
     *
     * <p>Which is what tells this from the rules being unmappable: the floor is mapped where its
     * author wrote it as a conjunct of its own, and that is the case below.
     */
    @Test
    void aConjunctOnlyPartlyMappableKeepsItsOwnCheckWhole() throws Exception {
        Issue issue = refusalOf("""
                data V = Int
                    invariant Bool.not(value < 3 || value == 7)
                """, 2L);
        assertEquals("invariant_violation", issue.code(),
                "the conjunct states a rule no constraint says, so none of it is hoisted");
    }

    /** The same two rules as two conjuncts, where the floor is the author's own unit and is
     *  mapped. */
    @Test
    void andTheSameRulesWrittenAsTwoConjunctsHoistTheOneThatMaps() throws Exception {
        assertEquals(new Refusal("out_of_range", 3L),
                Refusal.of(refusalOf("""
                        data V = Int
                            invariant value >= 3 && value /= 7
                        """, 2L), "min"));
    }

    // --- a rule stated as an operation rather than as a comparison ---

    private static final String PATTERN_WRITTEN_OUT = """
            data V = String
                invariant String.matches("[0-9]{3}", value)
            """;

    private static final String PATTERN_THROUGH_A_HELPER = """
            let shaped (s: String) = String.matches("[0-9]{3}", s)

            data V = String
                invariant shaped(value)
            """;

    /**
     * And a rule an operation states rather than a comparison is read through a helper too.
     *
     * <p>What a binding is does not depend on what stands under it, so a rule the mapping recognises
     * by which operation it is written in terms of reaches the boundary the same way a comparison
     * does.
     */
    @Test
    void anOperationStatingARuleIsTheSameRefusalThroughAHelper() throws Exception {
        assertEquals(Refusal.of(refusalOf(PATTERN_WRITTEN_OUT, "12x"), "pattern"),
                Refusal.of(refusalOf(PATTERN_THROUGH_A_HELPER, "12x"), "pattern"));
    }

    // --- the control: a rule no Raoh constraint states ---

    private static final String NO_EQUIVALENT_WRITTEN_OUT = """
            data V = String
                invariant List.all(c -> c <= 57, String.codePoints(value))
            """;

    private static final String NO_EQUIVALENT_THROUGH_A_HELPER = """
            let digitsOnly (s: String) = List.all(c -> c <= 57, String.codePoints(s))

            data V = String
                invariant digitsOnly(value)
            """;

    private static final String NO_EQUIVALENT_UNDER_A_DENIAL = """
            data V = String
                invariant Bool.not(List.any(c -> c > 57, String.codePoints(value)))
            """;

    /**
     * A rule with no exact equivalent keeps the check it already has, and says so the same way
     * whichever spelling it arrived in: were a spelling to change what falls back, this would be a
     * change to which rules become constraints rather than to how one rule is read.
     */
    @Test
    void aRuleNoConstraintStatesFallsBackHoweverItWasSpelt() throws Exception {
        Refusal writtenOut = Refusal.of(refusalOf(NO_EQUIVALENT_WRITTEN_OUT, "1a2"), "type");
        assertEquals(new Refusal("invariant_violation", "V"), writtenOut);
        assertEquals(writtenOut, Refusal.of(refusalOf(NO_EQUIVALENT_THROUGH_A_HELPER, "1a2"), "type"));
        assertEquals(writtenOut, Refusal.of(refusalOf(NO_EQUIVALENT_UNDER_A_DENIAL, "1a2"), "type"));
    }
}
