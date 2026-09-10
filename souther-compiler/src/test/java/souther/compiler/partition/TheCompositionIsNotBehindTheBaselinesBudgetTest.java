package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a class comes to when the values the model states ran out of room.
 *
 * <p>A class is looked for against every value the model states, each walked outward from the
 * target alone, and that walk is bounded. Behind the same bound sat the row composed from the
 * classes, which is not one of those values: where it stands is where the classes put it, and the
 * classes are what the search itself named. So it was not competing with the stated values for
 * their budget, and a model that stated enough of them took away the one row that needed none.
 *
 * <p>What the reader was handed instead was the search saying it had stopped, over a class the
 * model can hold a row for. That is a claim about the model made on the strength of a limit: it
 * sends a person looking for a rule to change where what they have is a row somebody can write in
 * a line.
 *
 * <p>The bound still says what it says. A walk that ran out of assignments and one that ran out of
 * budget are different facts about the search, and the second is one of the things
 * {@code THE_SEARCH_LEFT_SOMETHING_UNTRIED} is for — it decides the class's answer where nothing
 * else could be written for it, rather than in front of the composition. What the word says is that
 * something was left, and which of the two ways it was left is what travels beside it.
 */
class TheCompositionIsNotBehindTheBaselinesBudgetTest {

    /**
     * A rule relating two fields, and more values of the type than the walk has room for.
     *
     * <p>Every one of them holds {@code lo} above the class {@code hi} is looked for in, so the
     * target moved alone is refused at each, and there are more of them than the walk has room for.
     * The values are alike on purpose: what is being asked is what is left once they are spent, and
     * a value that built would end the walk before it.
     */
    private static String crowded() {
        StringBuilder lets = new StringBuilder();
        for (int i = 0; i <= 64; i++) {
            lets.append("let m").append(i)
                    .append(" = Request { lo = Amount(60), hi = Amount(7").append(i % 10)
                    .append(") }\n\n");
        }
        return """
                module example.trip

                data Amount = Int
                    invariant value >= 0

                data Request = { lo: Amount, hi: Amount }
                    invariant lo.value <= hi.value

                data Accepted = { at: String }

                %s
                behavior submit : (request: Request) -> Accepted
                    constructs Accepted

                let submit (request) = {
                    guard request.lo.value <= 50 else Accepted { at = "wide" }
                    guard request.hi.value <= 60 else Accepted { at = "tall" }
                    Accepted { at = "now" }
                }
                """.formatted(lets.toString());
    }

    /**
     * The class every stated value was spent on is answered by the composition.
     *
     * <p>{@code hi} at the bottom of its lower class is under every stated {@code lo}, which the
     * rule refuses, so each of the sixty-five values fails at the target alone and the walk over
     * them ends where its bound is. The row composed from the classes puts both positions at the
     * bottom of their own, which the rule allows — so there is a row here, and it is one the reader
     * gets rather than a sentence about a search.
     */
    @Test
    void theCompositionAnswersAClassEveryStatedValueWasSpentOn() {
        Adequacy.Filling filling = generated(crowded()).get("submit");
        assertNotNull(filling, "the behavior under test is generated for");

        ClassDisposition at = attemptAtTheLowerHi(filling);
        assertEquals(List.of("Request { lo = Amount(0), hi = Amount(0) }"),
                filling.composed().rowFor(((ClassDisposition.Built) at).rowId()).inputs().stream()
                        .map(FixtureTemplate::text).toList(),
                "composed from the classes, which is what a row is where none of the values the "
                        + "model states can be written for it: " + at);
    }

    /** What the search made of the class {@code hi} takes below the line the body draws. */
    private static ClassDisposition attemptAtTheLowerHi(Adequacy.Filling filling) {
        for (Map.Entry<ClassOfAPosition, ClassDisposition> each
                : filling.composed().discharge().classes().entrySet()) {
            if (each.getKey().at().term().endsWith("hi")
                    && each.getKey().classId().contains("0")) {
                return each.getValue();
            }
        }
        throw new AssertionError("the lower class of `hi` is one the search was asked about: "
                + filling.composed().discharge().classes());
    }

    private static Map<String, Adequacy.Filling> generated(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> all = Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return all;
    }
}
