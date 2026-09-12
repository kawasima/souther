package souther.compiler.inputs;

import souther.compiler.check.Carrier;

/**
 * Synthetic orders for a test that describes a term rather than reading one.
 *
 * <p>Here because a package is what package-private means, and a test source set is what says this
 * is not production. What it opens is open to tests and to nothing that ships: production reaches
 * {@link TermOrders} through {@link Quantities#ordersOf} and has no way to mint one, which is the
 * whole point of closing the constructor, and a test writing a border by hand still needs to say
 * what its number is counted on.
 */
public final class TermOrdersFixtures {

    private TermOrdersFixtures() { }

    /** A term whose value is the number it answers. */
    public static TermOrders itself(NumericTerm term, Carrier carrier) {
        return new TermOrders(term, carrier, carrier);
    }

    /** A term read on one order and answering on another, which is every taking of an operation. */
    public static TermOrders orders(NumericTerm term, Carrier observed, Carrier answered) {
        return new TermOrders(term, observed, answered);
    }

    /**
     * What the derivation answers for a term at a type, which is the derivation itself.
     *
     * <p>For the tests that are about it: what an operation answers with, and what a value at the
     * position it was taken of is read on. Production reaches this only through a reading, which is
     * what settles which type to ask about; a test naming the type is asking the narrower question
     * of what follows from a type once one is chosen.
     */
    public static TermOrders at(NumericTerm term, souther.compiler.types.Type positionType,
                                souther.compiler.check.Symbols symbols) {
        return TermOrdering.of(term, positionType, symbols,
                souther.compiler.check.ScopedDeclarations.kindsOf(symbols),
                souther.compiler.check.ScopedDeclarations.of(symbols));
    }
}
