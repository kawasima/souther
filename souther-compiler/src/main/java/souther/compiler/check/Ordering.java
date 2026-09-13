package souther.compiler.check;

import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

/**
 * How the values of a type are ordered: whether they are ordered at all, and by what.
 *
 * <p>These were two questions with one answer between them. Whether a type is ordered was a
 * {@code boolean}, and how to compare its values was worked out again at each of the four places
 * that emit a comparison — the operator, the sort family, the {@code sortBy} key, and the
 * {@code compareTo} a newtype carries. Four derivations of one fact disagree the way three did over
 * whether a {@code Date} is a carrier (see {@link Carrier}), and here the disagreement was silent:
 * {@code data StageN = Stage} was refused by {@code <} while its generated class declared
 * {@code Comparable<StageN>} and threw {@code IncompatibleClassChangeError} on the first Java reader
 * that compared two (issue #856).
 *
 * <p><b>Built from the spine, not from the base.</b> {@link TypeOps#base} answers what is left when
 * the names are off and drops how it got there, and a reader that then asks the next question of the
 * type as written is the defect this closes — it is what {@code BinaryElaborator} and the
 * {@code ORDERING} row of the old capability table both did, on the line after they computed the
 * base. So this reads {@link TypeOps#newtypeSpine} once and keeps both halves of its answer: the
 * terminal says what the order is, and the layers say whether a name is worn over it. Nothing here
 * asks a second time whether something was a newtype.
 *
 * <p><b>Sealed, so an order added is one every reader has to answer for.</b> The switches over these
 * four are what makes a fifth a build failure rather than a comparison emitted as an equality test.
 *
 * <p>The rules are ADR-0047 (a single-value newtype is compared by the value it wraps) and ADR-0069
 * (an enumeration is ordered by the order its cases are declared in, and that order lives on the sum
 * because one unit data may be a case of two sums). Composing them is what makes a newtype over an
 * enumeration ordered: {@code Ordered(Newtype<T>) = Ordered(T)} and {@code Ordered(Enumeration)},
 * so {@code Ordered(Newtype<Enumeration>)}.
 */
public sealed interface Ordering {

    /** A JVM {@code long}. {@code Int}, and nothing else. */
    record Longs() implements Ordering {}

    /**
     * The JVM value is {@link Comparable} and its {@code compareTo} is the order: a {@code String},
     * a {@code BigDecimal}, a {@code LocalDate}, a {@code LocalTime}, a {@code LocalDateTime} or an
     * {@code Instant} — and a single-value newtype as it is held, which carries a {@code compareTo}
     * of its own (ADR-0047).
     */
    record Natural() implements Ordering {}

    /**
     * A value of an enumeration: the sum answers where a case stands in its declaration, through the
     * {@code __order} / {@code __ordering} it carries. The value itself is not {@code Comparable},
     * because one unit data may be a case of two sums that place it differently (ADR-0069).
     *
     * @param enumeration the sum whose declaration order this counts in
     */
    record Places(TypeSymbol enumeration) implements Ordering {}

    /**
     * A single-value newtype, which is two orders depending on what is on the stack: itself as the
     * JVM holds it, and {@code inner} once it has been opened to the value it wraps. Use
     * {@link #asHeld()} and {@link #opened()} rather than reading this apart.
     *
     * <p>Never nested. The spine walk goes to the terminal in one pass, so {@code Manager = Level =
     * Int} is one {@code Wrapped(Longs)} and not two.
     */
    record Wrapped(Ordering inner) implements Ordering {

        public Wrapped {
            if (inner instanceof Wrapped) {
                throw new IllegalArgumentException(
                        "the spine walk reaches the terminal in one pass, so a wrapped order is never wrapped again");
            }
        }
    }

    Ordering LONGS = new Longs();
    Ordering NATURAL = new Natural();

    /**
     * How a value of this type, as the JVM holds it, is ordered — or null where it has no order.
     *
     * <p>Whether a type is ordered is this answer existing, which is what {@link
     * TypeOps#supportsOrdering} reports. Asking here and reporting there is one question and not
     * two: a reader that admits a value it cannot emit a comparison for is what #856 was.
     */
    static Ordering of(Type type, NewtypeInners inners, Symbols symbols, DeclarationKinds kinds,
                       PublishedDeclarations published) {
        TypeOps.NewtypeSpine spine = TypeOps.newtypeSpine(type, inners);
        Ordering terminal = ofTerminal(spine.terminal(), symbols, kinds, published);
        if (terminal == null) {
            return null;
        }
        return spine.layers().isEmpty() ? terminal : new Wrapped(terminal);
    }

