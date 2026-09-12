package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a rule is answerable for is the same after an edit that moves the text and says nothing.
 *
 * <p>The cut this is about. What a reading decided crosses out of the declaration that wrote it and
 * is read by every module that imports it, so an answer that differed whenever a declaration above
 * it moved would have all of them worked out again for an edit nobody can see — and a reader that
 * kept its answer would keep the place the code used to be at.
 *
 * <p><b>Asked of the whole value and not of one component of it.</b> What a shortfall is told apart
 * by is the construct its author wrote and the copy of it this is, and both of those are built out
 * of several values apiece; a test that read a field of one would be answering about the field.
 * Compared whole, whatever any of them comes to is compared, and a value added to one of them is
 * compared by having been added.
 *
 * <p>Two edits, because the copies are named by two things. A construct inside a helper is named by
 * what the helper's author wrote; which copy of it this is, is named by the call that made the copy.
 * Moving one text leaves the other where it was, so an identity that held a place in either would
 * be caught by exactly one of these.
 *
 * <p>And compared in the order they were met rather than as sets, so that an edit which kept both
 * copies and exchanged them is a difference. Read as sets, a lineage that named the copies by where
 * they were reached would pass while telling a reader about the other call.
 */
class WhatARuleIsAnswerableForIsWhatItWasBeforeTheTextMovedTest {

    private static final String UNREAD_X = souther.compiler.ARuleNoReadingTakesIn.about("x");

    /**
     * One choice written in a helper, expanded at two calls.
     *
     * <p>Both halves on purpose. The alternative nothing reads leaves the position open, so there
     * is something for a rule to be answerable for; the two calls make two copies of it, so what is
     * compared is a pair and not a single value.
     *
     * <p>And both calls are about the one field, so the copies agree about everything a shortfall
     * says except which copy they are. Written about two fields, the pair would be told apart by
     * the position and this would pass with the copy dropped.
     */
    private static String model(String beforeTheHelper, String beforeTheCall) {
        return """
                module demo
                %1$s
                let alt (x: String): Bool = x /= "a" || UNREAD_X
                %2$s
                data N = { s: String }
                    invariant r = alt(s) && alt(s)
                """.formatted(beforeTheHelper, beforeTheCall).replace("UNREAD_X", UNREAD_X);
    }

    private static final String AS_WRITTEN = model("", "");

    /** The same model with the helper pushed down the file. */
    private static final String THE_HELPER_MOVED = model("""
            // a comment nobody reads
            // and another""", "");

    /** And the same with the declaration that calls it pushed down instead. */
    private static final String THE_CALL_MOVED = model("", """
            // a comment nobody reads
            // and another""");

    /**
     * The same again where what the reading gives up on is a call rather than an operator.
     *
     * <p>Held beside the one above because the two are carried by different nodes. A comparison
     * keeps what its author wrote on the node itself; a call kept standing for the analysis to read
     * keeps it on the place beside it, and the copy it stands in was added there for this. Measured
     * on a comparison alone, a call that had lost either half would pass.
     */
    private static String aCallNothingCanBuild(String beforeTheHelper, String beforeTheCall) {
        return """
                module demo
                %1$s
                let costly (x: String): Bool = String.matches("a{60000}", x)
                %2$s
                data N = { s: String }
                    invariant r = costly(s) && costly(s)
                """.formatted(beforeTheHelper, beforeTheCall);
    }

    private static final String A_CALL_AS_WRITTEN = aCallNothingCanBuild("", "");

    private static final String A_CALL_WITH_THE_HELPER_MOVED = aCallNothingCanBuild("""
            // a comment nobody reads
            // and another""", "");

    private static final String A_CALL_WITH_THE_CALLER_MOVED = aCallNothingCanBuild("", """
            // a comment nobody reads
            // and another""");

    /** An edit above the helper leaves what its choice is answerable for where it was. */
    @Test
    void movingTheHelperChangesNothingARuleIsAnswerableFor() {
        assertFalse(answerableFor(AS_WRITTEN).isEmpty(),
                "the model has to leave something for a rule to be answerable for, or this"
                        + " compares two empty lists");
        assertEquals(answerableFor(AS_WRITTEN), answerableFor(THE_HELPER_MOVED),
                "the helper states what it stated, and where it is written is not part of that");
    }

    /** And so does an edit above the clause that calls it. */
    @Test
    void andSoDoesMovingTheClauseThatCallsIt() {
        assertEquals(answerableFor(AS_WRITTEN), answerableFor(THE_CALL_MOVED),
                "the clause states what it stated, and where it is written is not part of that");
    }

    /**
     * And the two copies stay two, which is what makes the comparison above worth making.
     *
     * <p>Compared without this, a shortfall that had lost the copy would pass both of them by
     * having one entry that never moves.
     */
    @Test
    void andTheTwoCopiesOfItAreStillTwo() {
        assertEquals(2, answerableFor(AS_WRITTEN).size(),
                "the helper is expanded at two calls and each copy leaves its own position open");
    }

    /** And a call kept standing says the same, whichever text moved. */
    @Test
    void movingTheTextAroundACallChangesNothingEither() {
        assertEquals(2, answerableFor(A_CALL_AS_WRITTEN).size(),
                "the helper is expanded at two calls and each copy asks for its own machine");
        assertEquals(answerableFor(A_CALL_AS_WRITTEN),
                answerableFor(A_CALL_WITH_THE_HELPER_MOVED),
                "the helper states what it stated, and where it is written is not part of that");
        assertEquals(answerableFor(A_CALL_AS_WRITTEN),
                answerableFor(A_CALL_WITH_THE_CALLER_MOVED),
                "and so does the declaration that calls it");
    }

    /** Everything a rule of {@code N} is answerable for, in the order it was met. */
    private static List<RuleShortfall> answerableFor(String source) {
        List<RuleShortfall> out = new ArrayList<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((_, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted it
                            && it.why() instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.addAll(says.shortfalls());
                    }
                }));
        return out;
    }

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
