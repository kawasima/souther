package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a rule this read from end to end costs a measurement, which is nothing.
 *
 * <p>One model, one clause, and the clause is the whole test. It draws a line; or it is read to the
 * end and leaves the positions no class of their own, three ways; or it is written in a form
 * nothing here takes apart. Everything read to the end costs the measurement nothing, and only the
 * last costs it what a limit of this compiler costs — a rule read from end to end reported as a
 * question nobody answered tells an author to go and look at a clause that is doing exactly what
 * they wrote.
 *
 * <p>And one that is read to the end and states the opposite. A comparison whose positions cancel
 * to something no row satisfies admits nothing rather than restricting nothing, so what it raises
 * is real — which is what keeps the reading above from being "the quantity is empty, so there is
 * nothing to answer for".
 *
 * <p>Asked of the document and of the sentence a person reads, because the two are written by
 * different code from the same evidence and this is the seam every round of #1029 and #1047 went
 * wrong at. The reason type told the two halves apart and the words underneath it did not.
 *
 * <p>What these hold to, said once so that a later reader has it:
 *
 * <ul>
 *   <li>What a rule cuts outranks how it is spelled. Which positions a rule restricts is settled by
 *       the quantity its canonical form cuts, and the spelling answers only where there is no
 *       quantity to be had.</li>
 *   <li>A comparison whose positions cancel and whose residue holds of every row raises no
 *       coverage obligation. One whose positions cancel and whose residue holds of none admits
 *       nothing, which is the opposite, and raises what it always did.</li>
 *   <li>A rule read to the end that draws no line is never a question nobody answered.</li>
 *   <li>Every question nobody answered is one whose reading actually stopped, which
 *       {@code RuleAccounting.Unaccounted} takes only a stop for.</li>
 *   <li>One comparison may come to different answers from different readings without being two
 *       rules, and the document still writes one word for it
 *       ({@code OneRuleReadTwoWaysIsTwoAnswersAndOneWordTest}).</li>
 * </ul>
 */
