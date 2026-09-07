package souther.compiler.query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A combination of two classes is not a thing a row is owed at.
 *
 * <p>What the pair space counts is how much of the model's own product the rows happen to reach.
 * Nothing asks for a row at a combination and nothing offers one: a row composed for a pair moves
 * two positions at once and says nothing about which of them the answer turned on, which is what
 * issue #967 settled. So a count of combinations no row reaches is not work anybody is behind on,
 * and the report says so beside it.
 *
 * <p><b>This is what makes that sentence true.</b> A report saying nothing is owed on the strength
 * of a formatter's reading would go on saying it the day a finding about a combination was added.
 * What holds it is that no gap this compiler can find is about one: the vocabulary of findings has
 * no arm for a combination, and the offering of rows answers every arm it has.
 *
 * <p>So a change that gave a combination a finding fails here rather than in a formatter, and
 * whoever makes it is asked the question this test is named for. Answering it is allowed — the
 * decision is #967's and may be revisited — but it is a change to what a row is owed at, and this
 * says so out loud.
 */
class PairCombinationsAreNotRowObligationsTest {

    /**
     * Every kind of gap this compiler can find, and none of them is about a combination.
     *
     * <p>Read off the seal rather than off a list kept beside it: an arm added to {@link About}
     * arrives here whether or not anybody remembered this test exists.
     *
     * <p>Held on the names, which is as far as a test can see without deciding for itself what a
     * shape is about. A finding named for a pair, a combination, or two classes together is what
     * this is looking for, and one added under a name that hides it is a change nobody wrote down.
     */
    @Test
    void noKindOfGapIsAboutACombination() {
        List<String> every = new ArrayList<>();
        walk(About.class, every);
        List<String> named = every.stream()
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("pair")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("combination"))
                .toList();

        assertEquals(List.of(), named,
                "a gap about a combination is a row somebody is owed at one, which is what the"
                        + " report says nobody is");
        // And the walk found the kinds. A sweep over a seal that came back with nothing passes
        // this whatever the arms are, which is the one way a check of this shape goes quietly
        // wrong — and a sweep of the arms directly under it misses the ones an arm of its own
        // holds.
        assertEquals(16, every.size(),
                () -> "the kinds of gap this compiler can find, which is what was swept: " + every);
    }

    /** Every kind under {@code from}, however many seals deep it is written. */
    private static void walk(Class<?> from, List<String> into) {
        if (from.isSealed()) {
            for (Class<?> arm : from.getPermittedSubclasses()) {
                walk(arm, into);
            }
            return;
        }
        into.add(from.getSimpleName());
    }

    /**
     * And the pair space carries no finding of its own.
     *
     * <p>The other half. A measure may answer for gaps it did not name — the classes measure finds
     * a class no row is in — so what says a combination is owed nothing is that the space itself
     * hands none over.
     */
    @Test
    void thePairSpaceHandsOverNoFinding() {
        for (java.lang.reflect.RecordComponent part
                : PartitionEvidence.PairSpace.class.getRecordComponents()) {
            assertFalse(Adequacy.Finding.class.isAssignableFrom(part.getType()),
                    () -> "the pair space carries " + part.getName() + ", which is a finding");
        }
    }
}
