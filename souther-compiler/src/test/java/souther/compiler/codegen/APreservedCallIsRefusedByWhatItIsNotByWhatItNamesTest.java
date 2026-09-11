package souther.compiler.codegen;

import souther.compiler.KeptCalls;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A representation keeps a call standing for the analysis that has rules about the operation. The
 * tree the backend emits from keeps none, so one reaching the emitter means it was handed another
 * tree — and that is decided by what the node is.
 *
 * <p>Not by what the call names. An emitter that refused only the operations it had no bytecode for
 * would emit the ones it happened to know, which is the same table lookup that let the two
 * representations be confused in the first place: {@code List.sortBy} has an emitter and
 * {@code List.map} does not, and neither of them belongs in a tree that runs.
 */
class APreservedCallIsRefusedByWhatItIsNotByWhatItNamesTest {

    private static final SourcePos POS = new SourcePos(3, 7);

    @Test
    void anOperationTheEmitterHasNoBytecodeForIsRefused() {
        assertRefused(ValueName.Stdlib.operation("List", "map"));
    }

    @Test
    void anOperationTheEmitterDoesHaveBytecodeForIsRefusedTheSameWay() {
        // `List.sortBy` is written out by name in the emitter's own call dispatch, so this is the
        // case an operation-keyed refusal would have let through
        assertRefused(ValueName.Stdlib.operation("List", "sortBy"));
    }

    @Test
    void soIsOneThatNamesAModulesOwnHelper() {
        ValueName helper = new ValueName.Helper("demo", "half");
        // A value the representation kept standing rather than a call: it declares no parameters,
        // and reaching the emitter is the same thing having gone wrong.
        assertRefused(helper, new Core.PreservedCall(KeptCalls.settledValue(helper, Type.INT),
                List.of(), new Core.KeptCallPlace(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture()), Type.INT, POS));
    }

    private static void assertRefused(ValueName.Stdlib.Operation operation) {
        assertRefused(operation, KeptCalls.to(operation, POS));
    }

    private static void assertRefused(ValueName operation, Core.PreservedCall call) {
        IllegalStateException e = call.unexpectedIn("the emitter");

        assertEquals(IllegalStateException.class, e.getClass());
        assertTrue(e.getMessage().contains(String.valueOf(operation)), e.getMessage());
        assertTrue(e.getMessage().contains(String.valueOf(POS)),
                "and where it was: " + e.getMessage());
    }
}
