package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two authorities answer "this was not read", and which of them it is decides what can be said.
 *
 * <p>A rule was read and could not be used, and the reader that read it knows which rule; or the
 * reading never arrived at the rules of a position, and there is no rule to name because none was
 * observed. Written as one shape with the rule left out where there is none, a consumer would have
 * to read an absent field to know which of the two it was holding — and a report would name a rule
 * for some entries and not the rest with nothing saying why.
 *
 * <p>Both are told under {@code PARTITION_NOT_READ}, and the reason beside each says which limit
 * it is waiting on. {@code PARTITION_RULES_NOT_REACHED} belongs to the finding about a position the
 * axes did measure, which is a different thing to act on and writes the same one field.
 */
class WhatWasNotReadIsSaidByWhoeverKnowsWhichRuleTest {

    /** A rule read and given up on: the position is inside an expression the terms do not name. */
    private static final String A_RULE = """
            module m

            data Ok
            data No

            behavior f : (n: Int) -> Ok | No
            let f (n) = if Int.multiply(n, n) < 10 then Ok else No
            """;

    /**
     * A position whose rules the reading never arrived at.
     *
     * <p>The clause states nothing that could be typed, so it is gone before any reading of the
     * value sees it and which position it governed goes with it. So there is a rule and no reader
     * that reached it, which is what leaves a position with no rule to name.
     *
     * <p>It used to be a relation stated of every element, held as a quantifier over the clause it
     * was written in. That clause is written about the sequence and is now short where it is
     * written, which is a question the model raised and nothing answered rather than a position
     * nothing read (#1072).
     */
    private static final String A_POSITION = """
            module m

            data Ok
            data Item = String
                invariant unreadable = value == 1

            data Basket = { item: Item }

            behavior f : (b: Basket) -> Ok
                constructs Ok
            let f (b) = Ok
            """;

    /** A rule finding names the rule, and is told under the word for a rule. */
    @Test
    void aRuleThisReadAndCouldNotUseNamesIt() {
        PartitionEvidence.NotRead said = notRead(A_RULE).getFirst();

        PartitionEvidence.NotRead.AnUnclassifiedRule rule =
                assertInstanceOf(PartitionEvidence.NotRead.AnUnclassifiedRule.class, said);
        assertInstanceOf(souther.compiler.check.RuleRef.Comparison.class, rule.rule());
        assertEquals(List.of(Adequacy.Kind.PARTITION_NOT_READ), kinds(A_RULE));
    }

    /**
     * And a position finding names no rule, because nothing observed one.
     *
     * <p>The clause reached no reading at all, so what stopped this is the reading and not any one
     * clause it can name, and what is said is the position alone. The shape has nowhere to put a
     * rule, which is what stops one being invented.
     */
    @Test
    void aPositionTheReadingDidNotReachNamesNoRule() {
        List<PartitionEvidence.NotRead> said = notRead(A_POSITION);

        assertTrue(said.stream().anyMatch(each ->
                        each instanceof PartitionEvidence.NotRead.APosition
                                && each.reason() == UndividedPosition.Reason.RULES_NOT_READ_AT_ALL),
                said::toString);
        assertFalse(said.stream().anyMatch(each ->
                        each instanceof PartitionEvidence.NotRead.ARule
                                || each instanceof PartitionEvidence.NotRead.AnUnclassifiedRule),
                said::toString);
    }

    /**
     * A report names the rule where there is one and the position where there is not, and the two
     * sentences are written apart.
     *
     * <p>One sentence over both would have to say "a rule about it" of an entry with no rule, which
     * is the sentence this is against — an author told that a rule went unread with nothing saying
     * which.
     */
    @Test
    void theTwoAreWrittenApart() {
        assertTrue(human(A_RULE).contains("not read: comparison@"), human(A_RULE));
        assertFalse(human(A_RULE).contains("not read: n "), human(A_RULE));
        assertTrue(human(A_POSITION).contains("not read: b.item ("), human(A_POSITION));
    }

    private static List<Adequacy.Kind> kinds(String model) {
        return findings(model).stream()
                .filter(each -> each.kind() == Adequacy.Kind.PARTITION_NOT_READ
                        || each.kind() == Adequacy.Kind.PARTITION_RULES_NOT_REACHED)
                .map(Adequacy.Finding::kind).toList();
    }

    private static List<Adequacy.Finding> findings(String model) {
        return report(model).modules().get(0).behaviors().get(0).findings();
    }

    private static List<PartitionEvidence.NotRead> notRead(String model) {
        return report(model).modules().get(0).behaviors().get(0).partition().notRead();
    }

    private static String human(String model) {
        return report(model).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    private static AdequacyReport report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
    /**
     * And the model that leaves a rule unread at a position it measured is one this compiler
     * refuses.
     *
     * <p>Said out loud, because it is what the word means now and not an accident of the fixture.
     * A rule written under a container, a case or an optional is read where it governs, one position
     * down (#1072); a rule this compiler cannot find a value for is a shortfall of its own. What is
     * left is a clause the front end could not type — and a model carrying one is refused, so a
     * document that says a measured position went short of a rule is a document about a model that
     * did not compile.
     *
     * <p>A tripwire and not a preference. The day a clause can go unread in a model that compiles,
     * this fails and whoever made it so is the one who should decide what the word means then.
     */
    @Test
    void theModelThatSaysSoIsOneThisCompilerRefuses() {
        assertTrue(isRefused(A_POSITION), A_POSITION);
        assertFalse(isRefused(A_RULE), "and a rule read and given up on is not that");
    }

    /** Whether this compiler refuses {@code source}, which is what the fixture above turns on. */
    private static boolean isRefused(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return !compilation.diagnostics().values().stream()
                .flatMap(java.util.List::stream).toList().isEmpty();
    }

}
