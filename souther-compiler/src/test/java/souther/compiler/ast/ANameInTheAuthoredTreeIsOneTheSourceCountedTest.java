package souther.compiler.ast;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A name in the tree a source produces is a reference that source counted, and one in a tree a pass
 * writes need not be.
 *
 * <p>Two invariants and not one. {@link Ast} is what reading a source leaves, so every name in it
 * is one the source wrote — the desugarings that run while it is being built write names of their
 * own, and each of those is a reference of the source that made it necessary. {@link Hir} is what
 * the passes below rewrite, and a name one of them writes is nothing a source wrote: there is no
 * occurrence to count it among, and numbering it with the author's would put this compiler's work
 * among the model's.
 *
 * <p>Which is why neither invariant is derived from the other. A reader below that needs to tell one
 * reference from another is told so by what the name it has in hand carries, and not by an argument
 * that runs back to how the tree above was built.
 */
class ANameInTheAuthoredTreeIsOneTheSourceCountedTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final WrittenOwner OWNER = new WrittenOwner.Body("demo", "spin");

    /** A name the parser read carries the reference the reading counted it as. */
    @Test
    void aNameTheParserReadCarriesTheReferenceItWasCountedAs() {
        Ast.Var read = Ast.Var.written("price", POS, new SourceReferenceOrigin(OWNER, 3));

        assertEquals(new SourceReferenceOrigin(OWNER, 3), read.origin());
    }

    /**
     * A name a desugaring writes while the tree is being read is a reference too. What made it
     * necessary is something the source wrote, and it is counted among that source's references
     * with the rest.
     */
    @Test
    void aNameADesugaringWritesWhileReadingIsCountedWithTheRest() {
        Ast.Var minted = Ast.Var.desugared("$whole", POS, new SourceReferenceOrigin(OWNER, 4));

        assertEquals(new SourceReferenceOrigin(OWNER, 4), minted.origin());
    }

    /**
     * A name with no reference is refused where it is made. Held as something a reader below has to
     * check for, the check would stand in for the invariant and say nothing about whether a name
     * without one is impossible or merely has not turned up.
     */
    @Test
    void aNameWithNoReferenceIsRefusedWhereItIsMade() {
        IllegalArgumentException counted = assertThrows(IllegalArgumentException.class,
                () -> Ast.Var.written("price", POS, null));

        assertEquals(true, counted.getMessage().contains("price"), counted.getMessage());
    }

    /**
     * And the same invariant is not asked of the tree below. A pass reading a binding it put there
     * wrote that name, so there is no reference of the source to carry — and this is a name the
     * passes hand around, not one refused for the lack of it.
     */
    @Test
    void aNameAPassWroteBelowCarriesNoReferenceOfTheSource() {
        Hir.Binder bound = new Hir.Binders(new BindingOwner.OfValue("demo", "spin"))
                .binder("$0", POS);

        Hir.Var read = Hir.Var.local(bound, POS);

        assertNotNull(read.answered(), "a pass reading a binding says what it reads");
        assertNull(read.origin(), "no source wrote it, so it is none of the source's references");
    }

    /** The one a pass writes still says what it reaches, which is what a reader below asks it. */
    @Test
    void aNameAPassWroteBelowStillSaysWhatItReaches() {
        ValueName.Helper helper = new ValueName.Helper("demo", "spin");

        Hir.Var written = Hir.Var.denoting("demo.spin", new ReachName.Own(helper), POS);

        assertEquals(helper, written.answered().denotes());
    }
}
