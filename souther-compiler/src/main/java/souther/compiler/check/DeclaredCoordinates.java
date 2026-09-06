package souther.compiler.check;

import souther.compiler.types.Type;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules written on a type, read once, as the two different things a reader of its coordinates
 * asks of them.
 *
 * <p>Which number a position is measured at, and what became of each rule once that was settled,
 * are two questions over one reading. Asked of two readings they are two answers about the same
 * clauses, free to part the day an allowance or a policy moves either of them — and what the two
 * would then disagree about is whether a rule that chose the coordinate is a rule anything happened
 * to. So the reading is made here and both projections come off it.
 *
 * <p><b>The two are not one another.</b> {@link Reading#writtenAbout()} is what the rules are
 * <em>about</em>, which a rule placing no end is as much a part of as one that orders the values:
 * {@code invariant String.length(value) /= 0} says the length is a number of this model whatever a
 * range could be made of it. {@link Reading#placed()} is where a rule put an end, which is what a
 * reader has to name when it says the end went unused. A caller turning the first into the second
 * would report a rule that placed nothing as a rule whose line was dropped.
 */
public final class DeclaredCoordinates {

    /**
     * One reading of one type's own rules.
     *
     * @param writtenAbout which numbers of the value the rules are written about, whatever each of
     *                     them came to. A set and not a choice: two numbers of one value can both
     *                     be written about, and the reader that has to choose is the one that knows
     *                     what it does where there is no choice to make
     * @param placed       the ends the rules put on the value's own coordinates, each with the
     *                     clause that put it there
     */
    public record Reading(Set<NumberAt.OfWhatNumber> writtenAbout,
                          List<FieldDomains.Placed> placed) {

        public Reading {
            writtenAbout = Set.copyOf(writtenAbout);
            placed = List.copyOf(placed);
        }
    }

    /**
     * What is written about a value of {@code type} itself.
     *
     * <p>The value's own coordinates and nothing under them. A rule of this type about a field of
     * what it wraps is a rule about that field's position, and reading it here would answer a
     * question about one place with a rule written about another.
     *
     * <p><b>The type's own rules and no others.</b> A rule reaching the value from the record
     * holding it states an end on a coordinate; it does not say which coordinate the position is,
     * and letting it say so takes an axis away — a {@code Name} measured on its own order, held in
     * a record that bounds the length of it, would stop being measured on the order its own clause
     * is about. So this is opened at the type, where its own clauses are the value's and everything
     * else is somewhere else.
     *
     * <p>Off the canonical quantity, and so off the reading that computes one. Which number a rule
     * is about is what its arithmetic came to, and {@code String.length(value) * 2 >= 4} has a bare
     * name on neither side — recognised from the spelling, such a rule is about nothing, and a
     * position whose one rule is that came back measured on the string's own order.
     */
    public static Reading of(Type type, RuleReadingSource source, ReadingPolicy policy) {
        return of(type, source, policy, StringMachineLookup.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the declaration's
     *  string rules before building any of it. */
    public static Reading of(Type type, RuleReadingSource source, ReadingPolicy policy,
                             StringMachineLookup machines) {
        FieldDomains domains = Rules.of(type, source, policy, machines).bounds();
        Set<NumberAt.OfWhatNumber> about = new LinkedHashSet<>();
        for (NumberAt<RuleKey> each : domains.writtenAbout()) {
            if (each.position().isTheValueItself()) {
                about.add(each.of());
            }
        }
        return new Reading(Collections.unmodifiableSet(about),
                domains.placedAt(RuleKey.THE_VALUE));
    }

    private DeclaredCoordinates() {}
}