class ARuleReadToTheEndIsNotOneThisCouldNotReadTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One behavior, one record, and one clause written about a field it does not divide.
     *
     * <p>The behavior is deliberately about a different field. What the clause costs has to be read
     * off the clause rather than off anything the body does with it, and a body comparing the same
     * field would put a second reading over the first.
     */
    private static String model(String clause) {
        return """
                module m

                data Auto
                data Manual
                data Small
                data Large
                data Size = Small | Large

                data Box = { size: Size, name: String, lo: Int, hi: Int }
                %s

                behavior byBox : (b: Box) -> Auto | Manual
                let byBox (b) =
                    match b.size with
                        | Small -> Auto
                        | Large -> Manual

                example byBox
                    | "one" : (Box { size = Small, name = "n", lo = 1, hi = 2 }) -> Auto
                """.formatted(clause);
    }

    /** What one of these models came to, in the words both surfaces write it in. */
    private record Measured(String status, List<String> weakening, List<String> kinds,
                            String human) {

        boolean says(String word) {
            return human.contains(word);
        }
    }

    private static Measured of(String clause) {
        Compilation compilation = Compilation.ofSource(model(clause), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        AdequacyReport report = AdequacyReport.of(compilation);
        JsonNode document = JSON.readTree(report.json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        JsonNode behavior = document.get("modules").get(0).get("behaviors").get(0);
        List<String> weakening = new ArrayList<>();
        behavior.path("weakening").forEach(each -> weakening.add(each.asString()));
        List<String> kinds = new ArrayList<>();
        behavior.path("findings").forEach(each -> kinds.add(each.get("kind").asString()));
        // And what the module's declarations are short of, under the declarations that owe them. A
        // line an `invariant` drew is not any behavior's, so it is published under the module —
        // read off the behavior alone, a clause this drew a line from would look like one nothing
        // measured (issue #1062).
        document.get("modules").get(0).path("declarations").forEach(declaration ->
                declaration.get("findings")
                        .forEach(each -> kinds.add(each.get("kind").asString())));
        return new Measured(behavior.get("status").asString(), weakening, kinds,
                report.human(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }

    /** A clause that draws a line, which is what the three below are read against. */
    @Test
    void aClauseThatDrawsALineIsMeasuredWhole() {
        Measured measured = of("    invariant lo >= 0");

        assertEquals("complete", measured.status());
        assertEquals(List.of(), measured.weakening());
        assertTrue(measured.kinds().contains("boundary_unmet"), measured.kinds().toString());
        assertFalse(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * A clause read to the end that relates two positions, which divides neither.
     *
     * <p>Nothing is missing: both sides were recognised, and what the clause says is not a set of
     * one position's values. The document says the position has no class from this rule and asks
     * nothing further of anybody.
     */
    @Test
    void aClauseRelatingTwoPositionsCostsTheMeasurementNothing() {
        Measured measured = of("    invariant lo <= hi");

        assertEquals("complete", measured.status());
        assertEquals(List.of(), measured.weakening());
        assertTrue(measured.says("it relates two positions rather than dividing one"),
                measured.human());
        assertFalse(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * A clause read to the end whose quantity the position does not appear in.
     *
     * <p>{@code lo - lo >= 0} holds of every row, and the position it names cancels against itself.
     * It costs the measurement what the clause above costs it, which is nothing: no value anywhere
     * is admitted or refused, so there is no question about the values for anybody to answer.
     *
     * <p>The position is written twice, so a classification counting off the sides made it a rule
     * about {@code lo} raising the questions a rule about a position raises — which nothing could
     * answer, because the rule states neither of them. A clause this compiler read from end to end
     * came out as a question nobody had answered and took the measurement to partial with it.
     */
    @Test
    void aClauseCuttingNothingCostsTheMeasurementNothing() {
        Measured measured = of("    invariant lo - lo >= 0");

        assertTrue(measured.says("it was read to the end and cuts nothing this position appears in"),
                measured.human());

        assertEquals("complete", measured.status());
        assertEquals(List.of(), measured.weakening());
        assertFalse(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * The same clause under a different operator, which is the same rule about the model.
     *
     * <p>{@code lo - lo == 0} holds of every row exactly as {@code lo - lo >= 0} does. Which
     * operator an author wrote is no part of whether their rule states anything, so a fix that
     * turned on the operator would have left this one telling them that which values may stand at
     * {@code lo} is a question nothing answered.
     */
    @Test
    void whichOperatorIsWrittenDoesNotDecideIt() {
        Measured measured = of("    invariant lo - lo == 0");

        assertEquals("complete", measured.status());
        assertEquals(List.of(), measured.weakening());
        assertFalse(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * Every operator against every way the cancelled positions can leave a number.
     *
     * <p>Which rows a clause holds of, once its positions have cancelled, is what the number left
     * over stands to nought — and the operator says which way that has to be. {@code lo - lo <= 1}
     * holds of every row for the same reason {@code lo - lo >= 0} does, and neither is more read
     * than the other.
     *
     * <p>Both directions of every operator are here because the number left over is what
     * {@code left - right} came to, and a reading that took it as what {@code right - left} came to
     * would answer every strict comparison backwards while still agreeing about the equalities.
     * Read at nought alone the two are the same reading.
     */
    @Test
    void whichRowsAClauseHoldsOfIsWhatItsPositionsCancelTo() {
        List<String> expected = List.of(
                "lo - lo >= 0: every row", "lo - lo >= 1: not every row",
                "lo - lo >= -1: every row",
                "lo - lo > 0: not every row", "lo - lo > 1: not every row",
                "lo - lo > -1: every row",
                "lo - lo <= 0: every row", "lo - lo <= 1: every row",
                "lo - lo <= -1: not every row",
                "lo - lo < 0: not every row", "lo - lo < 1: every row",
                "lo - lo < -1: not every row",
                "lo - lo == 0: every row", "lo - lo == 1: not every row",
                "lo - lo == -1: not every row",
                "lo - lo /= 0: not every row", "lo - lo /= 1: every row",
                "lo - lo /= -1: every row");

        List<String> answered = new ArrayList<>();
        for (String operator : List.of(">=", ">", "<=", "<", "==", "/=")) {
            for (String against : List.of("0", "1", "-1")) {
                String clause = "lo - lo " + operator + " " + against;
                Measured measured = of("    invariant " + clause);
                answered.add(clause + ": "
                        + (measured.status().equals("complete") ? "every row" : "not every row"));
            }
        }

        assertEquals(expected, answered);
    }

    /**
     * And a clause whose positions cancel to something no row satisfies, which is the opposite.
     *
     * <p>{@code lo - lo >= 1} is {@code 0 >= 1}. The quantity it cuts is empty, exactly as the two
     * above, and the rule admits nothing rather than restricting nothing — so the questions it
     * raises are real ones and the rows that cannot be built are what a reader is shown. Read off
     * the empty quantity alone, this would have come out as a rule that states nothing.
     */
    @Test
    void aClauseNoRowSatisfiesIsNotOneThatRestrictsNothing() {
        Measured measured = of("    invariant lo - lo >= 1");

        assertEquals("partial", measured.status());
        assertTrue(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * A clause admitting values by a pattern, which is now one of the ones read to the end.
     *
     * <p>What this compiler was short of is what it is no longer short of. A format names the
     * strings a position may hold, which is the question the reading of values asks, so the rule is
     * taken in and the measurement is whole — and the two words a report writes for being short of
     * a rule are absent rather than reworded.
     *
     * <p>Beside {@link #aClauseNothingReadsIsAQuestionNobodyAnswered}, which is the same shape of
     * clause about the same position with a form this still has no word for. The pair is what says
     * the difference is the reading and not the shape of the sentence.
     */
    @Test
    void aClauseAdmittingValuesByAPatternIsMeasuredWhole() {
        Measured measured = of("    invariant String.matches(\"[a-z]+\", name)");

        assertEquals("complete", measured.status());
        assertEquals(List.of(), measured.weakening());
        assertFalse(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
        assertFalse(measured.says("written in a form this compiler does not read"),
                measured.human());
    }

    /**
     * A pattern this reads no deeper into, which is a limit of the reading and says so.
     *
     * <p>Every construct in it is one this reads. What stopped the reading is how deeply they are
     * written, which is this compiler's measure and not the author's spelling — told that the form
     * is one nothing reads, they would go looking for the construct that was the trouble.
     */
    @Test
    void aPatternThisReadsNoDeeperIntoIsSaidAsThat() {
        String deep = "(".repeat(201) + "a" + ")".repeat(201);
        Measured measured = of("    invariant String.matches(\"" + deep + "\", name)");

        assertTrue(measured.says("written more deeply nested than this compiler reads"),
                measured.human());
        assertFalse(measured.says("written in a form this compiler does not read"),
                measured.human());
    }

    /**
     * And a pattern stopped by a construct the subset has no word for, which is not that.
     *
     * <p>{@code \\p{Alpha}} names a property of a character, and the subset here names symbols by
     * their numbers. So the rule is one written in a form nothing read, like any other — the pair
     * with the one above is what says the two are told apart by what stopped the reading rather
     * than by the reading having stopped.
     */
    @Test
    void aPatternStoppedByAConstructThisHasNoWordForIsAFormNothingRead() {
        Measured measured = of("    invariant String.matches(\"\\\\p{Alpha}+\", name)");

        assertTrue(measured.says("written in a form this compiler does not read"), measured.human());
        assertFalse(measured.says("written more deeply nested than this compiler reads"),
                measured.human());
    }

    /**
     * And a clause nothing here takes apart, which is what a limit of this compiler looks like.
     *
     * <p>Here the question standing is the truth: what the clause says about the values was never
     * read, so what the position may hold is not known to have been read either. The ones above are
     * what this one has to stay different from.
     *
     * <p>The threshold is written as arithmetic the reading of lines does not take apart, and it
     * leaves the position values all the same. That second half is the fixture's whole difficulty:
     * a spelling this cannot read over a bound no value satisfies is two things at once, and the
     * one below is what settles which of them a measurement answers to.
     */
    @Test
    void aClauseNothingReadsIsAQuestionNobodyAnswered() {
        Measured measured = of("    invariant String.length(name) <= 1 - 0");

        assertEquals("partial", measured.status());
        assertTrue(measured.weakening().contains("question_unanswered"),
                measured.weakening().toString());
        // And one word for it. The clause states where the values stop, so the question is raised
        // and nothing answered it; the finding the reader made when it gave up is what a report
        // says about the same rule, and counting it here as well is one shortfall under two words.
        assertFalse(measured.weakening().contains("rule_unread"), measured.weakening().toString());
        assertTrue(measured.says("written in a form this compiler does not read"), measured.human());
        assertTrue(measured.kinds().contains("rule_unaccounted"), measured.kinds().toString());
    }

    /**
     * And where the rules leave the input no value, that outranks what any reading of them managed.
     *
     * <p>The same clause as above with the bound moved past where a length runs. Two things are true
     * of it: this reading does not take the threshold apart, and no value of the position satisfies
     * it. They belong to different layers — whether the rules leave anything for a measure to be
     * about decides that there is a measurement, and how far a reading got decides how good one is —
     * and the first is asked first.
     *
     * <p>So the measurement is not weaker, it is not there: a class is a set of values a row can be
     * written at, and there are none. Reported as a reading that fell short, an author is asked for
     * rows over a model that admits none, and the proof that says so is thrown away in favour of
     * what one reader could not spell.
     *
     * <p>Not a gap either. Nothing here is a row somebody has not written yet.
     */
    @Test
    void aProvedEmptyInputOutranksWhatAReadingOfItManaged() {
        Measured measured = of("    invariant String.length(name) <= 0 - 1");

        assertTrue(measured.says("the rules reaching this behavior's input leave it no value"),
                measured.human());
        assertFalse(measured.weakening().contains("rule_unread"),
                "the measurement is not weaker, it is not there: " + measured.weakening());
        assertFalse(measured.weakening().contains("question_unanswered"),
                "and nothing stands unanswered about a position no row reaches: "
                        + measured.weakening());
    }
}
