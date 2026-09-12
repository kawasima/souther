package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.KeptCalls;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.semantics.ArgumentsStand;
import souther.compiler.semantics.DefinitionCase;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case an operation of the library is defined in comes out of {@link Choice} as an arm, with
 * the argument that case answers and every relation it is reached under.
 *
 * <p>Row by row and not operation by operation. What a reader downstream needs of a case is the
 * value and the conditions together: the value alone says {@code Int.clamp(lo, hi, n)} may answer
 * {@code hi}, and only the conditions say when — so an implementation that moved every answer across
 * and dropped one relation would leave a choice whose arms are all reachable, bounded by a span over
 * values it cannot take. Counting arms would pass that; this does not.
 *
 * <p>Held against the table rather than against a copy of it written out here. What is under test is
 * the reading, not the library: a second copy of the cases in this file would be the drift the
 * reading was moved into one place to stop, and it would go stale the day {@code clamp} is defined
 * differently while proving nothing about whether anyone noticed.
 *
 * <p>Every operation the table has, so the day one is added it is read here without being named.
 */
class EveryCaseALibraryDefinitionIsWrittenInBecomesAnArmTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "call");

    @Test
    void everyRowOfTheTableIsAnArmAnsweringItsArgumentUnderAllOfItsRelations() {
        assertFalse(DischargeRules.choosingOperations().isEmpty(),
                "no operation is defined by cases at all, so this read nothing rather than reading"
                        + " that nothing was wrong");
        for (ValueName operation : DischargeRules.choosingOperations()) {
            Core.PreservedCall call = callTo(operation);
            List<DefinitionCase<DeclaredArgument>> defined = DischargeRules.chosenBy(call);
            Choice choice = Choice.of(call);

            assertNotNull(choice, operation + " is defined in cases and answers no choice");
            assertEquals(Choice.Kind.THE_ARGUMENTS, choice.kind(),
                    operation + " is decided by how its arguments stand");
            assertEquals(defined.size(), choice.arms().size(),
                    operation + " has an arm per case it is defined in");

            for (int i = 0; i < defined.size(); i++) {
                DefinitionCase<DeclaredArgument> row = defined.get(i);
                Choice.Arm arm = choice.arms().get(i);
                String where = operation + " case " + (i + 1);

                assertSame(CallArguments.of(row.answers(), call), arm.answers(),
                        where + " answers the argument the case answers, as the value itself");
                assertTrue(arm.decidedBy() instanceof Choice.Decides.ByArgumentRelations,
                        where + " is decided by how the arguments stand");
                List<Choice.ArgumentRelation> stated =
                        ((Choice.Decides.ByArgumentRelations) arm.decidedBy()).relations();
                assertEquals(expected(row, call), stated,
                        where + " is reached under every relation the case names, and under no"
                                + " other. An answer moved across without its conditions is an arm"
                                + " reachable wherever the call stands");
            }
        }
    }

    /** A call to an operation the library does not define by cases is not one of these. The
     * arithmetic answers a value that is neither of its operands, and reading it as a choice would
     * bound it by a span over them. */
    @Test
    void anOperationDefinedInNoCasesIsNoChoice() {
        assertNull(Choice.of(callTo(ValueName.Stdlib.operation("Int", "add"))),
                "what `a + b` answers is not `a` and not `b`");
    }

    /** The relations the row names, written in the values this call was given. */
    private static List<Choice.ArgumentRelation> expected(DefinitionCase<DeclaredArgument> row,
                                                          Core.PreservedCall call) {
        List<Choice.ArgumentRelation> out = new ArrayList<>(row.given().size());
        for (ArgumentsStand<DeclaredArgument> stands : row.given()) {
            out.add(new Choice.ArgumentRelation(CallArguments.of(stands.left(), call), stands.rel(),
                    CallArguments.of(stands.right(), call)));
        }
        return out;
    }

    /** A call to {@code operation} whose arguments are told apart by what stands at each position,
     * so that a rule naming the wrong one is a different answer rather than the same one. */
    private static Core.PreservedCall callTo(ValueName operation) {
        Stdlib.Entry entry = DefaultStdlib.get().entry((ValueName.Stdlib.Operation) operation);
        assertNotNull(entry, operation + " is not declared by the library");
        List<Type> params = entry.signature().params();
        List<Core> args = new ArrayList<>(params.size());
        for (int i = 0; i < params.size(); i++) {
            args.add(new Core.Read("arg" + i, new BindingId(OWNER, i), params.get(i), POS));
        }
        return new Core.PreservedCall(
                KeptCalls.declared((ValueName.Stdlib.Operation) operation), args,
                new Core.KeptCallPlace(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture(), ExpansionLineage.ORIGINAL),
                entry.signature().result(), POS);
    }
}
