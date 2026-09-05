package souther.compiler.check;

import souther.compiler.types.Type;

import java.util.Set;

/**
 * Which numbers of a value the rules written on its type are about.
 *
 * <p>{@link DeclaredBounds}' question one step back. That one answers where the rules leave the
 * value, which every rule that places no end is absent from; this one answers what the rules are
 * written about, which a rule placing no end is as much a part of as one that orders the values.
 * A reader choosing which number a position is measured on wants the second: whether some clause
 * happened to place an end is a fact about the clauses beside it and about this compiler's
 * arithmetic, and neither decides what a position is.
 *
 * <p><b>Off the canonical quantity, and so off the reading that computes one.</b> Which number a
 * rule is about is what its arithmetic came to, and {@code String.length(value) * 2 >= 4} has a
 * bare name on neither side — recognised from the spelling, such a rule is about nothing, and a
 * position whose one rule is that came back measured on the string's own order. So the question is
 * put to the reading of the declaration, which worked the quantity of every conjunct out.
 *
 * <p><b>The type's own rules and no others.</b> A rule reaching the value from the record holding
 * it states an end on a coordinate; it does not say which coordinate the position is, and letting
 * it say so takes an axis away — a {@code Name} measured on its own order, held in a record that
 * bounds the length of it, would stop being measured on the order its own clause is about. So this
 * is opened at the type, where its own clauses are the value's and everything else is somewhere
 * else.
 *
 * <p>A set and not a choice. Two numbers of one value can both be written about, which is a model
 * with nothing here to pick between — said as a set, the reader that has to choose is the one that
 * knows what it does where there is no choice to make.
 *
 * <p>This question alone, for a caller that has only this to ask. The reading it comes off answers
 * more than this ({@link DeclaredCoordinates}), and a caller wanting both takes the reading rather
 * than this and a second one beside it: what the two would part about is whether a rule that chose
 * a coordinate is a rule anything happened to.
 */
public final class DeclaredSubjects {

    /** The numbers the rules on {@code type} and on the types it wraps are written about. */
    public static Set<NumberAt.OfWhatNumber> of(Type type, RuleReadingSource source,
                                                ReadingPolicy policy) {
        return DeclaredCoordinates.of(type, source, policy).writtenAbout();
    }

    private DeclaredSubjects() {}
}
