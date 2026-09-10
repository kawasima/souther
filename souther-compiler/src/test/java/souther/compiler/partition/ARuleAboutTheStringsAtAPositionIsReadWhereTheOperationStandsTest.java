package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.StringPredicates;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternSyntax;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a behavior states about the strings at one of its positions, read off the tree that has it.
 *
 * <p>{@code String.startsWith} is one of the language's own operations. The tree a backend emits has
 * it expanded into the walk it turns into, so a body written full of rules about its strings holds
 * none there — which is where such a rule was being looked for, and why a position an author had
 * written one for admitted every string there is.
 *
 * <p>Three things are held here. That the rule is found at all; that one rule read twice is two
 * readings of one rule, since a helper holding it is expanded at each call and the two divide the
 * position differently; and that a rule nothing reads is no rule, because a class divided off a
 * position by it is one no value of the model is ever on either side of.
 */
class ARuleAboutTheStringsAtAPositionIsReadWhereTheOperationStandsTest {

    /** Every string that begins with {@code written}, which is what {@code startsWith} states. */
    private static PatternSyntax beginningWith(String written) {
        return new PatternSyntax.InTurn(
                List.of(PatternSyntax.text(written), PatternSyntax.anything()));
    }

    @Test
    void aPredicateAppliedInABodyIsReadAsTheStringsItStates() {
        List<PredicateReadings.Reading> read = readingsOf("""
                behavior f : (code: String) -> Answer
                let f (code) = if String.startsWith("JP", code) then Yes else No
                """);

        assertEquals(1, read.size(), "the body states one rule about the strings at its position");
        assertEquals(new StringPredicates.Reading.Accepting(beginningWith("JP")),
                read.get(0).reading(),
                "and what it states is the strings that begin with what the author wrote");
        assertEquals("f", read.get(0).origin().rule().behavior(),
                "filed under the behavior whose body it stands in");
    }

    /**
     * A rule written under a name is read against what that name stands for.
     *
     * <p>The text is reached through the walk's own reading of the names in force, and a reader that
     * folded only what it could see for itself would report this rule as one whose argument nothing
     * worked out — while holding the answer.
     */
    @Test
    void theTextIsReachedThroughTheNamesInForceWhereTheRuleStands() {
        List<PredicateReadings.Reading> read = readingsOf("""
                behavior f : (code: String) -> Answer
                let f (code) = {
                    let prefix = "JP"
                    if String.startsWith(prefix, code) then Yes else No
                }
                """);

        assertEquals(1, read.size(), "the body states one rule");
        assertEquals(new StringPredicates.Reading.Accepting(beginningWith("JP")),
                read.get(0).reading(),
                "and the name stands for the text the author wrote");
    }

    /**
     * One rule applied twice is one rule and two readings.
     *
     * <p>The whole of why a reading is filed under an occurrence rather than under the rule. The two
     * applications divide the position into different sets, and filed under the rule alone the
     * second would close the account the first opened.
     */
    @Test
    void oneRuleAppliedTwiceIsTwoReadingsOfOneRule() {
        List<PredicateReadings.Reading> read = readingsOf("""
                let holds (p: String, s: String) : Bool = String.startsWith(p, s)

                behavior f : (code: String) -> Answer
                let f (code) =
                    if holds("JP", code) then Yes
                    else if holds("US", code) then Yes else No
                """);

        assertEquals(2, read.size(), "the body reads the rule at each place it is applied");
        assertEquals(read.get(0).origin().rule(), read.get(1).origin().rule(),
                "and the two are readings of the one rule the author wrote");
        assertNotEquals(read.get(0).origin().occurrence(), read.get(1).origin().occurrence(),
                "told apart by which reading of it this is");
        assertEquals(List.of(new StringPredicates.Reading.Accepting(beginningWith("JP")),
                        new StringPredicates.Reading.Accepting(beginningWith("US"))),
                read.stream().map(PredicateReadings.Reading::reading).toList(),
                "and each states what the call it stands in handed it");
    }

    /**
     * And a predicate nothing reads states nothing.
     *
     * <p>The negative control for the three above: the same rule, in a body that computes it and
     * never reads what it came to. A walk that found rules wherever they were written would come
     * back with one here, and the class it divided the position into would be one no value of the
     * model is ever on either side of.
     */
    @Test
    void aPredicateBoundToANameNothingReadsIsNoRule() {
        assertEquals(List.of(), readingsOf("""
                behavior f : (code: String) -> Answer
                let f (code) = {
                    let ignored = String.startsWith("JP", code)
                    Yes
                }
                """),
                "what a body computes and never reads states nothing about what it answers");
    }

    /** The rules {@code behavior}'s body states, read off the representation the analysis holds. */
    private static List<PredicateReadings.Reading> readingsOf(String behavior) {
        String source = """
                module demo

                data Yes
                data No
                data Answer = Yes | No

                """ + behavior;
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code() + " " + each.diagnostic().primary())
                        .toList(),
                "the model under test compiles");
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        AnalysisBody body = checked.analysisBodies().get("f");
        assertNotNull(body, "the behavior has a body for the analysis to read");
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs = compilation.db().ask(new Adequacy.Inputs(module)).value().get("f");
        return PredicateReadings.of("f", body,
                        compilation.db().ask(new Bodies.StatedContracts(module)).value().get("f"),
                        inputs.reading(rules), inputs.parameterReads(),
                        checked.elementBindings().get("f"),
                        new RuleReachNumbering(module, "f"))
                .predicates();
    }
}
