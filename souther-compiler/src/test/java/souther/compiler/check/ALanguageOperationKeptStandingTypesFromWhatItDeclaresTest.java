package souther.compiler.check;

import souther.compiler.DefaultStdlib;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operations the discharge representation keeps are the language's own, and each types from the
 * signature the library declared it with — variables and all, settled by the arguments it was given.
 * Nothing about the operation is written here: what is kept comes from the library, so one it gains
 * is kept without this being told.
 */
class ALanguageOperationKeptStandingTypesFromWhatItDeclaresTest {

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

    @Test
    void aPolymorphicOperationSettlesItsVariablesFromItsArguments() {
        // List.length : (List<'a>) -> Int — the argument decides 'a, and the result is not a variable
        Hir.Expr call = Hir.Apply.synthetic("List.length",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "length")), REF, WROTE,
                List.of(new Hir.ListLit(List.of(new Hir.IntLit(1, POS, null)), COMPOSED, POS, null)),
                POS, null);

        Core typed = Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE).preserving(KEPT));

        Core.PreservedCall kept = assertInstanceOf(Core.PreservedCall.class, typed);
        assertEquals(ValueName.Stdlib.operation("List", "length"), kept.operation());
        assertEquals(Type.INT, kept.type());
    }

    @Test
    void whatAFunctionArgumentAnswersSettlesTheRest() {
        // List.flatMap : (('a) -> List<'b>, List<'a>) -> List<'b>. The declared result of the
        // function argument is `List<'b>` and not `'b`, so reading it as an accumulator to grow —
        // which is one operation's meaning and not what applying a signature is — leaves `'b` a
        // variable and refuses the call. Applying the signature settles it from what the function
        // answered.
        Hir.Binders binders = new Hir.Binders(new BindingOwner.OfValue("demo", "test"));
        Hir.Block step = new Hir.Block(List.of(binders.binder("x", POS)),
                new Hir.ListLit(List.of(new Hir.IntLit(1, POS, null)), COMPOSED, POS, null), souther.compiler.types.RuleOrigin.unwritten(), POS, null);
        Hir.Expr call = Hir.Apply.synthetic("List.flatMap",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "flatMap")), REF, WROTE,
                List.of(step, new Hir.ListLit(List.of(new Hir.IntLit(2, POS, null)), COMPOSED, POS, null)),
                POS, null);

        Core typed = Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE).preserving(KEPT));

        assertEquals(Type.list(Type.INT), typed.type());
    }

    @Test
    void theOperationsKeptAreTheLibrarysAndNotAListWrittenHere() {
        assertNotNull(KEPT.signatureOf(ValueName.Stdlib.operation("List", "map")),
                "an operation the discharge rules are written about");
        assertNotNull(KEPT.signatureOf(ValueName.Stdlib.operation("String", "length")),
                "and one they are not — what is kept is not decided by having a rule");
    }

    @Test
    void anOperationRewrittenAwayBeforeAnyOfThisIsNotKept() {
        // `List.fold` becomes `List.foldFrom` before this tree exists, so it has no declaration to
        // keep — and a rule keyed by it could never be looked up either
        assertTrue(KEPT.signatureOf(ValueName.Stdlib.operation("List", "fold")) == null,
                "sugar has no declaration of its own");
        assertNotNull(KEPT.signatureOf(ValueName.Stdlib.operation("List", "foldFrom")),
                "what it becomes does");
    }

    @Test
    void aModulesOwnHelperIsNotKeptByThisPolicy() {
        // nothing could be derived from leaving it standing, so it is expanded — and a tree that
        // still holds one is this compiler having failed to do that
        ValueName.Helper half = new ValueName.Helper("demo", "half");
        Hir.Expr call = Hir.Apply.synthetic("half",
                new ReachName.Own(half), REF, WROTE, List.of(new Hir.IntLit(1, POS, null)), POS,
                null);

        assertThrows(RuntimeException.class, () -> Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE).preserving(KEPT)));
    }
}
