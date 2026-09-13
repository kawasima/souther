package souther.compiler.inputs;

import souther.compiler.check.Carrier;
import souther.compiler.check.NewtypeInners;
import souther.compiler.check.NumericAnswers;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.types.Type;

/**
 * Both orders a term stands on, worked out from what stands where its number comes from.
 *
 * <p>The one place the derivation lives, and not reachable from a term. A term is a value that
 * travels, and a type is in every reader's hand, so a derivation written on the term is a question
 * anything holding the two can answer — about wherever that type came from, and about no reading in
 * particular. What settles where a term's number comes from is the reading of the input, so the one
 * caller here is {@link ReadQuantities#ordersOf}, which puts the reading's own answer in.
 *
 * <p>What the answer is about is therefore the reading's to say, and every other layer asks
 * {@link Quantities#ordersOf} for it rather than working it out again.
 */
final class TermOrdering {

    private TermOrdering() { }

    /**
     * Both ends together, which is what every reader of a row wants and what neither end alone is
     * safe to stand in for. A term that is what a location holds has one order twice, and says so
     * here rather than by two readings that happen to agree.
     */
    static TermOrders of(NumericTerm term, Type positionType, NewtypeInners inners,
                         Symbols symbols,
                         DeclarationKinds kinds, PublishedDeclarations published) {
        Carrier observed = observedOn(positionType, inners, symbols, kinds, published);
        // One construction and not one per arm. A term that is a location's own content answers on
        // the order its value is read on, which is that order twice rather than a second way of
        // making a pair — and a second way is a second place a pair can come from.
        Carrier answered = switch (term) {
            case NumericTerm.ValueOf _ -> observed;
            case NumericTerm.TakenOf _, NumericTerm.TakenOver _ ->
                    answeredOn(term, positionType, inners, symbols, kinds, published);
        };
        return new TermOrders(term, observed, answered);
    }

    /**
     * The order the number a term names is measured on, or null where it has none.
     *
     * <p>What a rule about the length of a string is counted as is an {@code Int} at a position no
     * line is drawn on, and what a rule about {@code Time.hour(t)} is counted as is a count by one at
     * a position counting the seconds of its day. Both follow from what the operation answers and
     * from nothing about where it was applied — asked of the position, the step of the answer was
     * the step of the argument, and the twelfth hour was a line at the twelfth second.
     *
     * <p>Which is why {@code positionType} may be absent. A term under more steps than the walk that
     * finds an input's positions goes down has no position to ask, and what an operation answers is
     * what it answers all the same. What is null there is a term measured by its own values, which
     * is the one case the position was the answer to.
     */
    private static Carrier answeredOn(NumericTerm term, Type positionType, NewtypeInners inners,
                                      Symbols symbols,
                                      DeclarationKinds kinds, PublishedDeclarations published) {
        return switch (term) {
            case NumericTerm.ValueOf _ -> positionType == null ? null
                    : Carrier.ofValue(positionType, inners, symbols, kinds, published);
            case NumericTerm.TakenOf taken -> {
                Type answers =
                        NumericAnswers.typeOf(taken.operation(), positionType, inners, symbols);
                yield answers == null ? null
                        : Carrier.ofValue(answers, inners, symbols, kinds, published);
            }
            // Asked of the operation as a taking is, and asked of what it was given: a run is a
            // container of the values standing at the place it is read from, so what the operation
            // answers of one is what it answers of a container of them. Written out here as the
            // element's own order instead, this would be the account of a walk that adds restated
            // for every account, and the first one that answers something else would be read as
            // answering what its elements are.
            case NumericTerm.TakenOver over -> {
                Type answers = positionType == null ? null : NumericAnswers.typeOf(
                        over.operation(), new Type.ListOf(positionType), inners, symbols);
                yield answers == null ? null
                        : Carrier.ofValue(answers, inners, symbols, kinds, published);
            }
        };
    }

    /**
     * The order a value at the term's path is decoded on, or null where nothing orders it.
     *
     * <p>The other end of the same term, and null for more than one reason. A container is not
     * ordered and is read by what it holds rather than by a count of its own; a position whose type
     * nothing here can follow has no order either. Both are answers a reader can act on — what is
     * refused is silently reading the value on the order its answer is measured on, which is right
     * for every operation whose two ends agree and wrong without a word for the first that does not.
     */
    private static Carrier observedOn(Type positionType, NewtypeInners inners, Symbols symbols,
                                      DeclarationKinds kinds,
                                      PublishedDeclarations published) {
        return positionType == null ? null
                : Carrier.ofValue(positionType, inners, symbols, kinds, published);
    }
}
