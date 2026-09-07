package souther.compiler.regex;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A complement turns over the states a walk stops at, and what it leaves is still the one machine.
 *
 * <p>Two things and they fail apart. A machine a walk is only ever in one state of, where every
 * symbol leads somewhere, has a complement that is the same table with the other states stopped at
 * — so nothing is made deterministic and nothing is walked, and what a caller spends is the states
 * it already had. And the table being the same table is what makes the answer canonical without
 * being made so: what settles the one machine is read off the steps and the walk that numbers them,
 * and neither of those asks where a walk may stop.
 *
 * <p>Which is why the second is asked of the machine and not of a language made out of it. A
 * complement that came back short of canonical still holds the right strings, so every question
 * answered by walking agrees; and where a complement holds sequences no string is read as, taking
 * those out makes the answer canonical on the way and nothing downstream can tell either. The
 * question is about the machine the complement hands back, and it is asked there.
 */
class TheComplementOfTheOneMachineIsTheOneMachineTest {

    private static Meter roomy() {
        return new Meter(100_000, 10_000_000);
    }

    private static Automaton canonicalMachineOf(String regex, Meter meter) {
        PatternSyntax syntax =
                assertInstanceOf(PatternRead.Read.class, PatternParser.read(regex), regex).syntax();
        Automaton made = Automaton.of(syntax, meter);
        assertNotNull(made, regex);
        Automaton one = made.canonical(meter);
        assertNotNull(one, regex);
        return one;
    }

    private static Language language(String regex, Meter meter) {
        PatternSyntax syntax =
                assertInstanceOf(PatternRead.Read.class, PatternParser.read(regex), regex).syntax();
        Language made = PatternPlan.of(syntax).compile(meter);
        assertNotNull(made, regex);
        return made;
    }

    /**
     * What a complement of the one machine costs is nothing.
     *
     * <p>A meter counts the states a construction makes, and this one makes none: the steps are the
     * steps that were there and so is what each is numbered, and the answer to where a walk may
     * stop is what is built. Made deterministic first, it would be the subsets and then a machine
     * written out of them — the same states discovered a second time and charged for both.
     */
    @Test
    void turningTheStatesOverCostsNothing() {
        Meter meter = roomy();
        Automaton one = canonicalMachineOf("[ab]+", meter);
        int before = meter.left();

        Automaton turned = one.not(meter);

        assertNotNull(turned);
        assertEquals(one.size(), turned.size(), "the same states");
        assertEquals(before, meter.left(), "and none of them was made, so none was charged");
    }

    /**
     * And it is made where there is nothing left to make a state with.
     *
     * <p>Which is the half a reader has to be able to see. A construction that shares what it was
     * handed is not one an allowance has anything to say about, and a complement asked for with
     * nothing left is answered — where charging it for the states it did not make would refuse an
     * answer that costs this compiler nothing.
     */
    @Test
    void aComplementIsMadeWithNothingLeftToMakeAStateWith() {
        // As much in all as one machine may hold, so that what is left can be taken in one ask.
        Meter meter = new Meter(100_000, 100_000);
        Automaton one = canonicalMachineOf("[ab]+", meter);
        // Everything the meter had, so that a state asked for now is refused.
        assertTrue(meter.making().states(meter.left()), "the allowance is spent down to nothing");
        assertEquals(0, meter.left());

        Automaton turned = one.not(meter);

        assertNotNull(turned, "the complement is still answered");
        assertEquals(one.size(), turned.size());
    }

    /**
     * And what it hands back is the one machine for those strings already.
     *
     * <p>Asked of the complement itself and not of anything built out of it. Made canonical, it
     * comes to itself — same states, same steps, same numbering — which is what lets the caller
     * that knows its machine is canonical keep the answer without paying for that walk.
     */
    @Test
    void aComplementOfTheOneMachineNeedsNoMaking() {
        Meter meter = roomy();
        Automaton one = canonicalMachineOf("[ab]+", meter);

        Automaton turned = one.not(meter);
        Automaton again = turned == null ? null : turned.canonical(meter);

        assertNotNull(turned);
        assertNotNull(again);
        assertTrue(turned.sameAs(again), "the complement is the one machine for what it accepts");
    }

    /**
     * And the complement of a language is the language the same strings come to by another road.
     *
     * <p>The two are built without a step in common past the pattern: one turns a canonical machine
     * over, the other meets every string with the complement of a machine written out of the words
     * themselves, which is not deterministic to begin with. Equal means the tables are equal, state
     * for state, which is what a language being the one machine for its strings says.
     */
    @Test
    void aComplementIsTheSameMachineAsTheOneTheStringsComeToOtherwise() {
        Meter meter = roomy();

        Language turned = Language.ofWords(List.of("a"), meter).not(meter);
        Language met = Language.EVERY_STRING.without(List.of("a"), meter);

        assertNotNull(turned);
        assertNotNull(met);
        assertEquals(met, turned, "one set of strings is one machine, however it was reached");
        assertEquals(met.hashCode(), turned.hashCode(), "which is what a map looking one up asks");
    }

    /** And the strings themselves are the ones the complement should hold. */
    @Test
    void aComplementHoldsTheStringsTheLanguageDoesNot() {
        Meter meter = roomy();

        Language left = language("[ab]+", meter).not(meter);

        assertNotNull(left);
        assertTrue(left.has(""), "no letters at all is not one or more of them");
        assertTrue(left.has("c"), "nor is a letter the pattern leaves out");
        assertTrue(left.has("abc"), "nor is a word holding one");
        assertEquals(false, left.has("ab"), "and a word the language holds is not left over");
    }

    /** Turned over twice, a language is itself — table for table, not merely string for string. */
    @Test
    void aLanguageTurnedOverTwiceIsTheOneItWas() {
        Meter meter = roomy();
        Language held = language("[ab]+", meter);

        Language back = held.not(meter).not(meter);

        assertNotNull(back);
        assertEquals(held, back, "the same machine and not only the same strings");
    }
}
