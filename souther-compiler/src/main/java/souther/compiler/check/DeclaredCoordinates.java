package souther.compiler.check;

import souther.compiler.types.Type;

import java.util.List;

/**
 * Where the rules written on a type put an end on the value it is a type of.
 *
 * <p>The ends and not what the rules are about. Which numbers a value has is settled by what the
 * value is — what stands there, and what an operation counts of it where the type declares one that
 * counts its values — so a rule votes on nothing; what it does is place an end on one of them, and
 * that is what a reader has to name when it says where a line came from.
 */
public final class DeclaredCoordinates {

    /**
     * The ends a value of {@code type} has placed on it by its own type's rules.
     *
     * <p>The value's own numbers and nothing under them. A rule of this type about a field of what
     * it wraps is a rule about that field's position, and reading it here would answer a question
     * about one place with a rule written about another.
     *
     * <p>Off the reading that turns clauses into constraints, which is the one place the canonical
     * quantity of each of them was worked out. A reader recognising the number off the spelling of
     * a side answers nothing for {@code String.length(value) * 2 >= 4}, whose sides are neither a
     * name nor a measure of one.
     */
    public static List<FieldDomains.Placed> placedOnItsOwnValue(
            Type type, RuleReadingSource source, ReadingPolicy policy) {
        return placedOnItsOwnValue(type, source, policy, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the declaration's
     *  string rules before building any of it. */
    public static List<FieldDomains.Placed> placedOnItsOwnValue(
            Type type, RuleReadingSource source, ReadingPolicy policy,
            DeclarationReadings machines) {
        return Rules.of(type, source, policy, machines).bounds().placedAt(RuleKey.THE_VALUE);
    }

    private DeclaredCoordinates() {}
}
