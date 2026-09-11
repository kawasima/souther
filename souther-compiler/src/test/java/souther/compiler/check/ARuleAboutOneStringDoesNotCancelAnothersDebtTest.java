package souther.compiler.check;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one field's rule is written as does not decide what another field owes.
 *
 * <p>A row at a point is a whole value with that point in it, so composing one has to fill every
 * position of the record. A field whose rule nothing could turn into a set of strings was a field
 * nothing could compose a value for, and the row that would have stood at a sibling's boundary was
 * never built — which the account then read as the sibling not being owed a row at all. Adding a
 * field took a debt off a field two lines away, and a model measured one day stopped being measured
 * the next (issue #1249).
 *
 * <p>Held over the predicates a domain writes about strings, each beside the same length rule. The
 * five say different things and none of them says anything about the length of another field, so
 * the account under each of them is the same account.
 */
class ARuleAboutOneStringDoesNotCancelAnothersDebtTest {

    /** Every way a domain says something about a string, with a value each admits. */
    private static final List<List<String>> RULES = List.of(
            List.of("String.length(value) >= 1", "\"x\""),
            List.of("String.matches(\"T[0-9]{13}\", value)", "\"T0000000000000\""),
            List.of("String.contains(\"市\", value)", "\"横浜市\""),
            List.of("String.startsWith(\"JP\", value)", "\"JP1\""),
            List.of("String.endsWith(\".jp\", value)", "\"a.jp\""),
            List.of("String.isEmpty(value) == false", "\"x\""));

    /** A name bounded on its length, and one other field carrying the rule under test. */
    private static String model(String rule, String value) {
        return """
                module example.beside

                data Ok

                data Name = String
                    invariant one = String.length(value) >= 2

                data Other = String
                    invariant it = RULE

                data Form = { name: Name, other: Other }

                behavior check : (f: Form) -> Ok

                let check (f) = Ok

                example check
                    | "a form" : (Form { name = Name("xx"), other = Other(VALUE) }) -> Ok
                """.replace("RULE", rule).replace("VALUE", value);
    }

    /**
     * The name's own point is owed and unmet, whatever is written beside it.
     *
     * <p>Unmet and not merely counted. A point the account cannot decide is counted too, so a check
     * that only read the denominator would pass over a model where every one of these had become a
     * question nobody could answer — which is the same defect one state further along.
     */
    @Test
    void theNamesBoundaryIsOwedWhateverTheFieldBesideItSays() {
        for (List<String> each : RULES) {
            String report = report(model(each.get(0), each.get(1)));

            assertTrue(report.contains(
                            "! no row is at the IN point String.length(value) in 2 <"
                                    + " String.length(value) (invariant Name (one))"),
                    () -> "`" + each.get(0) + "` beside it left the name's point unaccounted for:\n"
                            + report);
        }
    }

    /**
     * And what the name owes is the same under every one of them.
     *
     * <p>The name's own lines and not the whole account: a rule about the field beside it may owe
     * points of its own — a bound on a length draws a border and is short of the rows for it — and
     * that is the model owing more rather than the name owing less. What may not move is anything
     * said about the line the name's own declaration drew.
     */
    @Test
    void whatTheNameOwesIsTheSameUnderEveryOneOfThem() {
        String first = aboutTheName(report(model(RULES.get(0).get(0), RULES.get(0).get(1))));
        assertTrue(first.contains("invariant Name (one)"), "the name owes something to compare");
        for (List<String> each : RULES.subList(1, RULES.size())) {
            assertEquals(first, aboutTheName(report(model(each.get(0), each.get(1)))),
                    "`" + each.get(0) + "` is a rule about `other` and about nothing else");
        }
    }

    /** Everything the report says about the line the name's declaration drew. */
    private static String aboutTheName(String report) {
        return report.lines()
                .filter(each -> each.contains("invariant Name (one)")
                        || each.startsWith("adequacy:"))
                .map(String::strip)
                .reduce("", (one, other) -> one + other + "\n");
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
