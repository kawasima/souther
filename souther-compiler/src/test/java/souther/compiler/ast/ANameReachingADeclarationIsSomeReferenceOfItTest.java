package souther.compiler.ast;

import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ReferenceDerivationCause;
import souther.compiler.types.DerivedReferenceOrigin;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A name that reaches a declaration says which reference of it this is; one that reaches anything
 * else does not.
 *
 * <p>Two occurrences of one name reach one declaration and are two references, and the block each
 * of them expands to as a value is its own. Nothing else here tells them apart: a spelling is a
 * pass's to change, and a place is shared by every copy of a helper that was expanded. So the
 * reference is carried, and a name reaching a declaration without one is refused where it is made
 * rather than wherever a reader first wants it.
 *
 * <p><b>Whoever wrote the name owes one.</b> An author's reference is one this source counted. A
 * pass writing a name where the language has no syntax for what it means — the operation an empty
 * collection stands for — wrote a reference too, and says what made it write one. Held to the
 * author's alone, the slot would have no value a pass could put in it, and what stood in for that
 * was a reader refusing halfway down.
 *
 * <p>And a name that reaches no declaration carries none, because what such a name is, is what it
 * reaches: a binding is already a thing this compiler tells from every other, a namespace is not a
 * thing to be a reference of, and a name that denotes nothing has nothing to be a reference of
 * either.
 */
class ANameReachingADeclarationIsSomeReferenceOfItTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final ValueName.Helper DECLARED = new ValueName.Helper("demo", "spin");

    private static final WrittenName SPIN = WrittenName.of("spin", POS);

    /** A name the author wrote, carrying the reference this source counted it as. */
    @Test
    void aNameReachingADeclarationMayCarryTheAuthorsReference() {
        Hir.Var.Denoting named = new Hir.Var.Denoting(SPIN, new ReachName.Own(DECLARED),
                new SourceReferenceOrigin(new WrittenOwner.Body("demo", "b"), 2), SPIN.region());

        assertEquals(new SourceReferenceOrigin(new WrittenOwner.Body("demo", "b"), 2),
                named.origin());
    }

    /**
     * And a name a pass wrote, carrying what made it write one. The arm exists to be used: a slot
     * that only ever held the author's would be one this value could not go in, and the pass would
     * be left with nothing to say.
     */
    @Test
    void aNameReachingADeclarationMayCarryAReferenceAPassDerived() {
        DerivedReferenceOrigin derived = new DerivedReferenceOrigin(
                new ReferenceDerivationCause.CollectionLiteral(SourceConstructOrigin.written(
                        new WrittenOwner.Body("demo", "b"), 0, SourceConstruct.COLLECTION_LITERAL)),
                0);

        Hir.Var.Denoting written = new Hir.Var.Denoting(SPIN, new ReachName.Own(DECLARED),
                derived, SPIN.region());

        assertEquals(derived, written.origin());
    }

    /**
     * And a name the generator composed, carrying the occurrence that run gave it. Nothing wrote
     * this reference and no construct a source wrote is behind it, so what says which one it is, is
     * the run that made it.
     */
    @Test
    void aNameReachingADeclarationMayCarryAReferenceARunComposed() {
        FixtureReferenceOrigin composed = new FixtureReferenceOrigin(1);

        Hir.Var.Denoting named = new Hir.Var.Denoting(SPIN, new ReachName.Own(DECLARED),
                composed, SPIN.region());

        assertEquals(composed, named.origin());
    }

    /** Neither, and the name is refused where it is put together. */
    @Test
    void aNameReachingADeclarationWithNoReferenceIsRefused() {
        IllegalArgumentException noReference = assertThrows(IllegalArgumentException.class,
                () -> new Hir.Var.Denoting(SPIN, new ReachName.Own(DECLARED), null, SPIN.region()));

        assertEquals(true, noReference.getMessage().contains("spin"), noReference.getMessage());
    }

    /**
     * A reference derived from a construct is derived from one a source wrote, and the cause
     * refuses one that says nobody did.
     *
     * <p>The asymmetry is the point. A pass may compose brackets no source wrote — the two lists a
     * comprehension lowers to — so {@code Hir.ListLit} holds either answer. What is derived from a
     * source construct is a narrower thing: given an unwritten one, the cause would be a type that
     * says a source is behind this while holding the record that none is, and the reference numbered
     * under it would be numbered under nothing.
     */
    @Test
    void aCauseDerivedFromASourceRefusesAConstructNoSourceWrote() {
        IllegalArgumentException nothingBehindIt = assertThrows(IllegalArgumentException.class,
                () -> new ReferenceDerivationCause.CollectionLiteral(SourceConstructOrigin.unwritten()));

        assertEquals(true, nothingBehindIt.getMessage().contains("collection"),
                nothingBehindIt.getMessage());
    }

    /**
     * And an application says why it is here, refused where it is put together rather than left for
     * a reader to find.
     *
     * <p>Asked of the record and not of the factories alone. A rule the factories keep is one the
     * representation does not: a pass reaching for the constructor would leave a reader nothing, and
     * what a reader does with nothing is work it out from the shape — which is the reading this
     * exists to remove.
     */
    @Test
    void anApplicationWithNoReasonToBeHereIsRefusedWhereItIsMade() {
        IllegalArgumentException noReason = assertThrows(IllegalArgumentException.class,
                () -> new Hir.Apply(new Hir.IntLit(1, POS, null), List.of(),
                        souther.compiler.ast.Origins.Own.IT_IS,
                        new Hir.AppliedCallee(null, Region.point(POS)), null, POS, null));

        assertEquals(true, noReason.getMessage().contains("some reason"), noReason.getMessage());
    }

    /** A read of a binding carries none: the binding is what tells it from every other read. */
    @Test
    void aNameReachingABindingCarriesNoReference() {
        Hir.Binder bound = new Hir.Binders(new BindingOwner.OfValue("demo", "spin"))
                .binder("x", POS);

        assertNull(Hir.Var.local(bound, POS).origin());
    }

    /** And a namespace is not a thing to be a reference of. */
    @Test
    void aNameReachingANamespaceCarriesNoReference() {
        ValueName.Stdlib.Namespace list = ValueName.Stdlib.namespace("List");

        Hir.Var.Denoting namespace = new Hir.Var.Denoting(WrittenName.of("List", POS),
                new ReachName.TheNamespace(list), null, WrittenName.of("List", POS).region());

        assertNull(namespace.origin());
    }

    /** Nor is a name resolution found nothing for. */
    @Test
    void aNameThatDenotesNothingCarriesNoReference() {
        assertNull(new Hir.Var.Unanswered(SPIN, null, SPIN.region()).origin());
    }
}
