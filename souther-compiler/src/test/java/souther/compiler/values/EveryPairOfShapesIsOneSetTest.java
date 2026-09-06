package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.Language;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every pair of shapes a set of values comes in is one set, and says what it holds.
 *
 * <p>Three shapes make nine ways to meet and nine to join, and what each of them says is a claim
 * about which values are in the answer. So each is asked that way — over a sample of values, against
 * what the two sides say about each of them.
 *
 * <p>Which is what a shape added has to be held to. A shape whose operations are exact against the
 * two that were here already and guessed at against itself is one whose meet is not a meet, and the
 * answer is a set either way — so nothing downstream would find out.
 *
 * <p>Asked of {@link Allowance}, since that is where two of these are put together. Every pair below is
 * within what one answer is allowed, so nothing here is given up on and what is read is the
 * algebra.
 */
class EveryPairOfShapesIsOneSetTest {

    private static final Sameness.Block<String> POSITION = Sameness.Block.of("value");

    /** What puts the sets together, and what it is allowed to build doing it. */
    private final Allowance<String> sets = AsACompilationAllows.forAdmittedValues();

    private static Language language(String regex) {
        PatternRead read = PatternParser.read(regex);
        Language made = PatternPlan.of(assertInstanceOf(PatternRead.Read.class, read, regex)
                .syntax()).compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter());
        assertNotNull(made, regex);
        return made;
    }

    private static Value text(String value) {
        return Value.text(value);
    }

    /** The sets asked about, one of each shape and more than one of some. */
    private static List<ValueSet> shapes() {
        return List.of(
                ValueSet.NONE,
                ValueSet.ANY,
                ValueSet.just(text("a")),
                ValueSet.oneOf(Set.of(text("a"), text("ab"), text("00"))),
                ValueSet.allBut(text("a")),
                ValueSet.allBut(text("zz")),
                ValueSet.matching(language("[a-z]+")),
                ValueSet.matching(language("[a-z]{2}")),
                ValueSet.matching(language("[0-9]+")),
                ValueSet.matching(language("a|ab")));
    }

    /** The values asked about. */
    private static final List<String> VALUES = List.of(
            "", "a", "b", "ab", "abc", "zz", "0", "00", "0a", "A");

    /** Whether {@code set} holds {@code value}, asked of the set rather than of its shape. */
    private static boolean holds(ValueSet set, String value) {
        return set.has(text(value));
    }

    /** What both hold, which is always built here: every pair is within the allowance. */
    private ValueSet met(ValueSet one, ValueSet two) {
        Allowance.Composed made = sets.meet(POSITION, one, two);
        assertFalse(made.gaveUp(), () -> one + " meet " + two + " is within what one answer holds");
        return made.set();
    }

    /** And what either holds, on the same terms — said as one plan over the alternatives, which is
     *  how a block holds what its alternatives leave it. */
    private ValueSet joined(ValueSet one, ValueSet two) {
        Allowance.Composed made = sets.joining(POSITION, List.of(one, two));
        assertFalse(made.gaveUp(), () -> one + " join " + two + " is within what one answer holds");
        return made.set();
    }

    /** A meet holds what both hold, whichever shapes they are. */
    @Test
    void aMeetHoldsWhatBothHold() {
        List<String> apart = new ArrayList<>();
        for (ValueSet one : shapes()) {
            for (ValueSet two : shapes()) {
                ValueSet met = met(one, two);
                for (String value : VALUES) {
                    boolean said = holds(met, value);
                    boolean both = holds(one, value) && holds(two, value);
                    if (said != both) {
                        apart.add(one + " meet " + two + " over \"" + value + "\": "
                                + said + " where both say " + both);
                    }
                }
            }
        }
        assertEquals(List.of(), apart);
    }

    /** And a join holds what either holds. */
    @Test
    void aJoinHoldsWhatEitherHolds() {
        List<String> apart = new ArrayList<>();
        for (ValueSet one : shapes()) {
            for (ValueSet two : shapes()) {
                ValueSet made = joined(one, two);
                for (String value : VALUES) {
                    boolean said = holds(made, value);
                    boolean either = holds(one, value) || holds(two, value);
                    if (said != either) {
                        apart.add(one + " join " + two + " over \"" + value + "\": "
                                + said + " where either says " + either);
                    }
                }
            }
        }
        assertEquals(List.of(), apart);
    }

    /**
     * Emptiness has one shape, and a language holding nothing takes it.
     *
     * <p>Which is the invariant this whole file already had: a set admitting nothing is a finite one
     * with nothing in it, and every reading that could reach it went through values it had in hand.
     * A pattern nothing satisfies is not a fourth way of holding nothing.
     */
    @Test
    void aLanguageHoldingNothingIsTheEmptySet() {
        ValueSet nothing = met(ValueSet.matching(language("[a-z]")), ValueSet.just(text("0")));

        assertEquals(ValueSet.NONE, nothing);
        assertTrue(nothing.isEmpty());
        assertInstanceOf(ValueSet.Finite.class, nothing);
    }

    /**
     * And everything has one shape, asked of the strings rather than of the pattern.
     *
     * <p>A pattern accepting every string there is admits every value, whatever it was written as.
     * Read off the shape, a set written as a pattern would answer that something was said here — and
     * what a rule saying nothing leaves is what every other rule saying nothing leaves.
     */
    @Test
    void aLanguageHoldingEverythingIsAny() {
        ValueSet all = ValueSet.matching(language("[\\x{0}-\\x{10FFFF}]*"));

        assertEquals(ValueSet.ANY, all);
        assertTrue(all.isAny());

        assertFalse(ValueSet.matching(language(".*")).isAny(),
                "a dot leaves out the line terminators, so a string holding one is missing");
    }

    /** A pattern is a set of strings, so two patterns accepting the same strings are one set. */
    @Test
    void twoPatternsAcceptingTheSameStringsAreOneSet() {
        assertEquals(ValueSet.matching(language("a|b")), ValueSet.matching(language("[ab]")));
        assertEquals(ValueSet.matching(language("(?:ab)+")),
                ValueSet.matching(language("ab(?:ab)*")));

        Set<ValueSet> once = new LinkedHashSet<>();
        once.add(ValueSet.matching(language("a|b")));
        once.add(ValueSet.matching(language("[ab]")));
        assertEquals(1, once.size(), "one set is held once");
    }

    /** A value that is not a string is not one of a language's, whatever the language. */
    @Test
    void whatIsNotAStringIsNotOneOfThem() {
        ValueSet met = met(ValueSet.matching(language("[0-9]+")),
                ValueSet.oneOf(Set.of(text("1"), Value.number(new java.math.BigDecimal(1)))));

        assertEquals(ValueSet.just(text("1")), met);
    }
}
