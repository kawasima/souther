package souther.compiler.check;

import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Every value a type has, where they can be counted out at all.
 *
 * <p>Asked so that a rule saying which values a position may not hold can be read as the values it
 * may. {@code /= true} beside {@code /= false} leaves nothing where the values are two and leaves
 * almost everything where they are strings, and the whole of that difference is this answer.
 *
 * <p>Not {@link Carrier}, which answers whether a position has an order-preserving count. The two
 * questions come apart at both ends: a boolean has no order and has two values, a string has an
 * order and has no end of them. They are asked apart here as well as answered apart — nothing below
 * reads that one — so a change to which types carry an order leaves this where it was.
 *
 * <p>Exhaustive over the primitives, so one added to the language is answered here rather than
 * falling to whichever arm this happened to end with. Where the values cannot be counted out the
 * answer is nothing at all, which is what leaves a rule about them saying only what it excludes.
 */
final class ValueUniverse {

    private ValueUniverse() {}

    /**
     * The values of {@code type} in the order the model writes them, or null where they are not
     * something this counts out.
     *
     * <p>Read through whatever names the type wears: a name wrapped round a boolean is two values
     * like the boolean it wraps.
     */
    static List<Value> of(Type type, Symbols symbols, DeclarationKinds kinds,
                          PublishedDeclarations published) {
        Type base = TypeOps.base(type, symbols);
        if (base instanceof Type.Prim prim) {
            return switch (prim) {
                case BOOL -> List.of(Value.truth(false), Value.truth(true));
                // Counted out by nobody here. An `Int` and a `Date` have ends rather than a list of
                // values, and what a rule leaves between two of them is read as an interval; a
                // `String`, a `Decimal` and the rest have no end of values between any two. Both
                // are the same answer to this question, which is about writing the values down.
                case INT, DECIMAL, STRING, DATE, TIME, DATETIME, INSTANT, RAW -> null;
            };
        }
        // An enumeration's cases, in the order the sum declares them. Asked of the sum directly and
        // not through {@link Carrier}: that one answers whether a position has an order-preserving
        // count, which is a different question that happens to hold of the same declarations today.
        // Read through it, a change to which types carry an order would silently change which types
        // have values that can be written out. Both go to `TypeOps` for what an enumeration is, so
        // this is one reading of that and not two.
        if (!(base instanceof Type.Ref(TypeSymbol.AtModule named))
                || !(published.of(named.key()) instanceof DeclarationMeaning.Sum _)
                || !TypeOps.isUnitOnlySum(base, kinds, published)) {
            return null;
        }
        List<Value> values = new ArrayList<>();
        AtomSpace.subjectAtoms(base, published).forEach(each -> values.add(Value.of(each)));
        return values.isEmpty() ? null : List.copyOf(values);
    }
}
