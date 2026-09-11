package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.Compiler;
import souther.compiler.check.InvariantChecker.Judgment;
import souther.compiler.check.InvariantChecker.Said;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.LabeledRegion;
import souther.compiler.diag.msg.InvariantMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clause one branch fails is not a clause the value fails.
 *
 * <p>A construction under a conditional is read once per branch, and the branches may fail different
 * clauses. Every path then violates the invariant — which is what E2010 is raised on, and it is
 * right to raise it — while no clause is one every path fails, and no value that can be built here
 * fails both. Saying of either clause that "the value being built is one this clause rejects" is
 * untrue of the value that comes down the other branch.
 *
 * <p>The two quantifications are not the same one, and combining the branches by taking the stronger
 * of what they found puts them together: what a verdict is decided by is whether every path refuses
 * the invariant, and what a clause may be said to be is whether every path refuses that clause.
 */
class AnErrorSaysWhatEveryPathFailsAndNotWhatOneOfThemDoesTest {

    /** One branch fails the first clause and establishes the second; the other, the reverse. */
    private static final String EITHER_WAY = """
            module demo

            data Pair =
                { a: Int
                , b: Int
                }
                invariant aNonNegative = a >= 0
                invariant bNonNegative = b >= 0

            behavior mk : (x: Int) -> Pair
                constructs Pair

            let mk (x) = {
                let n = if x > 0 then -1 else 1
                Pair { a = n, b = 0 - n }
            }
            """;

    @Test
    void theErrorIsStillRaised() {
        assertEquals("E2010", error().code(),
                "every path violates the invariant, whichever clause it is that refuses it");
    }

    @Test
    void neitherClauseIsOneEveryPathFails() {
        Judgment judgment = judgment();

        assertEquals(0, judgment.refuted().size(),
                "no clause is failed wherever the value is built: " + judgment.found());
        assertEquals(2, judgment.refutedSomewhere().size(),
                "each is failed on a path that reaches here: " + judgment.found());
        assertEquals(2, judgment.unsettled().size(),
                "and the guards establish neither, which is unchanged");
    }

    /** So the sentence says what it can say of every path, which does not name a clause. */
    @Test
    void theMessageDoesNotNameAClauseTheValueFails() {
        assertInstanceOf(InvariantMessage.TheValueIsOneTheInvariantRejectsUnnamed.class,
                error().said(), "the clauses have names, and neither of them is what this is about");
    }

    /** And the reader is still sent to both, under what is true of them. */
    @Test
    void bothClausesArePointedAtUnderWhatIsTrueOfThem() {
        List<LabeledRegion> marked = error().secondary();

        assertEquals(List.of(7, 8),
                marked.stream().map(one -> WhereItSits.in(EITHER_WAY, ((souther.compiler.diag.DiagnosticPlace.InSource) one.place()).region()).start().line()).toList());
        assertTrue(marked.stream().allMatch(one -> one.said()
                        instanceof InvariantMessage.ThisClauseRejectsTheValueOnSomeOfThePathsHere),
                "not `ThisClauseRejectsThisValue`, which the value that comes down the other branch"
                        + " refutes: " + marked);
    }

    private static Diagnostic error() {
        CompileException thrown = assertThrows(CompileException.class,
                () -> Compiler.compileWithWarnings(EITHER_WAY));
        return thrown.diagnostic();
    }

    private static Judgment judgment() {
        List<Said> said = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.WATCHING = said;
        try {
            assertThrows(CompileException.class, () -> Compiler.compileWithWarnings(EITHER_WAY));
        } finally {
            InvariantChecker.WATCHING = null;
        }
        assertFalse(said.isEmpty(), "nothing was checked, so nothing here is being held to anything");
        return said.get(said.size() - 1).judgment();
    }
}
