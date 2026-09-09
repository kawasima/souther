package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which reading stopped at a position decides what the position is short of, and a stop of one is
 * not an answer about the other.
 *
 * <p>Two readings answer about one position and they are asked different things. What the classes
 * are made of is which values may stand there; where the border falls is where they stop. So a rule
 * the first read from end to end and the second could not follow leaves the classes exactly as the
 * rules leave them — and a verdict that read "a reading stopped here" off a list holding both said
 * this compiler could not work out how the position divides, about a position every rule of which
 * it had divided.
 *
 * <p><b>And the border is not short of it either, which is a third answer and not the first.</b>
 * Whether the rule states a line at all is settled by the reading that has the arithmetic of a
 * comparison ({@code check.StatedLines}): a call restricting which strings may stand somewhere
 * orders none of them, so there is no end here for anything to be waiting on. That the reading of
 * ends has no word for the rule is a fact about that reading and is not promoted to an end nobody
 * worked out.
 *
 * <p>Which is not "the values read it, so the ends are answered". The two vocabularies stay apart:
 * what closes this is that the line's existence was decided, not that another reader managed the
 * clause. The control below is a rule that does state a line, on a number this compiler cannot
 * name, and it is short of it still.
 *
 * <p><b>The pair, and not either half.</b> A test that only asked for the border would pass over a
 * partition quietly widened; one that only asked for the partition would pass over a border quietly
 * closed. What is held here is that one model moves one of them and not the other.
 *
 * <p>The model is a choice whose alternatives the reading of values takes in whole — a value
 * written out, and a pattern — where one of them says nothing about where the strings stop.
 */
class WhichReadingStoppedDecidesWhatAPositionIsShortOfTest {

    private static final String ONLY_THE_ENDS_STOPPED = """
            module demo
            data Yes
            data No
            data Answer = Yes | No
            data N = { n: Int, s: String }
                invariant r = s == "a" || String.matches("[A-Z]{2}", s)

            behavior check : (v: N) -> Answer
            let check (v) = Yes
            """;

    /**
     * The partition says what the model says, because the values reading answered for every rule.
     *
     * <p>{@code StatedWithoutALine} is a fact about the model: the rules were read and they divide
     * the position no way. {@code CannotDerive} is a fact about this compiler, and it is what the
     * position came back as while the border's stop was being counted here.
     */
    @Test
    void aStopOfTheEndsLeavesThePartitionSayingWhatTheModelSays() {
        assertEquals(List.of("v.n=Absent", "v.s=StatedWithoutALine"),
                undividedIn(ONLY_THE_ENDS_STOPPED),
                "every rule about `v.s` was taken in by the reading the classes are made of");
    }

    /**
     * And the border says the model draws none, because neither alternative states a line.
     *
     * <p>A value written out and a pattern each say which strings may stand at {@code v.s} and
     * neither orders them, so the choice between them places no end — and that the reading of ends
     * has no word for a pattern says nothing about it.
     */
    @Test
    void andTheBorderSaysTheModelDrawsNoLine() {
        assertEquals(List.of("border      not applicable (the rules of this behavior draw no line)"),
                borderIn(ONLY_THE_ENDS_STOPPED),
                "neither alternative states where the values at `v.s` stop");
    }

    /**
     * And an alternative that does state one, on a number nothing can name, is short of it still.
     *
     * <p>The control for the pair above. {@code Int.abs(n) >= 5} says the values stop somewhere and
     * this compiler cannot say where, so the end at {@code v.n} is one reading further would
     * settle. Without this, the rule above would read as "a choice this compiler cannot follow
     * draws no line", which is the sentence the whole arrangement is against.
     */
    @Test
    void andAnAlternativeThatStatesALineNothingCanPlaceIsShortOfItStill() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn(ONLY_THE_ENDS_STOPPED.replace(
                        "s == \"a\" || String.matches(\"[A-Z]{2}\", s)",
                        "n >= 2 || Int.abs(n) >= 5")),
                "the alternative states where `v.n` stops and nothing worked out where");
    }

    private static List<String> undividedIn(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of("check",
                        InputDomain.of(sigs.get("check"), rules,
                                ReadAs.THE_COMPILATION_DOES),
                        rules, ReadAs.THE_COMPILATION_DOES)
                .undivided().stream()
                .map(each -> each.at() + "=" + each.why().getClass().getSimpleName())
                .toList();
    }

    private static List<String> borderIn(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return souther.compiler.report.AdequacyReport.of(compilation)
                .human(souther.compiler.diag.SourceNameResolver.identity()).lines()
                .map(String::strip)
                .filter(each -> each.startsWith("border"))
                .toList();
    }
}
