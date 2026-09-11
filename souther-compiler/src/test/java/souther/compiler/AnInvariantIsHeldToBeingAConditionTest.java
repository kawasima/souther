package souther.compiler;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Primary;
import souther.compiler.diag.msg.DeclarationMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A clause states a condition, and what says so is the elaboration of it.
 *
 * <p>Written because that reading moved. Typing a clause and running it were two elaborations, and
 * the check's was the one that dropped what it made (issue #1080); now there is one, and it is the
 * one the emitter reads. A clause that is not a condition has to be refused by it — a reading that
 * only produced a value would let a module through and hand a backend a boolean check with a number
 * in it.
 */
class AnInvariantIsHeldToBeingAConditionTest {

    @Test
    void aClauseThatIsNotACondition() {
        String source = """
                module demo
                data Amount = Int
                    invariant value + 1
                """;
        CompileException refused = assertThrows(CompileException.class, () -> Compiler.compile(source));

        assertAll(
                () -> assertInstanceOf(DeclarationMessage.AnInvariantExpressionIsBool.class,
                        refused.diagnostics().get(0).said(),
                        "what the clause came to is what says it is not a condition"),
                () -> assertEquals(3, WhereItSits.in(source,
                                ((Primary.InSource) refused.diagnostics().get(0).primary())
                                        .place().region()).start().line(),
                        "said at the clause, and not where a value is built"));
    }
}
