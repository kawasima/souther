package souther.compiler.regex;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * However many states a construction made, what comes out has one per thing a string can tell apart.
 *
 * <p>The refinement is the only place that decides it, and both ways of getting it wrong are quiet.
 * Leaving two states apart that no string separates still accepts the right strings, so nothing that
 * walks the machine complains — what it costs is the one machine, since two ways of writing one
 * language would then come to two tables and every reader comparing languages would be comparing
 * spellings. Putting two states together that a string does separate accepts strings the language
 * has not got, and a set that is quietly wider goes on being counted like any other.
 *
 * <p>Both are caught by the same question, which is why it is the one asked here: the words and the
 * pattern for those same words are made into machines by two roads and held against each other,
 * table for table. Either mistake leaves a table one road has and the other does not. Asking the
 * machine about strings is not that question — a grouping that has gathered up the wrong states
 * answers about most strings the way the right one does — so what the strings below are for is that
 * the count of states is a count of the right language's states.
 *
 * <p>Asked of a machine large enough for the grouping to be doing something: every word of a length
 * over two letters, built as a tree of two thousand states with an answer of ten.
 */
class TheStatesNoStringTellsApartAreOneStateTest {

    /** More than either machine here reaches, so that what is measured is the grouping. */
    private static Meter roomy() {
        return new Meter(100_000, 10_000_000);
    }

    /** How long the words are, and so how many of them there are. */
    private static final int LETTERS = 8;

    /** Every string of {@link #LETTERS} letters over {@code a} and {@code b}. */
    private static List<String> words() {
        List<String> words = new ArrayList<>(List.of(""));
        for (int at = 0; at < LETTERS; at++) {
            List<String> longer = new ArrayList<>();
            for (String word : words) {
                longer.add(word + "a");
                longer.add(word + "b");
            }
            words = longer;
        }
        return words;
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

    /**
     * The tree the words are built as, and the ten states a string can tell apart.
     *
     * <p>What a string of these letters can be is how many of them have been read and nothing else,
     * so the answer is one state per count up to the length and one for a string that is already not
     * one of the words. The construction has no way of knowing that: it makes a state per letter of
     * per word, and the subsets after it a state per prefix. Both are held to here, because a
     * grouping asked of a machine that was small to begin with is a grouping that was never asked.
     */
    @Test
    void aTreeOfEveryWordComesToOneStatePerLetterRead() {
        Meter meter = roomy();
        List<String> words = words();

        Automaton tree = Automaton.ofWords(words, meter);
        assertNotNull(tree);
        Automaton one = tree.canonical(meter);
        assertNotNull(one);

        assertEquals(256, words.size(), "every string of eight letters over two of them");
        assertEquals(words.size() * LETTERS + 1, tree.size(), "a state per letter of per word");
        assertTrue(tree.size() > 2000, "which is the many the grouping has to bring down");
        assertEquals(LETTERS + 2, one.size(),
                "one state per letter read, one for read them all, and one for read something else");
    }

    /**
     * And the pattern for those same strings comes to that same machine, table for table.
     *
     * <p>Two roads with nothing in common past what they accept: one is a tree of two hundred and
     * fifty-six words made deterministic, the other is a repetition read straight off the pattern
     * and already deterministic when the grouping sees it. A machine still carrying a state no
     * string tells apart is a machine one of these has and the other does not, and equal tables are
     * what says neither does.
     */
    @Test
    void theWordsAndThePatternForThemAreOneMachine() {
        Meter meter = roomy();

        Automaton fromWords = Automaton.ofWords(words(), meter);
        assertNotNull(fromWords);
        Automaton one = fromWords.canonical(meter);
        assertNotNull(one);
        Automaton other = canonicalMachineOf("[ab]{" + LETTERS + "}", meter);

        assertTrue(one.sameAs(other), "one set of strings is one machine, however it was reached");
    }

    /**
     * And the strings it holds are the words and not a letter more.
     *
     * <p>What makes the ten states above a count of this language's states rather than of some
     * other's. It is not the question the grouping is checked by: a machine that gathered up states
     * a string does tell apart still answers about these the way the right one does.
     */
    @Test
    void itHoldsTheWordsAndNothingBeside() {
        Meter meter = roomy();
        Language held = Language.ofWords(words(), meter);
        assertNotNull(held);

        assertTrue(held.has("a".repeat(LETTERS)), "a word of the right letters and the right length");
        assertTrue(held.has("abababab"), "and another");
        assertEquals(false, held.has("a".repeat(LETTERS - 1)), "one letter short is not a word");
        assertEquals(false, held.has("a".repeat(LETTERS + 1)), "nor is one letter long");
        assertEquals(false, held.has("c".repeat(LETTERS)), "nor is the right length in other letters");
        assertEquals(false, held.has(""), "nor is nothing at all");
    }
}
