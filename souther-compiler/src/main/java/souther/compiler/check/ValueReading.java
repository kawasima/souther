package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the model writes where a value stands, read one step at a time.
 *
 * <p>The one step every reading of a declaration takes. Which declarations state something of every
 * value here, what is readable on such a value, and what stands below that no reading here takes in
 * are three facts about a type, and every reader wants some of them. Each working them out from the
 * declaration is how a sum comes to be a value that states nothing to one reader, a value with a
 * readable field to a second, and a class with an accessor to a third.
 *
 * <p><b>Two readings, because a name worn over a value and a value with its names off are not the
 * same thing to read.</b> {@link UnderAName} is one nominal layer: what the name wraps is read at
 * the name the declaration gives it. {@link AtAValue} is the value underneath every such name, whose
 * readable surface is {@link ReadableFields}' answer and whose cases are handed on. Held as one
 * record with a flag saying which it was, the flag was free to say "a name is worn" beside a
 * readable surface no name has.
 *
 * <p><b>What a name is called, never whether reading it goes anywhere.</b> A newtype's {@code value}
 * is read at this same value, and a record's field is read one step in; which of the two a name is
 * is {@link Location#isStep}'s answer, asked by whoever writes a path down. Answered here, a walk
 * would take its step from the reading and the rule would have two statements.
 *
 * <p>Nothing here resolves a name to a declaration to find out what kind of thing it is;
 * {@link TypeView} has done that, and this says what the model writes at each kind. Nothing here
 * reads a clause, a value, or a path either. What a clause states of a value in hand is
 * {@link TypeGuarantees}, which asks this where it starts; how far to walk is {@link GuaranteeWalk}'s.
 */
sealed interface ValueReading {

    /** A declaration whose clauses hold of every value standing here, and the name it was reached
     *  by — which is what a clause of it resolves its fields against. */
    record Owner(TypeSymbol.AtModule named) {}

    /** The declaration standing here, which a walk files what it has entered under and a reader
     *  supposing values names, or null where no declaration stands here. */
    TypeSymbol entering();

    /** The declarations whose clauses hold of every value standing here. One for a record or a
     *  newtype; for a sum, the declarations its cases share — a rule written on one case refuses
     *  values of that case and not every value here. */
    List<Owner> owners();

    /** What is named here and what stands at each name, in the order the declaration writes it. */
    Map<String, Type> named();

    /** What stands under here that no reading of this takes in — the cases of a sum, what a
     *  container holds. */
    List<Type> handedOn();

    /**
     * One name worn over a value.
     *
     * <p>One name at a time. What the layer states is stated of this very value, and what the value
     * under it states is read where a walk reaches it.
     *
     * @param worn  the declaration the name is written by
     * @param named what that declaration declares, which for the newtype form is its {@code value}
     */
    record UnderAName(TypeSymbol worn, List<Owner> owners, Map<String, Type> named)
            implements ValueReading {

        public UnderAName {
            owners = List.copyOf(owners);
            named = Map.copyOf(named);
        }

        @Override
        public TypeSymbol entering() {
            return worn;
        }

        /** Nothing. A name is worn over one value, and that value is read where the name comes
         *  off. */
        @Override
        public List<Type> handedOn() {
            return List.of();
        }
    }

    /**
     * A value with every name it wears taken off.
     *
     * @param readable what a field access may write here, as the one answer to that question
     */
    record AtAValue(TypeSymbol entering, List<Owner> owners, ReadableFields readable,
                    List<Type> handedOn) implements ValueReading {

        public AtAValue {
            owners = List.copyOf(owners);
            handedOn = List.copyOf(handedOn);
        }

        @Override
        public Map<String, Type> named() {
            return readable.declaredFields();
        }
    }

    /** What the model writes where a value of {@code type} stands. */
    static ValueReading of(Type type, NewtypeInners inners, Symbols symbols,
                           PublishedDeclarations published) {
        TypeView view = TypeView.of(type, inners, symbols, published);
        if (view.isWrapped() && symbols.declaredNode(view.wrappers().getFirst())
                instanceof Hir.Data worn) {
            // The outermost name is the reading's, and what is readable under it is written on that
            // name's own declaration — so the name comes from the reading and the body is fetched
            // here, where reading a declaration is the question. Walked again for the body instead,
            // this would decide how far a newtype reaches a second time in a method that has
            // already been told.
            TypeSymbol name = view.wrappers().getFirst();
            return new UnderAName(name, owning(name, symbols),
                    TypeOps.fieldTypes(worn, symbols));
        }
        // What a field access may write here is one question with one owner, asked once for every
        // shape. What is left for the switch is which declarations state something of every value
        // here and what stands below that this does not take in.
        ReadableFields readable = ReadableFields.of(view.shape());
        return switch (view.shape()) {
            case Shape.Product product ->
                    new AtAValue(product.name(), owning(readable.declaredBy(), symbols), readable,
                            List.of());
            // A sum is a common product times a choice of case. What the cases share is stated of
            // every value standing here and is readable on one; what one case declares is under that
            // case, and a reading of it is opened where a match opens the case.
            case Shape.Sum sum ->
                    new AtAValue(sum.name(), owning(readable.declaredBy(), symbols), readable,
                            cases(sum.name(), published));
            // A unit data holds nothing and may write no rule about it (spec §unit-data), and a
            // primitive is written under no declaration of its own.
            case Shape.Unit unit -> new AtAValue(unit.name(), List.of(), readable, List.of());
            case Shape.Scalar _ -> new AtAValue(null, List.of(), readable, List.of());
            // What a container holds, what an option holds when it holds anything, an unnamed
            // union's members, a tuple's elements. Each is a value a reading of its own is opened
            // at, and none of them is a value standing here.
            case Shape.Cases _, Shape.Sequence _, Shape.Mapping _, Shape.Optional _, Shape.Tuple _,
                 Shape.Function _ -> new AtAValue(null, List.of(), readable, held(type));
            // Nothing was interpreted, so nothing is written here and nothing is under it.
            case Shape.Unresolved _, Shape.Uninhabited _, Shape.Bottom _, Shape.Erroneous _,
                 Shape.Undecided _ -> new AtAValue(null, List.of(), readable, List.of());
        };
    }

    private static List<Owner> owning(TypeSymbol name, Symbols symbols) {
        return owning(List.of(name), symbols);
    }

    /** The declarations these names denote, leaving out any that denotes none. */
    private static List<Owner> owning(List<TypeSymbol> names, Symbols symbols) {
        List<Owner> out = new ArrayList<>();
        for (TypeSymbol name : names) {
            Owner owner = TypeOps.writingFields(name, symbols);
            if (owner != null) {
                out.add(owner);
            }
        }
        return out;
    }

    /** A sum's cases, as the one closure over them answers. */
    private static List<Type> cases(TypeSymbol sum, PublishedDeclarations published) {
        List<Type> out = new ArrayList<>();
        for (TypeSymbol leaf : AtomSpace.subjectAtoms(Type.ref(sum), published)) {
            out.add(Type.ref(leaf));
        }
        return out;
    }

    /** The types a compound holds. */
    private static List<Type> held(Type type) {
        List<Type> out = new ArrayList<>();
        Type.forEachChild(type, out::add);
        return out;
    }
}
