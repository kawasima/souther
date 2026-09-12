package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a question's rule left is said in the order the places it stands on were written.
 *
 * <p>The order is the author's or it is nobody's. A reading meets the parts of a clause in whatever
 * order it walks them, and a list handed on afterwards says somebody put it in an order without
 * saying who — so a reader taking the first entry as the first thing to lift is reading a fact about
 * a walk unless the places decided it.
 *
 * <p><b>Asked of the document and not of the reading.</b> What a reading publishes holds no place:
 * which construct each reason is about is counted over what the author wrote, and the numbers it is
 * counted by are a function of that syntax rather than of the order it is written in. So the order
 * is settled where the places are resolved, which is where a document is written — and the reading's
 * answer is the same whichever order the walk met them in.
 *
 * <p>Measured by writing the same two clauses the other way round. Nothing else about the model
 * changes, so anything that comes out different is what the author's order settles, and anything
 * that comes out the same is settled by something else.
 */
class WhatAQuestionStandsOnIsSaidInTheOrderItWasWrittenTest {

    private static final String UNREAD_Y = souther.compiler.ARuleNoReadingTakesIn.about("y");

    /** What the document says about the pattern it will not build. */
    private static final String COSTLY = "read to the end, and the values the rules about this"
            + " position leave between them are more than this compiler will work out";

    /** And about the form nothing takes apart. */
    private static final String UNREAD = "written in a form this compiler does not read";

    private static String model(String clause) {
        return """
                module demo
                data Yes
                data No
                data Answer = Yes | No

                data N = { y: String }
                    invariant r = %s

                behavior check : (v: N) -> Answer
                let check (v) = Yes
                """.formatted(clause);
    }

    /**
     * A pattern this compiler will not build, beside a form no reading takes apart.
     *
     * <p>Two limits of one rule at one position, lifted by different work, so the question stands on
     * both and an author has two places to look at.
     */
    private static final String COSTLY_THEN_UNREAD =
            model("String.matches(\"a{60000}\", y) && " + UNREAD_Y);

    /** The same two clauses, written the other way round. */
    private static final String UNREAD_THEN_COSTLY =
            model(UNREAD_Y + " && String.matches(\"a{60000}\", y)");

    /** The clause written first is the first thing a reader is sent to. */
    @Test
    void theReasonsComeOutInTheOrderTheirClausesWereWritten() {
        assertEquals(List.of(COSTLY, UNREAD), saidOf(COSTLY_THEN_UNREAD),
                "the clause an author wrote first is the first thing they are sent to");
    }

    /** And written the other way round, the form does. */
    @Test
    void andTheOtherWayRoundTheyComeOutTheOtherWayRound() {
        assertEquals(List.of(UNREAD, COSTLY), saidOf(UNREAD_THEN_COSTLY),
                "which is what makes the order the author's rather than the walk's");
    }

    /** What the document says the question stands on, in the order it says them. */
    private static List<String> saidOf(String source) {
        List<String> said = standingOn(source);
        assertEquals(1, said.size(), "one rule, one position, one question that nothing answered");
        return List.of(said.getFirst().split("; "));
    }

    /** The sentence about every question of this model that nothing answered. */
    private static List<String> standingOn(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation)
                .human(SourceRendering.namedByIdentity(compilation.texts())).lines()
                .map(String::strip)
                .filter(each -> each.contains("not accounted for:"))
                .map(each -> each.substring(each.lastIndexOf(": ") + 2))
                .toList();
    }
}
