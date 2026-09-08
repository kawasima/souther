package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.check.InvariantChecker.Judgment;
import souther.compiler.check.InvariantChecker.Said;
import souther.compiler.check.InvariantChecker.Verdict;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Messages;
import souther.compiler.diag.msg.InvariantMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2010 says a value fails a clause, so what it names is the clauses the value fails.
 *
 * <p>An invariant is a conjunction, and a value that fails one of its clauses may leave others
 * standing that nothing where it is built decides. Those are clauses the guards did not establish,
 * which is what E2011 reports; they are not clauses the value fails. One set was answering both
 * questions, and the sentence that reads it says "the value being built is one that clause rejects"
 * — untrue of every clause in it that was merely left standing.
 *
 * <p>The refinement is of the classification and not of the analysis. What was one answer, unsettled,
 * is now two — unknown and refuted — and the two together are still exactly what unsettled was, so
 * nothing E2011 reports moves.
 */
class AValueFailsTheClausesItFailsAndNotTheOnesLeftStandingTest {

    /**
     * One clause the values refute and one nothing here decides, both named.
     *
     * <p>{@code low} is written out, so {@code lowNonNegative} fails on the values alone.
     * {@code high} is an input, so {@code ordered} is a clause the check can neither establish nor
     * refute.
     */
    private static final String REFUTED_AND_UNKNOWN = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant lowNonNegative = low >= 0
                invariant ordered = low <= high

            behavior f : (high: Int) -> Bound
                constructs Bound

            let f (high) = Bound { low = -1, high = high }
            """;

    /** The same, with the refuted clause the unnamed one. */
    private static final String REFUTED_UNNAMED_UNKNOWN_NAMED = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low >= 0
                invariant ordered = low <= high

            behavior f : (high: Int) -> Bound
                constructs Bound

            let f (high) = Bound { low = -1, high = high }
            """;

    /** Nothing left standing beside what the value fails: one clause, and the value fails it. */
    private static final String REFUTED_ONLY = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant lowNonNegative = low >= 0

            behavior f : (high: Int) -> Bound
                constructs Bound

