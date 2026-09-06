package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.BinOp;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a product of two values is held to, where the path bounds the two.
 *
 * <p>A product of two values is outside the affine fragment, so the check names it an atom of its
 * own and the domain holds nothing about it — however much it holds about the factors. What is
 * asked here is the step from the one to the other: the atom is recorded against what it was
 * computed from, and a reader turning that into a bound reads the factors out of the domain it is
 * given.
 *
 * <p>The two halves are apart on purpose. What an atom was computed from is a fact about the
 * expression and is settled where the atom is named; what it lies between depends on what the path
 * assumed, and is answered against the domain the question is asked with. Recording a bound at
 * naming time would fix it under whichever reading named it first.
 */
class AProductIsHeldToWhatThePathKnowsOfItsFactorsTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static BindingId binding(int index) {
        return new BindingId(new BindingOwner.OfValue("demo", "f"), index);
    }

    private static Core.Read read(String name, BindingId binding) {
        return new Core.Read(name, binding, Type.INT, POS);
    }

    private static Core.Binary arithmetic(BinOp op, Core left, Core right) {
        return new Core.Binary(op, left, right, ConstructOccurrence.unwritten(), Type.INT, POS);
    }

    private static LinearForm<FactSubject> num(long n) {
        return LinearForm.constant(BigDecimal.valueOf(n));
    }

    /** A domain in which each of {@code atoms} is at or above zero. */
    private static NumericDomain<FactSubject> atOrAboveZero(Terms terms, FactSubject... atoms) {
        NumericDomain<FactSubject> d = NumericDomain.top();
        for (FactSubject atom : atoms) {
            LinearForm<FactSubject> form = LinearForm.atom(atom);
            d = d.assume(form, Rel.GE, terms.kindsOf(form));
        }
        return d;
    }

    /**
     * The issue's first example: two factors the guards put at or above zero, and a product the
     * clause is read against.
     */
    @Test
    void aProductOfTwoFactorsThePathBoundsBelowIsBoundedBelow() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId a = binding(0);
        BindingId b = binding(1);
        Denotations at = Denotations.none().location(a, AsPlaces.of(a), AsPlaces.term(a)).location(b, AsPlaces.of(b), AsPlaces.term(b));

        LinearForm<FactSubject> product = terms.affineOf(
                arithmetic(BinOp.MUL, read("a", a), read("b", b)), at);

        assertNotNull(product, "a product is one value, whatever is known of it");
        FactSubject atom = product.coefs().keySet().iterator().next();
        NumericDomain<FactSubject> guarded = atOrAboveZero(terms,
                FactSubject.of(terms.bodyKey(read("a", a), at)),
                FactSubject.of(terms.bodyKey(read("b", b), at)));
        assertTrue(guarded.boundsOf(atom).saysNothing(),
                "nothing was said about the product itself");

        NumericDomain<FactSubject> derived = DerivedNumericFacts.refine(guarded, terms, Set.of(atom));

        assertEquals(Endpoint.inclusive(Count.of(0)), derived.boundsOf(atom).min());
        assertNull(derived.boundsOf(atom).max(), "nothing bounds either factor above");
    }

    /**
     * The same product read against a domain that assumed nothing: the derivation is what the path
     * knows and not a property of the operation, so it answers with nothing here.
     *
     * <p>This is what keeps a derived bound the path's. The check reads every construction twice —
     * once with what the guards established and once without — and a bound recorded against the atom
     * when it was named would be in both.
     */
    @Test
    void theSameProductIsBoundedByNothingWhereThePathAssumedNothing() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId a = binding(0);
        BindingId b = binding(1);
        Denotations at = Denotations.none().location(a, AsPlaces.of(a), AsPlaces.term(a)).location(b, AsPlaces.of(b), AsPlaces.term(b));

        LinearForm<FactSubject> product = terms.affineOf(
                arithmetic(BinOp.MUL, read("a", a), read("b", b)), at);
        FactSubject atom = product.coefs().keySet().iterator().next();

        NumericDomain<FactSubject> derived = DerivedNumericFacts.refine(NumericDomain.top(), terms, Set.of(atom));

        assertTrue(derived.boundsOf(atom).saysNothing());
    }

    /**
     * The issue's second example: a quotient of a scaled factor by a written constant, which is one
     * derivation reading another.
     *
     * <p>{@code x * 30 / 100} names {@code x * 30} nothing of its own — a scalar multiply is
     * linear — so the quotient's numerator is a form over {@code x}, and what the quotient lies
     * between follows from what {@code x} does.
     */
    @Test
    void aQuotientByAWrittenConstantFollowsTheBoundOnItsNumerator() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId x = binding(0);
        Denotations at = Denotations.none().location(x, AsPlaces.of(x), AsPlaces.term(x));
        Core scaled = arithmetic(BinOp.MUL, read("x", x), new Core.Int(30, Type.INT, POS));
        Core quotient = arithmetic(BinOp.DIV, scaled, new Core.Int(100, Type.INT, POS));

        LinearForm<FactSubject> form = terms.affineOf(quotient, at);

        assertNotNull(form);
        FactSubject atom = form.coefs().keySet().iterator().next();
        NumericDomain<FactSubject> guarded = atOrAboveZero(terms, FactSubject.of(terms.bodyKey(read("x", x), at)));

        NumericDomain<FactSubject> derived = DerivedNumericFacts.refine(guarded, terms, Set.of(atom));

        assertEquals(Endpoint.inclusive(Count.of(0)), derived.boundsOf(atom).min());
    }

    /**
     * A quotient whose numerator is itself a product: the two derivations are read in the order the
     * expression puts them, whichever order they were recorded in.
     */
    @Test
    void aQuotientOverAProductReadsWhatTheProductWasDerivedTo() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId a = binding(0);
        BindingId b = binding(1);
        Denotations at = Denotations.none().location(a, AsPlaces.of(a), AsPlaces.term(a)).location(b, AsPlaces.of(b), AsPlaces.term(b));
        Core product = arithmetic(BinOp.MUL, read("a", a), read("b", b));
        Core quotient = arithmetic(BinOp.DIV, product, new Core.Int(100, Type.INT, POS));

        LinearForm<FactSubject> form = terms.affineOf(quotient, at);
        FactSubject atom = form.coefs().keySet().iterator().next();
        FactSubject factorA = FactSubject.of(terms.bodyKey(read("a", a), at));
        FactSubject factorB = FactSubject.of(terms.bodyKey(read("b", b), at));
        NumericDomain<FactSubject> guarded = NumericDomain.<FactSubject>top()
                .assume(LinearForm.atom(factorA), Rel.GE,
                        terms.kindsOf(LinearForm.atom(factorA)))
                .assume(LinearForm.atom(factorA).minus(num(10)), Rel.LE,
                        terms.kindsOf(LinearForm.atom(factorA)))
                .assume(LinearForm.atom(factorB), Rel.GE,
                        terms.kindsOf(LinearForm.atom(factorB)))
                .assume(LinearForm.atom(factorB).minus(num(1000)), Rel.LE,
                        terms.kindsOf(LinearForm.atom(factorB)));

        NumericDomain<FactSubject> derived = DerivedNumericFacts.refine(guarded, terms, Set.of(atom));

        assertEquals(Endpoint.inclusive(Count.of(0)), derived.boundsOf(atom).min());
        assertEquals(Endpoint.inclusive(Count.of(100)), derived.boundsOf(atom).max(),
                "10 * 1000 / 100");
    }

    /** What an atom was computed from is a fact about the expression, so one atom reached twice is
     * recorded once and the second reading agrees with the first. */
    @Test
    void oneProductWrittenTwiceIsRecordedOnce() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId a = binding(0);
        BindingId b = binding(1);
        Denotations at = Denotations.none().location(a, AsPlaces.of(a), AsPlaces.term(a)).location(b, AsPlaces.of(b), AsPlaces.term(b));

        FactSubject first = terms.affineOf(arithmetic(BinOp.MUL, read("a", a), read("b", b)), at).coefs().keySet().iterator().next();
        FactSubject second = terms.affineOf(arithmetic(BinOp.MUL, read("a", a), read("b", b)), at).coefs().keySet().iterator().next();

        assertEquals(first, second);
        assertTrue(terms.knowledgeOf(first).computation()
                instanceof AtomKnowledge.Computation.Derived, "the one atom holds the one recipe");
    }

    /** A product of two whole numbers is a whole number, which is what the domain records it as. */
    @Test
    void aDerivedAtomKeepsTheSpacingOfTheNumberItIs() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        BindingId a = binding(0);
        BindingId b = binding(1);
        Denotations at = Denotations.none().location(a, AsPlaces.of(a), AsPlaces.term(a)).location(b, AsPlaces.of(b), AsPlaces.term(b));

        LinearForm<FactSubject> product = terms.affineOf(
                arithmetic(BinOp.MUL, read("a", a), read("b", b)), at);
        FactSubject atom = product.coefs().keySet().iterator().next();

        assertEquals(Map.of(atom, Granularity.DISCRETE), terms.kindsOf(product));
    }
}
