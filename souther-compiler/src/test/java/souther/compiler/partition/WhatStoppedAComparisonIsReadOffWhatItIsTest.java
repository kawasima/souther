package souther.compiler.partition;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a comparison that drew no line is reported as, read off what the comparison is.
 *
 * <p>A table, because the answers are only reviewable together. Each row is one shape of rule, and
 * what tells the rows apart is what the author wrote — not which of this compiler's readers happened
 * to answer first. Written the other way round, the word a report printed came from the arithmetic
 * declining to answer, so two rules of one kind were described as two kinds and two kinds as one.
 *
 * <p>The rows that draw a line are here as well as the rows that do not. A rule the arithmetic reads
 * still says what it could not divide, and pinning only the rules that stop would let one of these
 * quietly become the other.
 */
class WhatStoppedAComparisonIsReadOffWhatItIsTest {

    /**
     * The language's own arithmetic over two positions. It divides neither of them, which is what
     * it says and not something missing here.
     */
    @Test
    void arithmeticOverTwoPositionsRelatesThem() {
        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_PARTITION_SHAPE),
                whyAt(guard("a: Int, b: Int", "Int.add(a, b) > 10"), "a"));
        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_PARTITION_SHAPE),
                whyAt(guard("a: Int, b: Int", "Int.subtract(b, a) > 10"), "b"));
    }

    /**
     * The same arithmetic outside the fragment it is read in. The form was seen and not taken
     * apart, which is a wider reading of forms and not a statement about any operation: the language
     * has an operator for a product, so an author is not being asked to invert anything.
     */
    @Test
    void arithmeticOutsideTheFragmentIsAFormNobodyReads() {
        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_SYNTAX),
                whyAt(guard("a: Int", "Int.multiply(a, a) > 10"), "a"));
    }

    /**
     * An operation answered the value the rule is about. Where the values came from is known and
     * what the rule says about them here is not, which asks for a statement about the operation.
     *
     * <p>Told that its syntax was not read, an author goes looking for a spelling this compiler
     * handles perfectly well.
     */
    @Test
    void anOperationsAnswerIsARuleAboutAValueMadeFromThePosition() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(guard("a: DateTime, b: DateTime", "DateTime.minutesBetween(a, b) > 10"), "a"));
        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(guard("a: DateTime, b: DateTime", "DateTime.minutesBetween(a, b) > 10"), "b"));
    }

    /**
     * An operation the library writes in this language is one of those all the same.
     *
     * <p>{@code Int.abs} has a body, and that body is not what the reading of rules looks at. The
     * tree such a reading is made over keeps the operation standing, so what the author wrote —
     * a comparison against what an operation answered — is what is there to be read, and the word
     * is the same word every other operation gets.
     *
     * <p><b>Which is a decision about who owns a partition's contributions, taken here.</b> The
     * comparisons inside {@code Int.abs} are that operation's implementation, and a caller does not
     * owe rows for them: a line at nought came out of the body forking there, and the model that
     * called it never said anything about nought. So the caller's rule is read for what the caller
     * wrote, and what {@code abs} does to the values is a fact about the operation — one this
     * compiler may learn to state, and until it does, this is what the reading comes to.
     */
    @Test
    void anOperationWrittenInThisLanguageIsAnOperationLikeAnyOther() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(guard("a: Int", "Int.abs(a) > 10"), "a"));
    }

    /**
     * Which argument an operation was given is part of what the rule is, so both positions are
     * named however the author ordered them.
     */
    @Test
    void bothArgumentsOfAnOperationAreNamed() {
        PartitionEvidence measured =
                guard("a: DateTime, b: DateTime", "DateTime.minutesBetween(b, a) > 10");

        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(measured, "a"));
        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(measured, "b"));
    }

    /**
     * An operation this reads perfectly well, beside a form it does not. What is missing is the
     * form, and the operation is not what an author would change.
     *
     * <p>Read off the sides, the answer was about the length: whichever side held an operation was
     * the one blamed, so an operation became more likely to be named for its neighbour the more of
     * them this learned to read.
     *
     * <p>Filed at the length, which is the number the reading was after when it stopped. The rule
     * bounds the length and says nothing about which strings may stand there, so the string's own
     * values are not what went unread.
     */
    @Test
    void anOperationThisReadsIsNotBlamedForTheFormBesideIt() {
        PartitionEvidence measured = guard("s: String",
                "String.length(s) > Int.multiply(String.length(s), String.length(s))");

        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_SYNTAX),
                whyAt(measured, "String.length(s)"));
        assertEquals(List.of(), whyAt(measured, "s"),
                "the string's own values are not what the rule is about");
    }

    /**
     * And the operation is named where it is what stopped the reading, over the same position and
     * the same shape of comparison. The pair is the whole of the argument: one word for both would
     * be a rule about which side an operation was written on.
     */
    @Test
    void anOperationThisDoesNotReadIsNamed() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                whyAt(guard("a: DateTime", "DateTime.minutesBetween(a, a) > 10"), "a"));
    }

    /**
     * A rule read from end to end whose quantity the position does not appear in. Nothing fell
     * short here: there was no line in it to draw.
     *
     * <p>Told apart from a reading that stopped, which used to be the same absence. Answered as a
     * form nobody could read, it sent an author to rewrite a spelling this compiler had read
     * completely.
     */
    @Test
    void aRuleReadToTheEndThatCutsNothingSaysThat() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_CUTS_NOTHING),
                whyAt(guard("a: Int", "Int.subtract(a, a) > 0"), "a"));
    }

    /**
     * What the rule cuts is what it is about, and how it was spelled does not overrule that.
     *
     * <p>{@code a - a > b - b} names a position on each side and cuts nothing at all. Asked of the
     * sides first, one position on each was enough to call it a relation between two of them — a
     * class about two positions this compiler is waiting to be able to state, for a rule that
     * states nothing about either. The single-position cancellation beside it does not catch this:
     * there is only one position there for the sides to find.
     */
    @Test
    void whatARuleCutsOverrulesWhatItsSidesMention() {
        assertEquals(List.of(UndividedPosition.Reason.RULE_CUTS_NOTHING),
                whyAt(guard("a: Int, b: Int",
                        "Int.subtract(a, a) > Int.subtract(b, b)"), "a"));
    }

    /**
     * A helper is a binding round the expression it became, and what the expression is made of is
     * its body. An argument the body never reads is no part of the value.
     *
     * <p>Read as both, {@code ignored(a, b)} was an expression about {@code a}: the rule came back
     * as one relating two positions, and a reader was sent to a position the value does not depend
     * on. What reaches an argument is the body reading its name, which is the same way the
     * arithmetic beside this reaches one.
     */
    @Test
    void anArgumentAHelperDoesNotReadIsNoPartOfWhatItAnswers() {
        String model = """
                module demo

                data Ok
                data No

                let second (x: Int, y: Int): Int = Int.multiply(y, y)

                behavior f : (a: Int, b: Int) -> Ok | No
                let f (a, b) = {
                    guard second(a, b) > 10 else No
                    Ok
                }
                """;

        assertEquals(List.of(), whyAt(measured(model), "a"));
        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_SYNTAX),
                whyAt(measured(model), "b"));
    }

    /** And a name over an expression answers what the expression answers. */
    @Test
    void namingAnExpressionDoesNotChangeWhatItIsMadeOf() {
        String named = """
                module demo

                data Ok
                data No

                let squared (x: Int): Int = Int.multiply(x, x)

                behavior f : (a: Int) -> Ok | No
                let f (a) = {
                    guard squared(a) > 10 else No
                    Ok
                }
                """;

        assertEquals(whyAt(guard("a: Int", "Int.multiply(a, a) > 10"), "a"),
                whyAt(measured(named), "a"));
    }

    /**
     * An operation with arithmetic written round it is still the operation that stopped the
     * reading, and a clause of the same shape says the same thing.
     *
     * <p>The two readers name different things: a body names a position of an input, and a clause
     * names a coordinate of the value it is written about, of which an operation's answer is not
     * one. Read as an atom and found unprojectable afterwards, the clause reader had thrown the
     * operation away and answered from the shape of the whole side — so one token of arithmetic
     * outside the call was the difference between two sentences for one rule.
     */
    @Test
    void arithmeticRoundAnOperationDoesNotChangeWhatStoppedTheReading() {
        for (String condition : List.of("DateTime.minutesBetween(a, b) <= 30",
                "Int.add(DateTime.minutesBetween(a, b), 1) <= 30",
                "Int.add(1, DateTime.minutesBetween(a, b)) <= 30")) {
            assertEquals(List.of(UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE),
                    whyAt(guard("a: DateTime, b: DateTime", condition), "a"), condition);
        }
    }

    private static PartitionEvidence guard(String parameters, String condition) {
        String model = """
                module demo

                data Ok
                data No

                behavior f : (%s) -> Ok | No
                let f (%s) = {
                    guard %s else No
                    Ok
                }
                """.formatted(parameters,
                parameters.replaceAll(":\\s*[A-Za-z<>]+", ""), condition);
        return measured(model);
    }

    private static PartitionEvidence measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, PartitionEvidence> coverage =
                compilation.db().ask(new Adequacy.Coverage("demo")).value();
        assertNotNull(coverage, () -> "the model under test compiles: " + model);
        PartitionEvidence measured = coverage.get("f");
        assertNotNull(measured, () -> "f was measured: " + model);
        return measured;
    }

    /** What a report is told stopped the reading at {@code position}. */
    private static List<UndividedPosition.Reason> whyAt(PartitionEvidence measured,
                                                        String position) {
        return measured.notRead().stream()
                .filter(each -> each.at().equals(position))
                .map(PartitionEvidence.NotRead::reason)
                .toList();
    }
}
