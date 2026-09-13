package souther.compiler.partition;

import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a value a rule named is recognised in a row and written back.
 *
 * <p>Which values those are, and whether they stand at the position at all, is the reading's
 * ({@code Distinctions#ofValues}). What is here is the other half: a class asking whether an
 * observation is that value, and a fixture writing one.
 */
final class ValueClasses {

    /**
     * One named value's class.
     *
     * <p>The reading has already settled that the value stands at this position, so a value this
     * cannot write is the two of them disagreeing rather than a division to give up on — said
     * loudly, because a class quietly missing here is a distinction the model states going missing
     * from every measure at once.
     *
     * @param view the position as it is written, so that a value is classified under the names it
     *             wears and written back under them
     */
    static PartitionClass classAt(Value value, TypeView view, List<TypeSymbol> worn,
                                  RuleReadingSource ruleSource) {
        FixtureTemplate bare = written(value, view.shape());
        if (bare == null) {
            throw new IllegalStateException(
                    "the reading of `" + Type.show(view.declared()) + "` states a distinction at a"
                            + " value this cannot write; the two readings of one position disagree"
                            + " about what stands at it");
        }
        Recognition is = Recognition.Under.of(worn,
                new Recognition.AtAValue(value,
                        placeOf(value, view.declared(), ruleSource.inners(), ruleSource.symbols(),
                                ruleSource.kinds(), ruleSource.published())));
        FixtureTemplate stands = WornNames.under(view.wrappers(), bare, ruleSource);
        return (stands == null
                // A name the position wears that nothing here writes. The class is the position's
                // either way and a row already sitting in it still covers it; what is absent is the
                // offer of a new row (issue #696).
                ? PartitionClass.ungeneratable(bare.text(), bare.text(), is,
                        "nothing here can write a value of this position")
                : PartitionClass.of(bare.text(), bare.text(), is,
                        RepresentativeSource.of(stands)))
                // The one value it was made from, which is the whole of what it holds.
                .holding(ValueSet.just(value));
    }

    /**
     * Where the value sits on the order of what stands at the position, or null where nothing places
     * it there.
     *
     * <p>Made here because this is where both halves are: the value the rules named, and the type
     * whose order it has to be placed on. A class that carried the value alone would leave every
     * reader holding a place to place it, each reaching for whichever carrier it had — and a day
     * count compared against a model's own number is what a carrier is for stopping.
     *
     * <p>Null wherever the carrier cannot place what the value is: a type with no order at all, and
     * a value written in a shape that order does not hold. Both are answers about this class rather
     * than reasons to stop building it — the class is still what a row is read against.
     */
    private static souther.compiler.numeric.Place placeOf(Value value, Type type,
                                                          souther.compiler.check.NewtypeInners
                                                                  inners,
                                                          Symbols symbols,
                                                          DeclarationKinds kinds,
                                                          PublishedDeclarations published) {
        return placeOf(switch (value) {
            case Value.Text text -> new ObservedValue.Text(text.value());
            case Value.Truth truth -> new ObservedValue.Bool(truth.value());
            case Value.Number number -> new ObservedValue.Decimal(number.value());
            case Value.Case one -> new ObservedValue.Unit(one.data());
        }, type, inners, symbols, kinds, published);
    }

    /**
     * Where {@code value} sits on the order a position of {@code type} is counted on, or null where
     * the type has no order or the value is not on it.
     *
     * <p>The one place a class is given its place, for every kind of class that has one. Asked here
     * with the position's type rather than with a carrier, so that no builder of a class picks the
     * order: which order a case of an enumeration is placed on is the enumeration's, and a unit data
     * that is a case of two sums is at a different place in each.
     */
    static souther.compiler.numeric.Place placeOf(ObservedValue value, Type type,
                                                  souther.compiler.check.NewtypeInners inners,
                                                  Symbols symbols,
                                                  DeclarationKinds kinds,
                                                  PublishedDeclarations published) {
        souther.compiler.check.Carrier carrier =
                souther.compiler.check.Carrier.ofValue(type, inners, symbols, kinds, published);
        return carrier == null ? null : carrier.placeOf(value);
    }

    /**
     * The value as a row writes it, or null where this does not write values of that kind.
     *
     * <p>Which of {@code Int} and {@code Decimal} a number is written as is what the position is
     * with its names off, which the reading of it already says. The two are never compared with each
     * other (E1319), so a position is written about in one of them and never in both.
     */
    private static FixtureTemplate written(Value value, Shape shape) {
        return switch (value) {
            case Value.Text text -> FixtureTemplate.string(text.value());
            case Value.Truth truth -> FixtureTemplate.bool(truth.value());
            case Value.Number number ->
                    shape instanceof Shape.Scalar scalar && scalar.prim() == Type.Prim.DECIMAL
                            ? FixtureTemplate.decimal(number.value())
                            : whole(number.value());
            case Value.Case _ -> null;
        };
    }

    /** A whole number, or null where the number the rules name is not one. */
    private static FixtureTemplate whole(BigDecimal value) {
        try {
            return FixtureTemplate.integer(value.longValueExact());
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private ValueClasses() {}
}
