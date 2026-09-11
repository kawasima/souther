package souther.compiler.codegen;

import souther.compiler.types.ValueName;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.InvariantStatement;
import souther.compiler.check.InvariantStatements;
import souther.compiler.check.StatedComparison;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.core.Kernel;
import souther.compiler.numeric.EndSide;
import souther.compiler.types.Type;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Maps a newtype's invariant onto Raoh's decoder constraints (issue #83), so a violation reported by
 * a derived decoder carries the code and metadata of the rule it broke — {@code too_short} with
 * {@code min}, {@code invalid_format} with {@code pattern} — instead of one {@code
 * invariant_violation} for every invariant in the model. The failure itself is Raoh's: the code, the
 * metadata, the default message and the path all come from the constraint, so a
 * {@code MessageResolver} keyed on the standard codes works unchanged.
 *
 * <p>Only exact equivalences are mapped. A constraint weaker than the invariant would be caught by
 * {@code __construct}, which still runs; a constraint stronger than it would reject values the
 * domain accepts, and would do so at the boundary where it reads as bad input. Anything this cannot
 * prove equivalent is left to the emitter's fallback.
 *
 * <p><b>Read off what a statement states, never off the tree it was written as.</b> One rule written
 * out, reached through a helper and written as the denial of its opposite is one statement
 * ({@link InvariantStatement}), and what a decoder reports is public boundary behaviour — so a
 * mapping that turned on the spelling would hand two callers two different codes for one rule of the
 * model. The bindings a helper left and the denial an author wrote are spent where the statement is
 * made, and what arrives here is a claim about two values in that order.
 */
public final class InvariantConstraints {

    /** The name a newtype's single field carries, and so the name its invariant reads it by. */
    private static final String VALUE = "value";

    public sealed interface Constraint {}

    /** A {@code StringDecoder} constraint. */
    public sealed interface OfString extends Constraint {}

    public record MinLength(int n) implements OfString {}

    public record MaxLength(int n) implements OfString {}

    public record FixedLength(int n) implements OfString {}

    public record Pattern(String regex) implements OfString {}

    /** A {@code LongDecoder} constraint — Souther's {@code Int} is carried as a long. */
    public sealed interface OfInt extends Constraint {}

    public record Min(long n) implements OfInt {}

    public record Max(long n) implements OfInt {}

    public record Positive() implements OfInt {}

    public record NonNegative() implements OfInt {}

    /** A {@code DecimalDecoder} constraint. */
    public sealed interface OfDecimal extends Constraint {}

    public record DecimalMin(BigDecimal n) implements OfDecimal {}

    public record DecimalMax(BigDecimal n) implements OfDecimal {}

    public record DecimalPositive() implements OfDecimal {}

    public record DecimalNonNegative() implements OfDecimal {}

    /** A {@code ListDecoder} constraint — a newtype over a {@code List}, whose decoder Raoh answers
     * typed until something untyped is chained onto it. */
    public sealed interface OfList extends Constraint {}

    /** {@code nonempty()} rather than {@code minSize(1)}: Raoh states emptiness on its own, and says so
     * in the message. */
    public record NonEmpty() implements OfList {}

    public record MinSize(int n) implements OfList {}

    public record MaxSize(int n) implements OfList {}

    public record FixedSize(int n) implements OfList {}

    /** {@code unique()}: no element appears twice, compared by value as Souther compares. */
    public record Unique() implements OfList {}

    /** A {@code RecordDecoder} constraint — a newtype over a {@code Map}, which crosses the boundary as
     * an object and is decoded as a record of its values. */
    public sealed interface OfMap extends Constraint {}

    public record MapMinSize(int n) implements OfMap {}

    public record MapMaxSize(int n) implements OfMap {}

    /** The symbols this reads clauses against. Which operations state a constraint is a fact about
     *  the library the clause was resolved against, so it is held here rather than asked at each
     *  call. */
    private final Symbols symbols;
    /** Where a term's text is worked out, which is the reading that made these statements. Asked of
     *  it rather than folded here, so a pattern composed of what a module's own value holds is read
     *  as the pattern it is. */
    private final InvariantStatements read;

    private InvariantConstraints(Symbols symbols, InvariantStatements read) {
        this.symbols = symbols;
        this.read = read;
    }

    /** Reading statements the reading {@code read} made, against the library {@code symbols} names. */
    public static InvariantConstraints against(Symbols symbols, InvariantStatements read) {
        return new InvariantConstraints(symbols, read);
    }

    /**
     * What one side of a comparison is of the value a constraint would be about, or null where it is
     * about something else.
     *
     * <p>Which side that is, is the reading's to spend ({@link StatedComparison#at}), so this says
     * only what a side is and never which of them bore it.
     */
    private enum Measured {

        /** The value itself. */
        VALUE,
        /** How many characters it has. */
        STRING_LENGTH,
        /** How many elements it has. */
        LIST_LENGTH,
        /** How many entries it has. */
        MAP_SIZE
    }

    /**
     * The Raoh constraint equivalent to {@code statement} on a newtype whose value is {@code base},
     * or empty when this cannot prove one.
     */
    public Optional<Constraint> of(InvariantStatement statement, Type base) {
        return switch (statement) {
            case InvariantStatement.Unread _ -> Optional.empty();
            case InvariantStatement.Applies it -> ofCall(it.call(), base);
            case InvariantStatement.Compares it -> ofComparison(it.states(), base);
        };
    }

    private Optional<Constraint> ofComparison(StatedComparison states, Type base) {
        // `0 <= value` says what `value >= 0` says, and which side bore the value is spent here:
        // what comes back is a claim about the value and what it is held against, in that order.
        StatedComparison.Numbered<Measured> bound = states.at(this::measured);
        if (bound == null) {
            return Optional.empty();
        }
        ComparisonClaim placed = bound.claim();
        Core against = bound.other();
        if (base == Type.STRING) {
            return bound.number() == Measured.STRING_LENGTH
                    ? ofStringLength(placed, against) : Optional.empty();
        }
        if (base == Type.INT) {
            return bound.number() == Measured.VALUE ? ofInt(placed, against) : Optional.empty();
        }
        if (base == Type.DECIMAL) {
            return bound.number() == Measured.VALUE ? ofDecimal(placed, against) : Optional.empty();
        }
        if (base instanceof Type.ListOf) {
            return bound.number() == Measured.LIST_LENGTH
                    ? ofListSize(placed, against) : Optional.empty();
        }
        if (base instanceof Type.MapOf) {
            return bound.number() == Measured.MAP_SIZE
                    ? ofMapSize(placed, against) : Optional.empty();
        }
        return Optional.empty();
    }

    /** What {@code e} is of the newtype's value, or null where it is about something else. */
    private Measured measured(Core e) {
        if (isValue(e)) {
            return Measured.VALUE;
        }
        if (!(e instanceof Core.PreservedCall call) || call.args().size() != 1
                || !isValue(call.args().get(0))) {
            return null;
        }
        if (applies(call, Kernel.STRING_LENGTH)) {
            return Measured.STRING_LENGTH;
        }
        if (applies(call, Kernel.LIST_LENGTH)) {
            return Measured.LIST_LENGTH;
        }
        return applies(call, Kernel.MAP_SIZE) ? Measured.MAP_SIZE : null;
    }

    /**
     * The bound at {@code end} that admits what a bound placed at {@code n} admits, or null where
     * there is none to name.
     *
     * <p>A length, a size and an {@code Int} are whole numbers, so a bound that refuses the number
     * it names admits exactly what the next one along admits — and Raoh's constraints are inclusive,
     * so that is the one to hand it. At either end of what the constraint can hold there is no next
     * number, and the clause keeps the check it already has.
     */
    private static Long inclusiveAt(EndSide end, boolean holdsAtTheValue, long n,
                                    long least, long most) {
        if (holdsAtTheValue) {
            return n;
        }
        if (end == EndSide.LOWER) {
            return n == most ? null : n + 1;
        }
        return n == least ? null : n - 1;
    }

    /** Which end of the values a comparison bounds: the side it is satisfied on is where its
     *  values run from. */
    private static EndSide endOf(ComparisonClaim.Cut cut) {
        return EndSide.facing(cut.satisfyingSide());
    }

    /**
     * A bound on how many elements a list has: {@code List.length(value) >= 1} is Raoh's
     * {@code nonempty()}, {@code >= 3} its {@code minSize(3)}, and so on. A size is a whole number, so a
     * strict bound is the adjacent inclusive one — read the same way a string's length is.
     *
     * <p>A {@code Set} has no entry of its own here. Souther decodes one as a list and drops the
     * duplicates while mapping it (spec §collections), so a constraint chained after that mapping is no
     * longer on a typed decoder, and one chained before it would count the duplicates.
     */
    private static Optional<Constraint> ofListSize(ComparisonClaim placed, Core against) {
        Integer n = sizeBound(against);
        if (n == null) {
            return Optional.empty();
        }
        return switch (placed) {
            case ComparisonClaim.Singled singled ->
                    singled.holdsAtTheValue() ? Optional.of(new FixedSize(n)) : Optional.empty();
            case ComparisonClaim.Cut cut -> {
                EndSide end = endOf(cut);
                Long at = inclusiveAt(end, cut.holdsAtTheValue(), n, 0, Integer.MAX_VALUE);
                yield at == null ? Optional.empty()
                        : Optional.of(end == EndSide.LOWER
                                ? at == 1 ? new NonEmpty() : new MinSize(at.intValue())
                                : new MaxSize(at.intValue()));
            }
        };
    }

    /** The same for a map, which Raoh decodes as a record of its values and bounds by entry count.
     * There is no emptiness constraint of its own there, so {@code >= 1} is a minimum of one. */
    private static Optional<Constraint> ofMapSize(ComparisonClaim placed, Core against) {
        Integer n = sizeBound(against);
        if (n == null || !(placed instanceof ComparisonClaim.Cut cut)) {
            return Optional.empty();
        }
        EndSide end = endOf(cut);
        Long at = inclusiveAt(end, cut.holdsAtTheValue(), n, 0, Integer.MAX_VALUE);
        return at == null ? Optional.empty()
                : Optional.of(end == EndSide.LOWER
                        ? new MapMinSize(at.intValue()) : new MapMaxSize(at.intValue()));
    }

    /** The literal bound a size is held against, or null when it is held against something else. */
    private static Integer sizeBound(Core against) {
        Long bound = intLiteral(against);
        if (bound == null || bound < 0 || bound > Integer.MAX_VALUE) {
            return null;
        }
        return bound.intValue();
    }

    private Optional<Constraint> ofCall(Core.PreservedCall call, Type base) {
        // `String.matches(p, value)` is whole-string anchored (Strings.matches), and so is Raoh's
        // pattern (Matcher.matches), so the two accept the same strings. The regex is asked for the
        // same way the check asks — one reading of which expressions are compile-time strings and of
        // what one composes to, so a pattern the check accepted cannot arrive here unrecognised and
        // lose its constraint. It has been compiled once at check time, so it is known well-formed.
        if (base == Type.STRING && applies(call, Kernel.STRING_MATCHES) && call.args().size() == 2
                && isValue(call.args().get(1))) {
            return Optional.ofNullable(read.textOf(call.args().get(0))).map(Pattern::new);
        }
        // `List.allDistinctBy(x -> x, value)` says of the elements what Raoh's `unique()` says of them:
        // no two are equal, by the same value equality (spec §collections, ADR-0009). A projection that
        // is not the identity says it of something else — the elements' products, their ids — and Raoh
        // has no constraint for that, so the clause keeps its own check.
        if (base instanceof Type.ListOf && statesDistinctness(call)
                && call.args().size() == 2 && isValue(call.args().get(1))
                && isIdentity(call.args().get(0))) {
            return Optional.of(new Unique());
        }
        return Optional.empty();
    }

    private static Optional<Constraint> ofStringLength(ComparisonClaim placed, Core against) {
        Integer bound = sizeBound(against);
        if (bound == null) {
            return Optional.empty();
        }
        int n = bound;
        return switch (placed) {
            case ComparisonClaim.Singled singled ->
                    singled.holdsAtTheValue() ? Optional.of(new FixedLength(n)) : Optional.empty();
            case ComparisonClaim.Cut cut -> {
                EndSide end = endOf(cut);
                Long at = inclusiveAt(end, cut.holdsAtTheValue(), n, 0, Integer.MAX_VALUE);
                yield at == null ? Optional.empty()
                        : Optional.of(end == EndSide.LOWER
                                ? new MinLength(at.intValue()) : new MaxLength(at.intValue()));
            }
        };
    }

    private static Optional<Constraint> ofInt(ComparisonClaim placed, Core against) {
        Long bound = intLiteral(against);
        if (bound == null || !(placed instanceof ComparisonClaim.Cut cut)) {
            return Optional.empty();
        }
        long n = bound;
        EndSide end = endOf(cut);
        // Raoh has a name for each of the two bounds at nought, and they are two names rather than
        // one: a bound refusing nought is `positive()` where the same bound moved to one is a
        // minimum of one, and both are emitted. So which of them a rule comes to is read before the
        // bound is moved — unlike a list, where the bound at one and the bound past nought are the
        // one constraint and moving first says so.
        if (end == EndSide.LOWER && n == 0) {
            return Optional.of(cut.holdsAtTheValue() ? new NonNegative() : new Positive());
        }
        Long at = inclusiveAt(end, cut.holdsAtTheValue(), n, Long.MIN_VALUE, Long.MAX_VALUE);
        return at == null ? Optional.empty()
                : Optional.of(end == EndSide.LOWER ? new Min(at) : new Max(at));
    }

    private static Optional<Constraint> ofDecimal(ComparisonClaim placed, Core against) {
        BigDecimal bound = decimalLiteral(against);
        if (bound == null || !(placed instanceof ComparisonClaim.Cut cut)) {
            return Optional.empty();
        }
        boolean zero = bound.signum() == 0;
        // A Decimal has no next value, so a bound refusing the number it names is no inclusive
        // bound at all — except at nought, which Raoh states directly as positive().
        if (!cut.holdsAtTheValue()) {
            return endOf(cut) == EndSide.LOWER && zero
                    ? Optional.of(new DecimalPositive()) : Optional.empty();
        }
        return Optional.of(endOf(cut) == EndSide.LOWER
                ? zero ? new DecimalNonNegative() : new DecimalMin(bound)
                : new DecimalMax(bound));
    }

    private static boolean isValue(Core e) {
        return e instanceof Core.Read read && read.name().equals(VALUE);
    }

    /**
     * Whether {@code call} applies {@code kernel} — asked of what the name reaches, which is what a
     * library operation is filed under whether or not an import let it be written bare, and then of
     * which kernel the library declares that operation to be.
     *
     * <p>The kernel and not the alias. What makes a length comparison a size constraint is that the
     * call computes a length; the alias it is published under is the library's, and a clause
     * recognised by one would go on being recognised for exactly as long as the two agreed.
     *
     * <p>A call applying a name nothing declares, or one that is no kernel, applies no operation
     * this recognises. There is no constraint to map it to, and the clause keeps the check it
     * already has.
     */
    private boolean applies(Core.PreservedCall call, Kernel kernel) {
        return call.operation() instanceof ValueName.Stdlib.Operation operation
                && symbols.kernelOf(operation) == kernel;
    }

    /** Whether {@code call} states that the elements are distinct — the library's own predicate for
     *  it, which has a Souther body rather than a kernel and so is a value the library hands over
     *  ({@link Symbols#theDistinctnessPredicate}). */
    private boolean statesDistinctness(Core.PreservedCall call) {
        return symbols.theDistinctnessPredicate().equals(call.operation());
    }

    /** Whether a projection hands back what it was given — {@code x -> x}, however the parameter is
     * spelled. A block with one parameter is how a lambda arrives here (spec §blocks). The body reads
     * the parameter when it reads that binding; a name spelled like it, bound elsewhere, is another
     * value. */
    private static boolean isIdentity(Core e) {
        return e instanceof Core.Block block && block.params().size() == 1
                && block.body() instanceof Core.Read read
                && read.binding().equals(block.params().get(0).binding());
    }

    /** An Int literal, negation included ({@code -1}), or null when the operand is not one. */
    private static Long intLiteral(Core e) {
        if (e instanceof Core.Int lit) {
            return lit.value();
        }
        if (e instanceof Core.Neg neg && neg.operand() instanceof Core.Int lit
                && lit.value() != Long.MIN_VALUE) {
            return -lit.value();
        }
        return null;
    }

    /** A Decimal literal; an Int literal counts, since a bare literal takes the other side's type. */
    private static BigDecimal decimalLiteral(Core e) {
        if (e instanceof Core.Decimal lit) {
            return lit.value();
        }
        if (e instanceof Core.Neg neg && neg.operand() instanceof Core.Decimal lit) {
            return lit.value().negate();
        }
        Long asInt = intLiteral(e);
        return asInt == null ? null : BigDecimal.valueOf(asInt);
    }
}
