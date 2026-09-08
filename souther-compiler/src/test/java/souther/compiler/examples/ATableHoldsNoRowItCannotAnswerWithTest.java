package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A fake's table is a listing and a rule, and they have to be the same thing: what
 * {@link ExampleStatements.Standins#explicit} holds is what
 * {@link ExampleStatements.Standins#answering} can return.
 *
 * <p>Held at construction rather than checked afterwards. The compile that builds a table already
 * keeps the rows it cannot dispatch to apart from the ones it can, and a reader enumerating the
 * listing — which is what running a bound implementation against a table's entries will do — is
 * entitled to the agreement whatever else changes above it. A table that held a row it can never
 * return would put that reader back where this started: reporting about a value the fake would never
 * have answered with.
 */
class ATableHoldsNoRowItCannotAnswerWithTest {

    private static final SourcePos SOMEWHERE = new SourcePos(1, 1);

    private static ExampleStatements.Standin stating(String argument) {
        return new ExampleStatements.Standin(new Object[] {argument},
                new Hir.FakeRow(new Hir.Matched.Arguments(List.of()), null, SOMEWHERE), null);
    }

    @Test
    void aTableRefusesARowAnEarlierRowAlreadyStates() {
        ExampleStatements.Standin first = stating("m-1");
        ExampleStatements.Standin second = stating("m-1");

        assertThrows(IllegalArgumentException.class,
                () -> new ExampleStatements.Standins(List.of(first, second), null),
                "the second is a row the dispatch can never return, so the table does not hold it");
    }

    @Test
    void aTableOfRowsStatingDifferentThingsIsWhatItSays() {
        ExampleStatements.Standin one = stating("m-1");
        ExampleStatements.Standin nine = stating("m-9");

        ExampleStatements.Standins table = assertDoesNotThrow(
                () -> new ExampleStatements.Standins(List.of(one, nine), null));

        assertSame(one, table.answering(new Object[] {"m-1"}), "each row answers what it states");
        assertSame(nine, table.answering(new Object[] {"m-9"}));
    }
}
