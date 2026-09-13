package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A call under a library qualifier that reached call elaboration applying nothing is one of two
 * things, and which of them is not read off the spelling.
 *
 * <p>The library declares a member of that name, or it does not. Where it does, the call was to be
 * expanded or bound before anything typed it and neither happened — which is this compiler
 * disagreeing with itself, and nothing an author wrote or can undo. Where it does not, the author
 * named an operation the library has no member for, and that is theirs. Told apart here rather than
 * both being said as the second: a member the library keeps to itself is one a caller may not write,
 * and the report for a name nobody wrote sends the reader to look for a mistake in their own text.
 *
 * <p>A {@code private} declaration is a member for this question. Whether a caller may write the
 * name and whether the library has one are two questions, and this is the second — which is why a
 * failure to expand {@code List.foldFrom} is answered here as the compiler's rather than as a
 * misspelling of something in the published surface.
 *
 * <p>A sugar declares nothing. It is a rewrite onto a name that does, so a call still spelled as the
 * sugar is one the rewrite did not take — {@code List.fold} written with two arguments is not the
 * three-argument call it stands for — and what is wrong with it is what was written.
 *
 * <p>That such a call is refused at all is {@link
 * AnUnexpandedCallIsOnlyTypedWhereARepresentationKeepsItTest}'s; which refusal it is, is this.
 */
class WhetherAWrongLibraryCallIsTheAuthorsIsTheLibrarysToSayTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** A call of {@code qualifier.operation} applied to {@code args} integers, standing unexpanded. */
    private static Hir.Expr callTo(String qualifier, String operation, int args) {
        ValueName.Stdlib.Operation name = ValueName.Stdlib.operation(qualifier, operation);
        List<Hir.Expr> given = new java.util.ArrayList<>();
        for (int i = 0; i < args; i++) {
            given.add(new Hir.IntLit(i, POS, null));
        }
        return Hir.Apply.synthetic(name.qualified(), new ReachName.OfLibrary(name),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0),
                new ApplicationOrigin.Written(SourceConstructOrigin.written(
                        new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL)),
                given, POS, null);
    }

    private static RuntimeException refusing(Hir.Expr call) {
        return assertThrows(RuntimeException.class,
                () -> Elaborator.elaborate(call, Scope.NONE, CheckContext.of(Symbols.none(DefaultStdlib.get()),
                        PublishedDeclarations.NONE, DeclarationKinds.NONE)));
    }

    /**
     * The recursive helper the fold combinators are derived from, applied to what it declares. It is
     * reached only through the library's own bodies, so a call of it standing here is an expansion
     * this compiler owed and did not make.
     */
    @Test
    void aNameTheLibraryDeclaresIsThisCompilersMistake() {
        RuntimeException refused = refusing(callTo("List", "foldFrom", 4));

        assertTrue(refused instanceof IllegalStateException,
                "a name the library declares is not something an author can be told about, and was: "
                        + refused);
        assertTrue(refused.getMessage().contains("List.foldFrom"),
                "and the failure names what was not expanded: " + refused.getMessage());
    }

    /** A published one says the same thing: which member it is decides nothing here. */
    @Test
    void andSoIsOneItPublishes() {
        assertTrue(refusing(callTo("List", "map", 2)) instanceof IllegalStateException);
    }

    /** A spelling under a library qualifier that the library has no member for. */
    @Test
    void aNameItDeclaresNothingUnderIsTheAuthorsAndIsReportedAsThat() {
        RuntimeException refused = refusing(callTo("List", "mapp", 2));

        assertTrue(refused instanceof CompileException,
                "a misspelling is the author's to fix, and was: " + refused);
        assertEquals("E1506", ((CompileException) refused).code());
    }

    /**
     * A sugar written with fewer arguments than it is sugar for. The rewrite is not taken, so what
     * stands is a name the library declares nothing under — and the reader is told about the call
     * they wrote rather than about the one it would have become.
     */
    @Test
    void aSugarTheRewriteDidNotTakeIsTheAuthorsToo() {
        RuntimeException refused = refusing(callTo("List", "fold", 2));

        assertTrue(refused instanceof CompileException,
                "a sugar has no declaration to have failed to expand: " + refused);
        assertEquals("E1506", ((CompileException) refused).code());
        assertTrue(refused.getMessage().contains("List.fold"),
                "and it is named as it was written: " + refused.getMessage());
    }
}