    /**
     * How a comparison of two operands is emitted, once each has been opened to the value it wraps.
     *
     * <p><b>This does not decide whether the operands may be compared.</b> That is {@code
     * BinaryElaborator.orderedComparable}, and it is asked of the types as written, because the
     * nominal boundary is the type and not its base: two different newtypes over one enumeration
     * open to the same order and are still not comparable (ADR-0047). Asked here of the opened
     * types, this answers for a pair that rule has already admitted.
     */
    static Ordering ofComparison(Type lt, Type rt, NewtypeInners inners, Symbols symbols,
                                 DeclarationKinds kinds,
                                 PublishedDeclarations published) {
        Type lb = TypeOps.base(lt, inners);
        Type rb = TypeOps.base(rt, inners);
        // A case value, a union of cases and the sum itself are all comparable on the sum's order
        // without ranging over it, and either side may be the one that names the sum — so the
        // enumeration is read off the pair rather than off one operand.
        TypeSymbol enumeration = TypeOps.comparisonEnumeration(lb, rb, symbols, kinds, published);
        if (enumeration != null) {
            return new Places(enumeration);
        }
        // Otherwise both operands open to one type, which the admissibility rule established and
        // this states rather than assumes: every route that admits a pair short of the enumeration
        // one leaves them with equal bases. Answering off the left alone would give an order for a
        // pair that has none — and the backend's "a comparison the checker admitted has no order"
        // is only an assertion about the checker while nothing here can fail.
        return lb.equals(rb) ? ofTerminal(lb, symbols, kinds, published) : null;
    }

    /** How a value still held as the type it was asked of is ordered: a newtype by the {@code
     *  compareTo} its own class carries, everything else by itself. What the sort family reads,
     *  since it hands the value to the runtime as it stands. */
    default Ordering asHeld() {
        return this instanceof Wrapped ? NATURAL : this;
    }

    /** How a value is ordered once the newtype spine has been opened to its terminal value. What the
     *  operator reads, since it opens each operand before comparing. */
    default Ordering opened() {
        return this instanceof Wrapped w ? w.inner() : this;
    }

    /** The order of a type with no newtype name left on it. Every constructor is answered, so a type
     *  constructor added to {@link Type} stops compiling until it says whether it has an order. */
    private static Ordering ofTerminal(Type terminal, Symbols symbols, DeclarationKinds kinds,
                                       PublishedDeclarations published) {
        return switch (terminal) {
            case Type.Prim p -> switch (p) {
                case INT -> LONGS;
                // The JVM carries each of these as Comparable, which is why they are the ordered
                // ones (spec §primitives).
                case STRING, DECIMAL, DATE, TIME, DATETIME, INSTANT -> NATURAL;
                case BOOL, RAW -> null;
            };
            // A sum every one of whose cases is a unit data, one of its cases, or a union of them.
            // Null where more than one enumeration lists the case: the order belongs to the sum, so
            // a value two sums place differently has none of its own, and that is refused rather
            // than guessed (ADR-0069).
            case Type.Ref r -> placesIn(r, symbols, kinds, published);
            case Type.Union u -> placesIn(u, symbols, kinds, published);
            // A collection has no order of its own whatever it holds, a function and a tuple none at
            // all, and a type standing for a type has no values to order.
            case Type.ListOf _, Type.SetOf _, Type.OptionOf _, Type.MapOf _, Type.TupleOf _,
                 Type.FnOf _, Type.Open _, Type.Nothing _, Type.Never _, Type.Erroneous _ -> null;
        };
    }

    private static Ordering placesIn(Type t, Symbols symbols, DeclarationKinds kinds,
                                     PublishedDeclarations published) {
        TypeSymbol enumeration = TypeOps.orderingEnumeration(t, symbols, kinds, published);
        return enumeration == null ? null : new Places(enumeration);
    }
}
