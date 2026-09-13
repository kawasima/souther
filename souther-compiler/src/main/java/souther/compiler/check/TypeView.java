package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.List;

/**
 * A position's type, read once: what it is, and what it is written as.
 *
 * <p>The two are not the same question and were being answered by whoever needed them. A
 * {@code data StageN = Stage} is a sum — its values are the sum's cases under a name — and it is
 * written {@code StageN(Prospecting)}. The reader that drew a line on it read through the name; the
 * reader that asked what it divides into stopped at the name; and the position came back as one the
 * model does not divide, which is the opposite of what the declaration says (issue #631).
 *
 * <p>So the interpretation happens here and nowhere else:
 *
 * <ul>
 *   <li>{@link #shape} is what the position <em>is</em>, with every name off. A reader answering
 *       every case of a {@link Shape} has answered every kind of position, which switching on
 *       {@link Type} does not achieve — {@code Type.Ref} is one constructor for four things.
 *   <li>{@link #wrappers} is what the position is <em>written as</em>: the names a value wears,
 *       outermost first. A representative is built by putting them back on, and an observation is
 *       read by taking them off. Both directions stay available because nominality is the point of
 *       a newtype — {@code StageN(Prospecting)} is what the row writes, and {@code Prospecting} is
 *       what classifies it.
 *   <li>{@link #declared} is the type as the signature wrote it, for anything that reports the
 *       position back to an author.
 * </ul>
 *
 * <p><b>A reader does not re-decide any of this.</b> Asking {@code symbols.declaredNode(ref.name())} again
 * to find out whether a name is a newtype is the defect this exists to remove, not a shortcut around
 * it.
 *
 * <p><b>Names, and not the declarations they are written by.</b> What a value wears is which names
 * it is written under; the bodies those names are declared with are what a reader of the rules
 * asks for, and it asks the walk over the declarations
 * ({@link TypeOps#newtypeChain}) rather than this. Handed a declaration here, a consumer holding an
 * interpreted position could read structure back out of it and settle for itself what this had
 * already decided — which is the reading this exists to be the only one of.
 *
 * @param declared the type the signature wrote
 * @param wrappers the newtype names worn over {@link #shape}, outermost first; empty for a type
 *                 written under no name of its own
 * @param shape    what the value is, with those names off
 */
public record TypeView(Type declared, List<TypeSymbol> wrappers, Shape shape) {

    public TypeView {
        wrappers = List.copyOf(wrappers);
    }

    /**
     * How {@code type} is to be read at a position.
     *
     * <p>The names come off first, by the one walk that decides how far a newtype reaches
     * ({@link TypeOps#newtypeSpine}), so this cannot disagree with the carrier or the range about
     * where a value's base is.
     */
    public static TypeView of(Type type, NewtypeInners inners, Symbols symbols,
                              PublishedDeclarations published) {
        TypeOps.NewtypeSpine spine = TypeOps.newtypeSpine(type, inners);
        return new TypeView(type, spine.layers().stream().map(TypeOps.Layer::named).toList(),
                shapeOf(spine.terminal(), symbols, published));
    }

    /**
     * The same, for a reader that holds the declarations rather than the compilation's answer.
     *
     * <p>What a position is, is read off the declarations either way — the shape under the names
     * comes from them. What differs is how far the names come off: asked of the compilation, that
     * answer is kept when a declaration only moves, and read off the tree it is worked out again.
     * Every caller of this is a reader that has not been handed the first, and a test counts them.
     */
    public static TypeView asWritten(Type type, Symbols symbols, PublishedDeclarations published) {
        return of(type, NewtypeInners.asWritten(symbols), symbols, published);
    }

    /** Whether any name is worn over the shape — which is a fact about how the value is written,
     *  never about what it is. */
    public boolean isWrapped() {
        return !wrappers.isEmpty();
    }

    /**
     * What {@code terminal} is. Exhaustive over {@link Type}, and over the declarations a
     * {@link Type.Ref} can denote — no {@code default} in either, so a constructor added to
     * {@code Type} or a declaration form added to {@code Hir} stops compiling here until it has
     * been said what kind of position it is.
     *
     * <p>{@code terminal} has already had its newtype names taken off. A {@code Ref} that is still a
     * newtype here is one whose {@code value} the walk could not reach, which is a name that
     * resolved to nothing usable rather than a shape.
     */
    private static Shape shapeOf(Type terminal, Symbols symbols,
                                 PublishedDeclarations published) {
        return switch (terminal) {
            case Type.Prim prim -> new Shape.Scalar(prim);
            case Type.Never _ -> new Shape.Uninhabited();
            case Type.Nothing _ -> new Shape.Bottom();
            case Type.Erroneous _ -> new Shape.Erroneous();
            case Type.Var _, Type.MetaVar _ -> new Shape.Undecided();
            case Type.Union union -> new Shape.Cases(union.members());
            case Type.ListOf list -> new Shape.Sequence(Shape.Sequence.Kind.LIST, list.element());
            case Type.SetOf set -> new Shape.Sequence(Shape.Sequence.Kind.SET, set.element());
            case Type.MapOf map -> new Shape.Mapping(map.key(), map.value());
            case Type.OptionOf option -> new Shape.Optional(option.element());
            case Type.TupleOf tuple -> new Shape.Tuple(tuple.elements());
            case Type.FnOf fn -> new Shape.Function(fn.params(), fn.result());
            case Type.Ref ref -> denoted(ref.name(), symbols, published);
        };
    }

    /**
     * What a name stands for. {@code Hir.Def} permits three declarations and all three are answered
     * here, so a fourth stops compiling rather than arriving as a name that denotes nothing.
     *
     * <p>A newtype arriving here is one the spine stopped on — its {@code value} was not declared,
     * so there is no base to read a shape from.
     */
    private static Shape denoted(TypeSymbol name, Symbols symbols,
                                 PublishedDeclarations published) {
        return switch (symbols.declaredNode(name)) {
            case Hir.SumData sum ->
                    new Shape.Sum(name, TypeOps.commonSpreadOf(sum, symbols, published));
            case Hir.UnitData _ -> new Shape.Unit(name);
            case Hir.Data data when data.newtype() -> new Shape.Unresolved(name);
            case Hir.Data data -> new Shape.Product(name, TypeOps.fieldTypes(data, symbols));
            case null -> new Shape.Unresolved(name);
        };
    }
}
