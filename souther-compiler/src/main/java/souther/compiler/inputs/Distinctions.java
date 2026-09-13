package souther.compiler.inputs;

import souther.compiler.ast.Hir;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.NewtypeInners;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The distinctions a position's declarations state, read through every name they are written under.
 *
 * <p>The one reading of what a declaration states. What a position divides into, what a witness of
 * a type varies over, and what values stand for one of its cases were three readings of one
 * declaration, and they disagreed about how far to look through a name.
 *
 * <p>Through the names, because a newtype is the value it wraps (spec §primitives): a
 * {@code data StageN = Stage} is the sum {@code Stage} and divides into its cases. The names come
 * off to say what the position is and go back on to say what stands at it — one fact, read from
 * {@link TypeView} once, rather than each reader deciding again how far to look.
 *
 * <p><b>An empty answer here is this reading finding nothing stated, and never the position having
 * no distinctions.</b> The two read the same and are not the same claim: the second is a statement
 * about the model, and it is not one this is in a position to make — the rules a body writes have
 * not been read, and what is under the position has not been asked. Which of the two it is travels
 * with the answer ({@link ReadingResult}).
 */
public final class Distinctions {

    /**
     * What the position's type states, or nothing where it states no division of its own.
     *
     * <p>Exhaustive over {@link Shape}, with no {@code default}, so that a shape added later stops
     * this compiling rather than arriving as a position this quietly has no evidence about.
     *
     * <p>Nothing about the rules on the position. What its type declares and what its rules leave
     * it able to hold are two facts, and this is the first of them.
     */
    public static List<Case> ofType(TypeView view, Symbols symbols,
                                    PublishedDeclarations published) {
        return switch (view.shape()) {
            // A `Bool` is two values. No other primitive has distinctions to read off its type:
            // what a number's rules leave is a range with edges — everything outside a newtype's
            // invariant is refused at construction (E1903), so there is no class on the other side
            // to cover — and what does divide a number is a threshold, read from a body and not
            // here.
            case Shape.Scalar scalar -> scalar.prim() == Type.Prim.BOOL
                    ? List.of(new Case.Truth(true), new Case.Truth(false)) : List.of();
            case Shape.Sum sum -> casesOf(Type.ref(sum.name()), symbols, published);
            case Shape.Cases cases -> casesOf(Type.union(cases.members()), symbols, published);
            case Shape.Optional _ ->
                    List.of(new Case.Presence(false), new Case.Presence(true));
            // Shapes whose types state no division of their own. A record is made of positions and
            // a collection holds its values inside something — what that comes to is the structural
            // reading's answer, not this one's — and a unit data has one value, which no
            // distinction of this reading's tells from another.
            case Shape.Product _, Shape.Unit _, Shape.Sequence _, Shape.Mapping _,
                 Shape.Tuple _, Shape.Function _,
            // And the five that are not value shapes: nothing here was interpreted, so there is
            // nothing to read distinctions off. Which of them it was is said by the caller, from
            // the same shape.
                 Shape.Uninhabited _, Shape.Bottom _, Shape.Erroneous _, Shape.Undecided _,
                 Shape.Unresolved _ -> List.of();
        };
    }

    /** Whether this reading could be made at all, or what stopped it. A type nothing could be read
     *  off is not a position with no distinctions: nothing was read to say so. */
    static BlockReason.AboutThePosition unreadableAt(TypeView view) {
        return view.shape() instanceof Shape.Unresolved ? new BlockReason.TypeUnresolved() : null;
    }

    /** A sum's cases as distinctions, folded to their leaves and in the order they are declared —
     *  which is the order a report names them in. */
    private static List<Case> casesOf(Type sum, Symbols symbols,
                                      PublishedDeclarations published) {
        List<Case> out = new ArrayList<>();
        for (TypeSymbol leaf : AtomSpace.subjectAtoms(sum, published)) {
            out.add(new Case.SumCase(leaf, oneValue(leaf, symbols)));
        }
        return List.copyOf(out);
    }

    /**
     * Whether a case is the whole of one value.
     *
     * <p>A unit data is: naming it builds it, so the case and the value are the same thing and a
     * rule denying that value denies the whole case. A case holding a record or wrapping one has no
     * end of values and says nothing, which is what leaves a denial unable to prove it empty.
     */
    private static boolean oneValue(TypeSymbol leaf, Symbols symbols) {
        return !(symbols.declaredNode(leaf) instanceof Hir.Data);
    }

    /**
     * The distinctions the rules give a position whose type states none.
     *
     * <p>The same division a sum states, written another way. {@code data Gender = A | B} and
     * {@code data Gender = String invariant value == "A" || value == "B"} divide their position
     * exactly alike — every value of it is one or the other, and a row sits in one of them — and the
     * second was read by nothing, so a report said the model draws no distinction there.
     *
     * <p>One distinction per value and no complement. Everything outside what an invariant admits
     * is refused at construction (E1903), so there is no class on the other side to cover — the
     * same restraint a bounded newtype gets, where the bound is a line and not a pair of classes.
     *
     * <p>Only where the values are named. {@link ValueSet.Cofinite} says which values are refused
     * and names none of the ones left, so it divides nothing here.
     *
     * <p>All of them or none. A value this cannot place at the position is the rule naming
     * something the position does not hold, and a division this can only half describe is one it
     * does not make.
     */
    static List<Case> ofValues(ValueSet admitted, Type type, NewtypeInners inners) {
        if (!(admitted instanceof ValueSet.Finite finite) || finite.values().isEmpty()) {
            return List.of();
        }
        List<Case> out = new ArrayList<>();
        for (Value value : finite.values()) {
            if (!standsAt(value, type, inners)) {
                return List.of();
            }
            out.add(new Case.Named(value));
        }
        return List.copyOf(out);
    }

    /**
     * Whether a value a rule named is one this position holds.
     *
     * <p>A case of an enumeration is not one of these: what tells two cases apart is which
     * declaration each is, and the reading that names the cases of a position is the one that reads
     * its type — so a case arriving here is a distinction the declared reading has already made,
     * and a second one for it would be the same distinction twice.
     *
     * <p>A number an {@code Int} cannot hold is not one either. A rule naming one admits nothing,
     * which is a refusal of the declaration rather than a distinction here.
     */
    private static boolean standsAt(Value value, Type type, NewtypeInners inners) {
        return switch (value) {
            case Value.Text _, Value.Truth _ -> true;
            case Value.Number number -> TypeOps.numericBase(type, inners) == Type.DECIMAL
                    || whole(number.value());
            case Value.Case _ -> false;
        };
    }

    private static boolean whole(java.math.BigDecimal value) {
        try {
            value.longValueExact();
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private Distinctions() {}
}
