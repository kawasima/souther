package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clause the tests around here stand on is still one no reading takes in.
 *
 * <p>Every property they need of it, held in one place. A test using {@link ARuleNoReadingTakesIn}
 * is measuring what a reading does with a rule it cannot read, and each of them would go on passing
 * over a clause this compiler had since learned — it would be measuring nothing, and saying so
 * about a model where nothing is left unread.
 *
 * <p>So the day a capability takes this clause in, this test fails and the others do not. What it
 * says then is what has happened: the examples need a new spelling, and here is the list of
 * properties the new one has to have.
 */
class ARuleNoReadingTakesInIsStillOneTest {

    private static final String SUBJECT = "value";

    /** A model of one string position, with the clause and nothing else about it. */
    private static String alone() {
        return """
                module example.unread

                data Ok

                data S = String
                    invariant CLAUSE

                data Form = { s: S }

                behavior check : (f: Form) -> Ok

                let check (f) = Ok
                """.replace("CLAUSE", ARuleNoReadingTakesIn.about(SUBJECT));
    }

    /**
     * The clause beside a rule that draws a line, so that there is a point to be wrong about.
     *
     * <p>Satisfiable: a string of two characters that is not empty holds both. What a reading that
     * had quietly narrowed the position to the values it read out of the clause would say instead is
     * that the rules leave nothing there.
     */
    private static String besideALine(String clause) {
        return """
                module example.unread

                data Ok

                data S = String
                    invariant CLAUSE String.length(value) >= 2

                data Form = { s: S }

                behavior check : (f: Form) -> Ok

                let check (f) = Ok
                """.replace("CLAUSE", clause);
    }

    /** It is something an author may write: it parses, it types, and nothing is refused. */
    @Test
    void itIsAClauseAnAuthorMayWrite() {
        Compilation compilation = Compilation.ofSource(alone(), "Main");
        compilation.answerEverything();

        assertTrue(compilation.errors().isEmpty(),
                () -> "the clause no longer compiles: " + compilation.errors());
    }

    /**
     * And no reading takes it in, which the report says in the words it promises for that.
     *
     * <p>Read off the report rather than off a reader, because what the tests using this need is
     * that <em>every</em> reading stopped on it — a check against one of them would pass while
     * another had learned it.
     */
    @Test
    void noReadingTakesItIn() {
        String report = report(alone());

        assertTrue(report.contains("written in a form this compiler does not read"),
                () -> "something now reads the clause:\n" + report);
    }

    /**
     * And it narrows nothing.
     *
     * <p>The property that keeps a fixture honest rather than merely unread. A clause read as
     * something about the position — {@code String.trim(value) == "x"} taken for {@code value ==
     * "x"} — would be unread in name and narrowing in fact, and a test standing on it would be
     * measuring a reading that had already answered.
     *
     * <p>Asked as a difference and not as a sentence, because the sentence is one the line beside
     * it says on its own: a bound at two leaves nothing at one, and that is the rules doing what
     * they say. What the clause may not do is add to what is claimed empty.
     */
    @Test
    void itNarrowsNothing() {
        assertEquals(emptyPlaces(report(besideALine(""))),
                emptyPlaces(report(besideALine(ARuleNoReadingTakesIn.about(SUBJECT) + " &&"))),
                "the clause took values away from the position it is about");
    }

    /** Every place the report says the rules leave nothing, which is what may not move. */
    private static List<String> emptyPlaces(String report) {
        return report.lines().filter(each -> each.contains("the rules leave no value there"))
                .map(String::strip).sorted().toList();
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
