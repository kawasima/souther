package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.Prepared;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Sig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Shapes;

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
 * rules leave them, and leaves the line underived — and a verdict that read "a reading stopped
 * here" off a list holding both said this compiler could not work out how the position divides,
 * about a position every rule of which it had divided.
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

    /** And the border is short of it, which is the reading that did stop. */
    @Test
    void andTheBorderIsShortOfIt() {
        assertEquals(List.of("border      not measured (no line was derived at any position)"),
                borderIn(ONLY_THE_ENDS_STOPPED),
                "the line at `v.s` rests on an alternative this compiler does not follow");
    }

    private static List<String> undividedIn(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(each -> each.name().equals("check")).findFirst().orElseThrow();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of(spec.name(),
                        InputDomain.of(spec, sigs.get("check"), rules,
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
