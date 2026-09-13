package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Typing is defined over what a representation is allowed to hold. The tree the backend emits from
 * has every call it cannot emit already expanded, so one arriving at call elaboration there is this
 * compiler having failed to expand it — not a program to type. That guard is what stops an expansion
 * bug from being emitted as something else, and it is fixed here before any representation is given
 * permission to keep a call standing.
 */
class AnUnexpandedCallIsOnlyTypedWhereARepresentationKeepsItTest {

    /** The applications are a body's: this test stands where an author's call stands. */
    private static final ApplicationOrigin WROTE = new ApplicationOrigin.Written(
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL));

    private static final SourcePos POS = new SourcePos(1, 1);

    @Test
    void aStandardLibraryCallLeftStandingIsNotSomethingToType() {
        Hir.Expr call = Hir.Apply.synthetic("List.map",
                new ReachName.OfLibrary(ValueName.Stdlib.operation("List", "map")),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(new Hir.IntLit(1, POS, null)), POS, null);

        assertThrows(RuntimeException.class, () -> Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE)));
    }

    @Test
    void aHelperLeftStandingIsNotSomethingToTypeEither() {
        // a module's own `let` is expanded into the body that called it, so this is the same failure
        // as above and not a different one — the guard is about the representation, not about which
        // namespace the name was in
        ValueName.Helper half = new ValueName.Helper("demo", "half");
        Hir.Expr call = Hir.Apply.synthetic("half",
                new ReachName.Own(half),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(new Hir.IntLit(1, POS, null)), POS, null);

        assertThrows(RuntimeException.class, () -> Elaborator.elaborate(call, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE)));
    }
}