            let f (high) = Bound { low = -1, high = high }
            """;

    // --- the three are a partition of what was read ---------------------------------------------

    @Test
    void everyClauseThatWasReadIsOnExactlyOneSide() {
        for (String source : List.of(REFUTED_AND_UNKNOWN, REFUTED_UNNAMED_UNKNOWN_NAMED,
                REFUTED_ONLY)) {
            Judgment judgment = judgmentOn(source);
            int sides = judgment.settled().size() + judgment.refuted().size()
                    + unknown(judgment).size();

            assertEquals(judgment.found().size(), sides,
                    "one clause, one answer about it: " + judgment.found());
        }
    }

    /** And what was one answer is still exactly the two it was refined into. */
    @Test
    void unsettledIsUnknownAndRefutedTogether() {
        for (String source : List.of(REFUTED_AND_UNKNOWN, REFUTED_UNNAMED_UNKNOWN_NAMED,
                REFUTED_ONLY)) {
            Judgment judgment = judgmentOn(source);
            List<Clause.Id> both = new ArrayList<>(unknown(judgment).keySet());
            both.addAll(judgment.refuted().keySet());

            assertEquals(judgment.unsettled().keySet(), java.util.Set.copyOf(both),
                    "the guards established neither kind: " + judgment.found());
            assertTrue(Collections.disjoint(unknown(judgment).keySet(),
                    judgment.refuted().keySet()), "and no clause is both");
        }
    }

    // --- what E2010 names -----------------------------------------------------------------------

    @Test
    void aClauseTheValueDoesNotFailIsNotNamedAsOneItFails() {
        InvariantMessage.TheValueIsOneTheInvariantRejects said = assertInstanceOf(
                InvariantMessage.TheValueIsOneTheInvariantRejects.class,
                errorOn(REFUTED_AND_UNKNOWN).diagnostic().said());

        assertEquals("lowNonNegative", said.unsettled(),
                "`ordered` is a clause the check could not settle, not one the value fails");
    }

    /**
     * And where the clause the value fails has no name, the error says so — rather than reaching for
     * the name of a clause that merely stands, which is what asking the unsettled clauses for a name
     * did.
     */
    @Test
    void anUnnamedRefutedClauseIsNotAnsweredWithTheNameOfAStandingOne() {
        assertInstanceOf(InvariantMessage.TheValueIsOneTheInvariantRejectsUnnamed.class,
                errorOn(REFUTED_UNNAMED_UNKNOWN_NAMED).diagnostic().said());
        assertFalse(rendered(REFUTED_UNNAMED_UNKNOWN_NAMED).contains("ordered"),
                "the clause left standing is not what this error is about: "
                        + rendered(REFUTED_UNNAMED_UNKNOWN_NAMED));
    }

    /** Where the value fails every clause there is, nothing about what is said changes. */
    @Test
    void aValueThatFailsTheOnlyClauseIsToldTheSameAsBefore() {
        InvariantMessage.TheValueIsOneTheInvariantRejects said = assertInstanceOf(
                InvariantMessage.TheValueIsOneTheInvariantRejects.class,
                errorOn(REFUTED_ONLY).diagnostic().said());

        assertEquals("lowNonNegative", said.unsettled());
    }

    // --- and when it is raised at all -----------------------------------------------------------

    /**
     * One rung above what this fixes. The sentence is untrue of a clause that merely stands, and it
     * would be untrue of the whole diagnostic if E2010 could be raised where nothing was refused at
     * all — including in the spelling that names nothing, which would then say a value is rejected
     * by an invariant that rejects it nowhere.
     *
     * <p>What is held is that a path was read on which the value fails a clause. Not that some one
     * clause is failed on all of them: the branches above a construction may fail different clauses,
     * and then every path violates the invariant while none of the clauses is one the value fails —
     * which is what {@code REFUTED_SOMEWHERE} is, and what
     * {@link AnErrorSaysWhatEveryPathFailsAndNotWhatOneOfThemDoesTest} is about.
     */
    @Test
    void nothingIsReportedAsRejectedWhereNoClauseIsRefusedOnAnyPath() {
        for (String source : List.of(REFUTED_AND_UNKNOWN, REFUTED_UNNAMED_UNKNOWN_NAMED,
                REFUTED_ONLY)) {
            Judgment judgment = judgmentOn(source);
            if (judgment.verdict() == Verdict.REFUTED_ALONE
                    || judgment.verdict() == Verdict.REFUTED_NOT_ALONE) {
                assertTrue(judgment.found().values().stream()
                                .anyMatch(one -> one.status().refusedSomewhere()),
                        "E2010 is raised on this verdict, so a clause the value fails somewhere is"
                                + " what it is about: " + judgment.found());
            }
        }
    }

    // --- a clause is not established and refused at once -----------------------------------------


    // --- reading the check ----------------------------------------------------------------------

    private static java.util.SequencedMap<Clause.Id, Clause.Ref> unknown(Judgment judgment) {
        java.util.SequencedMap<Clause.Id, Clause.Ref> side = new java.util.LinkedHashMap<>();
        judgment.found().forEach((id, one) -> {
            if (one.status() == ClauseStatus.UNKNOWN) {
                side.put(id, one.clause());
            }
        });
        return side;
    }

    private static Judgment judgmentOn(String source) {
        List<Said> said = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.WATCHING = said;
        try {
            assertThrows(CompileException.class, () -> Compiler.compileWithWarnings(source));
        } finally {
            InvariantChecker.WATCHING = null;
        }
        assertFalse(said.isEmpty(), "nothing was checked, so nothing here is being held to anything");
        return said.get(said.size() - 1).judgment();
    }

    private static CompileException errorOn(String source) {
        CompileException thrown = assertThrows(CompileException.class,
                () -> Compiler.compileWithWarnings(source));
        assertEquals("E2010", thrown.diagnostic().code(), thrown.getMessage());
        return thrown;
    }

    private static String rendered(String source) {
        return Messages.render(errorOn(source).diagnostic().said(), Locale.ENGLISH);
    }
}
