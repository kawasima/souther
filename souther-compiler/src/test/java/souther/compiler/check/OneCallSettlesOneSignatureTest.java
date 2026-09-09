package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.WrittenOwner;
import souther.compiler.types.ReachName;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Applying a declared signature is one act, and a call settles it the same way wherever it is
 * applied: from what the context expects of the result, and then from what the value arguments state.
 *
 * <p>Held here because a difference between the two readers is not a failure anyone sees. A variable
 * settled from an empty collection instead of from the expected type leaves a closure typed over
 * {@code Nothing}, which either reports an arithmetic error in the author's own predicate or —
 * worse — types quietly and leaves an analysis reading a tree that says less than the one the backend
 * emits from.
 */
class OneCallSettlesOneSignatureTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** The lists here are this test's own: no source spells the brackets. */
    private static final SourceConstructOrigin COMPOSED = SourceConstructOrigin.unwritten();

    /** This test stands in for a body, so the names it applies are that body's references. */
    private static final SourceReferenceOrigin REF =
            new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0);

    /** And the applications are that body's too: this test stands where an author's call stands. */
    private static final ApplicationOrigin WROTE = new ApplicationOrigin.Written(
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL));
    private static final Preserved KEPT = Preserved.byTheLanguagesOwnOperations();
    private static final Hir.Binders BINDERS = new Hir.Binders(new BindingOwner.OfValue("demo", "t"));

    /** {@code List.filter(x -> true, [])} — nothing in the call says what the list holds. */
    private static Hir.Expr filterOverAnEmptyList() {
        Hir.Block predicate = new Hir.Block(List.of(BINDERS.binder("x", POS)),
                new Hir.BoolLit(true, POS, null), souther.compiler.types.RuleOrigin.unwritten(), POS, null);
        return Hir.Apply.synthetic("List.filter",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "filter")), REF, WROTE,
                List.of(predicate, new Hir.ListLit(List.of(), COMPOSED, POS, null)), POS, null);
    }

    @Test
    void anExpectedResultPinsAnEmptyContainerBeforeThePreservedClosureIsTyped() {
        Core typed = Elaborator.elaborate(filterOverAnEmptyList(), Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get())).preserving(KEPT), Type.list(Type.INT));

        Core.PreservedCall kept = assertInstanceOf(Core.PreservedCall.class, typed);
        assertEquals(Type.list(Type.INT), kept.type(),
                "the position the call stands in is the only thing that says what the list holds");
    }

    @Test
    void andTheClosureIsTypedOverWhatWasPinnedRatherThanOverNothing() {
        Core typed = Elaborator.elaborate(filterOverAnEmptyList(), Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get())).preserving(KEPT), Type.list(Type.INT));

        Core.PreservedCall kept = assertInstanceOf(Core.PreservedCall.class, typed);
        Core.Block predicate = assertInstanceOf(Core.Block.class, kept.args().get(0));
        assertEquals(List.of(Type.INT), ((Type.FnOf) predicate.type()).params(),
                "a predicate over `Nothing` is one the author's own body cannot use");
    }

    @Test
    void aFunctionHandedToAPreservedCallLearnsWhatTheValuesSettle() {
        // A lambda a `let` binds is never applied where a preserved call holds the application, so
        // what it takes is read off the declaration at the position it was handed to — through the
        // same settlement, so the parameter type that walk learns is the one the call reaches.
        String source = """
                module demo
                data Amount = Int
                    invariant value >= 1
                behavior f : (xs: List<Int>) -> List<Amount>
                    constructs Amount
                let f (xs) = {
                    let positive = y -> y > 0
                    List.map(y -> Amount(y), List.filter(positive, xs))
                }
                """;

        Compiler.Compiled compiled = Compiler.compileWithWarnings(source);

        assertFalse(compiled.classes().isEmpty(), "the function's parameter type was read");
    }

    @Test
    void everyValueArgumentIsReadOnceAndTheFunctionArgumentsNotAtAll() {
        // Reading an argument decides variables of the application it stands in, so a second reading
        // answers from the state the first one left. The settlement classifies and then unifies, and
        // both are that one reading.
        List<Type> params = List.of(
                Type.fn(List.of(new Type.Var("a", false)), Type.BOOL),
                Type.list(new Type.Var("a", false)));
        int[] reads = new int[params.size()];

        SignatureApplication.settledByValues(params, Type.list(new Type.Var("a", false)),
                Type.list(Type.INT), i -> {
                    reads[i]++;
                    return Type.list(Type.INT);
                }, Symbols.none(DefaultStdlib.get()));

        assertEquals(0, reads[0], "a function argument is typed after the values, not here");
        assertEquals(1, reads[1], "and a value argument is read once, however it is ordered");
    }

    @Test
    void anArgumentIsElaboratedOnceHoweverOftenARuleAsksItsType() {
        // The same guarantee where the answers come from: what a rule reasoned about and what reached
        // the tree are one elaboration of one argument.
        CallElaborator.CallArgs args = new CallElaborator.CallArgs(
                List.of(new Hir.IntLit(1, POS, null)), Scope.NONE, CheckContext.of(Symbols.none(DefaultStdlib.get())));

        args.type(0);
        Core first = args.cores().get(0);
        args.type(0);

        assertSame(first, args.cores().get(0), "asked twice, elaborated once");
    }

    @Test
    void anArgumentThatAnswersNoValueDoesNotSettleWhatOneThatDoesHasSaid() {
        // Option.withDefault : ('a, Option<'a>) -> 'a. The empty list states nothing about what it
        // holds, so the option beside it is what decides — the other order holds the option to the
        // element type of nothing.
        Hir.Expr call = Hir.Apply.synthetic("Option.withDefault",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("Option", "withDefault")), REF,
                WROTE,
                List.of(new Hir.ListLit(List.of(), COMPOSED, POS, null),
                        Hir.Apply.synthetic("List.get",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "get")),
                                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 1),
                                new ApplicationOrigin.Written(SourceConstructOrigin.written(
                                        new WrittenOwner.Body("m", "b"), 1, SourceConstruct.CALL)),
                                List.of(new Hir.IntLit(0, POS, null),
                                        new Hir.ListLit(List.of(new Hir.ListLit(
                                                List.of(new Hir.IntLit(1, POS, null)), COMPOSED,
                                                POS, null)), COMPOSED, POS, null)),
                                POS, null)),
                POS, null);

        Core typed = Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get())).preserving(KEPT));

        assertEquals(Type.list(Type.INT), typed.type());
    }
}
