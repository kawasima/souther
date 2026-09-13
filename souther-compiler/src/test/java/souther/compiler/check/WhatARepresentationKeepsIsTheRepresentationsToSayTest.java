package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.core.CompleteSignature;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.ReachName;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which calls survive is decided where a representation is built, and the checker is told. It asks
 * only whether this call is one of them — not what the operation means, which belongs to whoever
 * reads the representation. So an operation kept with no rule about it types like any other and
 * states nothing, and typing a call never waits on someone having a rule for it.
 */
class WhatARepresentationKeepsIsTheRepresentationsToSayTest {

    /** The applications are a body's: this test stands where an author's call stands. */
    private static final ApplicationOrigin WROTE = new ApplicationOrigin.Written(
            SourceConstructOrigin.written(new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL));

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final ValueName.Stdlib.Operation MAP =
            ValueName.Stdlib.operation("List", "map");

    /** `(Int) -> Int`, standing for whatever the operation was declared as. */
    private static final Type.FnOf SIGNATURE = (Type.FnOf) Type.fn(List.of(Type.INT), Type.INT);

    @Test
    void aKeptCallIsTypedFromTheSignatureItWasDeclaredWith() {
        Core typed = elaborate(callTo(MAP), keeping(MAP, SIGNATURE));

        Core.PreservedCall kept = assertInstanceOf(Core.PreservedCall.class, typed);
        assertEquals(MAP, kept.operation(), "what the name resolved to, not how it was written");
        assertEquals(Type.INT, kept.type());
    }

    @Test
    void aRepresentationThatKeepsNothingStillRefusesIt() {
        assertThrows(RuntimeException.class, () -> elaborate(callTo(MAP), Preserved.NONE));
    }

    @Test
    void anOperationThisRepresentationNeverSaidItKeepsIsStillARefusal() {
        // keeping one operation is not keeping the namespace it is in: a call this representation
        // never claimed is this compiler having failed to expand it, whichever library it names
        Preserved keepsMapOnly = keeping(MAP, SIGNATURE);

        assertThrows(RuntimeException.class,
                () -> elaborate(callTo(ValueName.Stdlib.operation("List", "filter")), keepsMapOnly));
    }

    @Test
    void aKeptCallAppliedToTheWrongNumberOfArgumentsIsSaidAsThat() {
        Hir.Expr twoArgs = Hir.Apply.synthetic("List.map", new ReachName.OfLibrary(MAP),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(new Hir.IntLit(1, POS, null), new Hir.IntLit(2, POS, null)), POS, null);

        assertThrows(RuntimeException.class, () -> elaborate(twoArgs, keeping(MAP, SIGNATURE)));
    }

    @Test
    void aTreeThatBelongsToAnotherRepresentationDoesNotInheritThePermission() {
        // A body reaching a declaration's invariant reaches another tree, built by another question.
        // What it keeps is its own to say at its own entry, and being reached from here is not a
        // representation having said anything.
        //
        // Held by there being nowhere to inherit it from: the contexts a clause is elaborated in are
        // built rather than derived from whatever context reached them (issue #1080). This used to
        // be a context you could carry across the boundary and a method that emptied it on the way.
        assertEquals(Preserved.NONE,
                CheckContext.executableInvariant(Symbols.none(DefaultStdlib.get()),
                                PublishedDeclarations.NONE, DeclarationKinds.NONE, null)
                        .preserved());
        assertEquals(Preserved.NONE,
                CheckContext.executableEnsures(Symbols.none(DefaultStdlib.get()),
                                PublishedDeclarations.NONE, DeclarationKinds.NONE).preserved(),
                "and a rule is read at an entry of its own, as a clause is");
    }

    @Test
    void whichDataIsBeingCheckedIsNotWhereARepresentationEnds() {
        // `forData` moves within one representation as well, so it must not quietly mean the
        // permission is gone
        CheckContext keeping = CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE).preserving(keeping(MAP, SIGNATURE));

        assertEquals(keeping.preserved(), keeping.forData(null).preserved());
    }

    private static Hir.Expr callTo(ValueName.Stdlib.Operation operation) {
        return Hir.Apply.synthetic(operation.qualified(), new ReachName.OfLibrary(operation),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0), WROTE,
                List.of(new Hir.IntLit(1, POS, null)), POS, null);
    }

    private static Preserved keeping(ValueName operation, Type.FnOf signature) {
        return Preserved.keeping(List.of(CompleteSignature.ofDeclaration(operation,
                signature.params(), signature.result())));
    }

    private static Core elaborate(Hir.Expr e, Preserved kept) {
        return Elaborator.elaborate(e, Scope.NONE,
                CheckContext.of(Symbols.none(DefaultStdlib.get()), PublishedDeclarations.NONE,
                DeclarationKinds.NONE).preserving(kept));
    }
}
