package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.check.NumberAt;
import souther.compiler.check.Owed;
import souther.compiler.check.RuleKey;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A question about a number taken of a position names that number.
 *
 * <p>The subject of a coverage question said <em>that</em> a number was taken and not which, so
 * every reader that wanted the name reached past the question to whatever stood beside it. The
 * document's {@code subject.measure} is written from the question, and it was written from the axis:
 * a position with no axis at all still has whatever was written about it, and the axis beside a
 * question is not always measured at the number that question is about.
 *
 * <p>What it published is in {@link #theNumberIsNamedAndNotTheThingItWasTakenOf}: a rule about how
 * long a list is came back with the list's own path in the field that says which count the line
 * falls on — so a consumer reading the document was told that the line falls on a count and then
 * handed the position as the count.
 */
class AQuestionAboutANumberNamesTheNumberTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * A rule about the length of a list that no reading turns into a line.
     *
     * <p>{@code 10 * 2} is a number this compiler does not fold, so the rule states where the values
     * stop and nothing placed the end — which is a boundary question standing, about the length. The
     * position itself has no axis: nothing divides a bare {@code List<String>}, so there is no axis
     * measured at the length for a reader to take the name from.
     */
    private static final String A_RULE_ABOUT_A_LENGTH = """
            module m

            data Tags = { names: List<String> }
                invariant said = List.length(names) <= 10 * 2

            behavior g : (t: Tags) -> Int
            let g (t) = 1

            example g
                | "one" : (Tags { names = [] }) -> 1
            """;

    /** The same rules, with a body drawing a line on the length, which is a measure of its own. */
    private static final String THE_SAME_UNDER_A_GUARD = """
            module m

            data Tags = { names: List<String> }
                invariant said = List.length(names) <= 10 * 2

            behavior g : (t: Tags) -> Int
            let g (t) = if List.length(t.names) > 0 then 1 else 2

            example g
                | "one" : (Tags { names = [] }) -> 2
            """;

    private static JsonNode partitionOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)))
                .get("modules").get(0).get("behaviors").get(0).get("partition");
    }

    /** One question of the document, as `question at subject`, with the number where there is one. */
    private static List<String> standingIn(String source) {
        List<String> out = new ArrayList<>();
        for (JsonNode each : partitionOf(source).get("unanswered")) {
            JsonNode about = each.get("subject");
            JsonNode measure = about.get("measure");
            out.add(each.get("question").asString() + " at " + about.get("path").asString()
                    + (measure == null ? "" : " on " + measure.asString()));
        }
        return out;
    }

    /**
     * The number, and not the thing it was taken of.
     *
     * <p>What this asserted before the question carried the number was
     * {@code boundary at t.names on t.names}: the field that says which count the line falls on
     * held the position, because the only name to hand was the axis's and the axis is measured at
     * the list rather than at its length.
     */
    @Test
    void theNumberIsNamedAndNotTheThingItWasTakenOf() {
        assertEquals(List.of(
                        "admitted_values at t.names",
                        "boundary at t.names on List.length(t.names)"),
                standingIn(A_RULE_ABOUT_A_LENGTH),
                "the line falls on the length, and the values it admits are the list's");
    }

    /**
     * And it does not move when a body draws on the same position.
     *
     * <p>A body comparing another number of the position adds a measure of that number beside the
     * ones the declarations made; it does not take a question with it. A question is the model's
     * and is about the number its own rule names, so what a body compares changes neither which
     * questions stand nor which number each is about.
     *
     * <p>These two documents disagreed. One rule, one question, and
     * {@code boundary at t.names on t.names} with no guard against
     * {@code boundary at t.names on List.length(t.names)} with one — the published number being
     * whatever the axis beside the question had ended up measured at.
     */
    @Test
    void aBodyDrawingOnThePositionMovesNoQuestion() {
        assertEquals(standingIn(A_RULE_ABOUT_A_LENGTH), standingIn(THE_SAME_UNDER_A_GUARD),
                "the same rules raise the same questions about the same numbers, whatever the"
                        + " axis beside them ended up being measured at");
    }

    /** The question about which values may stand there is asked once, however many numbers the
     *  position is measured by. */
    @Test
    void thePositionsOwnQuestionIsAskedOnce() {
        List<JsonNode> admitted = new ArrayList<>();
        for (JsonNode each : partitionOf(A_RULE_ABOUT_A_LENGTH).get("unanswered")) {
            if (each.get("question").asString().equals("admitted_values")) {
                admitted.add(each);
            }
        }

        assertEquals(1, admitted.size(),
                () -> "one rule, one question about the values, however many numbers the position"
                        + " is measured by: " + admitted);
        assertNull(admitted.get(0).get("subject").get("measure"),
                "and no number beside it, because it is not about one");
    }

    /**
     * Two operations over one path are two questions.
     *
     * <p>The identities and nothing that reads them, which is the half a record answers for. What
     * reads them is asked in
     * {@code check.AskingForOneNumbersEndsDoesNotAnswerWithAnothersTest}: these being two values is
     * no use if the selector beside them tells them apart by whether a number was taken, and that
     * is the reading that regressed before.
     *
     * <p>The language declares one number taken of a {@code List} today, so this is asked of the
     * identities rather than of a model.
     */
    @Test
    void twoNumbersAtOnePathStayTwo() {
        ValueName length = ValueName.Stdlib.operation("List", "length");
        ValueName size = ValueName.Stdlib.operation("Set", "size");

        assertNotEquals(new Owed.Boundary(NumberAt.takenOf(RuleKey.of("names"), length)),
                new Owed.Boundary(NumberAt.takenOf(RuleKey.of("names"), size)),
                "a line on one operation's number is not a line on another's");
        assertNotEquals(new Owed.Boundary(NumberAt.takenOf(RuleKey.of("names"), length)),
                new Owed.Boundary(NumberAt.valueOf(RuleKey.of("names"))),
                "nor a line on what the position itself holds");
        assertNotEquals(new Owed.Boundary(NumberAt.takenOf(RuleKey.of("names"), length)),
                new Owed.AdmittedValues(RuleKey.of("names")),
                "and a question about a number is not the question about the position");
    }

    /**
     * And the other side of the crossing keeps the two questions one position raises apart.
     *
     * <p>Which values may stand at a position and where a line on a number of it falls are two
     * questions at one path, and a reader matching an axis compares what each says it is about. Both
     * say where they sit, so a reader that wanted only the path could have neither told apart —
     * which is why what each asks is the arm rather than something carried beside it.
     */
    @Test
    void thePositionsTwoQuestionsAreTwoAtOnePath() {
        TermPath at = TermPath.of("t").then("names");
        InputQuestion aLine = new InputQuestion.AboutANumber(NumberAt.valueOf(at));
        InputQuestion itsValues = new InputQuestion.AboutAPosition(at);

        assertNotEquals(aLine, itsValues,
                "where a line falls on a position's own values is not which values may stand there");
        assertEquals(at, aLine.path(),
                "and both say where they sit, which is what an axis is matched on");
        assertEquals(at, itsValues.path());
        assertNotEquals(aLine.obligation(), itsValues.obligation(),
                "what each asks follows from which it is, and is not carried beside it");
    }
}
