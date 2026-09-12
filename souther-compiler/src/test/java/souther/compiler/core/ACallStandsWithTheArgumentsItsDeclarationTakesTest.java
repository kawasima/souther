package souther.compiler.core;

import org.junit.jupiter.api.Test;

import souther.compiler.KeptCalls;
import souther.compiler.check.CallArguments;
import souther.compiler.check.DeclaredArgument;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A kept call has the arguments the declaration it names takes.
 *
 * <p>What the readers below it stand on. A rule about an operation says which argument it is about
 * — by a written position, or by the part that argument plays — and the reader that finds it does
 * so without checking that the call has one there. That reading is right because of this and not
 * because of anything the reader does.
 *
 * <p>Said of the node rather than of the sites that build one. Three passes rebuild a kept call to
 * put something else in one of its arguments, and each of them carries the operation across
 * unchanged; a rebuild that dropped an argument or added one would be a call standing for a
 * declaration it does not fit, and the pass would have no reason to notice.
 */
class ACallStandsWithTheArgumentsItsDeclarationTakesTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** These calls are this test's own: no source wrote the name or the application. */
    private static final ReferenceOrigin COMPOSED_NAME = new FixtureReferenceOrigin(0);

    private static final ApplicationOrigin COMPOSED = new ApplicationOrigin.ComposedFixture();

    /** Where such a call stands, which this fixture composes: no source wrote the name or the
     *  application. */
    private static final Core.KeptCallPlace COMPOSED_PLACE =
            new Core.KeptCallPlace(COMPOSED_NAME, COMPOSED, ExpansionLineage.ORIGINAL);

    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "call");

    private static final ValueName.Stdlib.Operation LENGTH =
            ValueName.Stdlib.operation("List", "length");

    @Test
    void aCallOfWhatItsDeclarationTakesStands() {
        assertEquals(1, KeptCalls.to(LENGTH, POS).args().size(),
                "`List.length` takes one argument, and a call of it stands with one");
    }

    @Test
    void aCallOfFewerIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new Core.PreservedCall(KeptCalls.declared(LENGTH), List.of(),
                        COMPOSED_PLACE, Type.INT, POS));

        assertTrue(e.getMessage().contains("List.length"), e.getMessage());
    }

    @Test
    void andSoIsACallOfMore() {
        List<Core> two = new ArrayList<>(KeptCalls.to(LENGTH, POS).args());
        two.add(new Core.Int(0, Type.INT, POS));

        assertThrows(IllegalStateException.class,
                () -> new Core.PreservedCall(KeptCalls.declared(LENGTH), two,
                        COMPOSED_PLACE, Type.INT, POS));
    }

    /**
     * And it goes on standing with them.
     *
     * <p>The statement is about the node and not about the moment it was built. A pass that kept
     * the list it handed over could otherwise leave behind a call whose own statement about itself
     * had stopped being true, with every reader below it reading the call afterwards.
     */
    @Test
    void andGoesOnStandingWithThemAfterTheCallerHasMovedOn() {
        List<Core> handed = new ArrayList<>(KeptCalls.to(LENGTH, POS).args());
        Core.PreservedCall call = new Core.PreservedCall(KeptCalls.declared(LENGTH), handed,
                COMPOSED_PLACE, Type.INT, POS);

        handed.add(new Core.Int(0, Type.INT, POS));

        assertEquals(1, call.args().size(), "the call took the arguments over");
        assertEquals(call.declared().arity(), call.args().size());
    }

    /**
     * A value kept standing is a call of no arguments, and is held to that.
     *
     * <p>Being written with no parameters is what makes it a value, so the same statement covers it
     * — a reference to one is an application of what its declaration takes, which is nothing.
     */
    @Test
    void aValueKeptStandingTakesNone() {
        ValueName value = new ValueName.Helper("demo", "half");

        assertEquals(0, new Core.PreservedCall(KeptCalls.settledValue(value, Type.INT), List.of(),
                COMPOSED_PLACE, Type.INT, POS).args().size());
        assertThrows(IllegalStateException.class,
                () -> new Core.PreservedCall(KeptCalls.settledValue(value, Type.INT),
                        List.of(new Core.Int(0, Type.INT, POS)), COMPOSED_PLACE,
                        Type.INT, POS));
    }

    /**
     * And a pass that replaces an argument leaves a call of the same declaration standing.
     *
     * <p>The rebuild carries the operation rather than looking it up again, so what the rebuilt call
     * is held to is what the original was — and the rebuild goes through the same constructor, so a
     * pass that put back a different number of arguments would be refused where it wrote them.
     *
     * <p>The argument replaced is the one the bound fact about the operation names, which is the
     * only kind of argument a pass holds: {@code List.reverse} is built from its one argument.
     */
    @Test
    void replacingAnArgumentLeavesACallOfTheSameDeclaration() {
        ValueName.Stdlib.Operation reverse = ValueName.Stdlib.operation("List", "reverse");
        Core.PreservedCall call = KeptCalls.to(reverse, POS);
        Core other = new Core.Read("other", new BindingId(OWNER, 1),
                new Type.ListOf(Type.INT), POS);
        DeclaredArgument from = DefaultBoundOperationFacts.get().buildsItsResultFrom(reverse).from();
        Core.PreservedCall rebuilt = CallArguments.replacedIn(from, call, other);

        assertEquals(call.declared(), rebuilt.declared());
        assertEquals(call.args().size(), rebuilt.args().size());
        assertEquals(List.of(other), rebuilt.args());
    }
}
