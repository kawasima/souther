package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternRead;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Where a set's strings stop is walked once in a revision, however many readings meet the set.
 *
 * <p>The walk is handed the set and nothing else, and what it may spend is an allowance minted for
 * that set alone — so the answer is a fact about the set, and a reading that takes one another
 * reading walked came to what it would have come to on its own. Which is what lets it be shared at
 * all: a declaration reaching a string rule that ten others also reach is not ten walks.
 *
 * <p><b>Including the walks that ran out.</b> A set whose extent costs more than the allowance
 * comes back saying so, and that is settled by the set like everything else here, because the
 * allowance is the one every set is walked under. So the sets that cost the most are the ones this
 * saves most on, and a knowledge that kept only the answers it liked would walk those again for
 * every reading that meets them.
 */
class AnExtentIsWalkedOnceHoweverManyReadingsMeetTheSetTest {

    /** A set with an extent to find, which two readings meet. */
    private static final ValueSet ADMITS_A_RUN =
            ValueSet.matching(languageOf("JP[\\s\\S]*"));

    /**
     * A set whose extent costs more than a walk may spend: the words are more letters between them
     * than one machine may have states.
     */
    private static final ValueSet COSTS_TOO_MUCH = manyLongWords();

    @Test
    void twoReadingsSharingWhatTheRevisionKnowsWalkARunOnce() {
        KnownExtents known = aRevisionsKnowledge();
        long walked = StringMachineAnswers.extentsWalked();

        TextExtent first = StringMachineAnswers.unborrowed(known).extentOf(ADMITS_A_RUN);
        TextExtent second = StringMachineAnswers.unborrowed(known).extentOf(ADMITS_A_RUN);

        assertInstanceOf(TextExtent.One.class, first, "the set names a run");
        assertEquals(first, second, "and the second reading is answered with it");
        assertEquals(1, StringMachineAnswers.extentsWalked() - walked,
                "walked for the first reading and by neither of them again");
    }

    @Test
    void twoReadingsSharingWhatTheRevisionKnowsWalkAnUnaffordableSetOnce() {
        KnownExtents known = aRevisionsKnowledge();
        long walked = StringMachineAnswers.extentsWalked();

        TextExtent first = StringMachineAnswers.unborrowed(known).extentOf(COSTS_TOO_MUCH);
        TextExtent second = StringMachineAnswers.unborrowed(known).extentOf(COSTS_TOO_MUCH);

        assertEquals(new TextExtent.NotBuilt(Meter.Stopped.ONE_MACHINE), first,
                "the words are more than one machine may hold, and that is what stopped it");
        assertEquals(first, second, "and the second reading is told the same");
        assertEquals(1, StringMachineAnswers.extentsWalked() - walked,
                "a walk that ran out is knowledge like any other");
    }

    @Test
    void twoReadingsWithNothingSharedWalkARunEach() {
        long walked = StringMachineAnswers.extentsWalked();

        StringMachineAnswers.unborrowed(KnownExtents.NONE).extentOf(ADMITS_A_RUN);
        StringMachineAnswers.unborrowed(KnownExtents.NONE).extentOf(ADMITS_A_RUN);

        assertEquals(2, StringMachineAnswers.extentsWalked() - walked,
                "nothing to answer from, so each reading walks it");
    }

    @Test
    void twoReadingsWithNothingSharedWalkAnUnaffordableSetEach() {
        long walked = StringMachineAnswers.extentsWalked();

        StringMachineAnswers.unborrowed(KnownExtents.NONE).extentOf(COSTS_TOO_MUCH);
        StringMachineAnswers.unborrowed(KnownExtents.NONE).extentOf(COSTS_TOO_MUCH);

        assertEquals(2, StringMachineAnswers.extentsWalked() - walked,
                "the expensive ones are the ones walked again");
    }

    /**
     * A string rule two declarations reach, compiled: where its strings stop is walked once for the
     * whole compile, and a second declaration reaching it walks none of it.
     *
     * <p>What this holds is the wiring rather than the mechanism above. Every declaration reaching
     * a rule is a reading of it, and each of those readings asks where the sets at its own
     * positions stop — so the rule's set is met once per declaration that reaches it, and the
     * declaration whose rule it is meets it as well. What makes that one walk is that all of them
     * are made under the revision and are handed what it knows.
     */
    @Test
    void aSecondDeclarationReachingOneRuleWalksNothingMore() {
        long alone = walksWhileCompiling("""
                module demo exposing ( Left )

                data Code = String
                    invariant String.matches("[A-Z]{2}-[0-9]{4}", value)

                data Left = { code: Code }
                """);
        long both = walksWhileCompiling("""
                module demo exposing ( Left, Right )

                data Code = String
                    invariant String.matches("[A-Z]{2}-[0-9]{4}", value)

                data Left = { code: Code }

                data Right = { code: Code }
                """);

        assertEquals(1, alone,
                "one string rule is one set, and the compile walks where its strings stop once");
        assertEquals(alone, both,
                "and the second declaration reaching it walks none of it");
    }

    /** How many extents were walked while {@code source} was compiled and answered. */
    private static long walksWhileCompiling(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        long walked = StringMachineAnswers.extentsWalked();
        compilation.answerEverything();
        return StringMachineAnswers.extentsWalked() - walked;
    }

    /** What a revision knows, as the store keeps it: answered by the set and by nothing else. */
    private static KnownExtents aRevisionsKnowledge() {
        Map<ValueSet, TextExtent> known = new HashMap<>();
        return new KnownExtents() {

            @Override
            public TextExtent of(ValueSet set) {
                return known.get(set);
            }

            @Override
            public void remember(ValueSet set, TextExtent extent) {
                known.put(set, extent);
            }
        };
    }

    /** The strings {@code pattern} accepts. */
    private static Language languageOf(String pattern) {
        PatternRead read = PatternParser.read(pattern);
        assertInstanceOf(PatternRead.Read.class, read, pattern + " is read");
        Language made = PatternPlan.of(((PatternRead.Read) read).syntax())
                .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter());
        assertNotNull(made, pattern + " compiles");
        return made;
    }

    /** More letters written out than one machine may have states. */
    private static ValueSet manyLongWords() {
        Set<Value> words = new LinkedHashSet<>();
        for (int each = 0; each < 700; each++) {
            words.add(new Value.Text(each + "-".repeat(100)));
        }
        return new ValueSet.Finite(words);
    }
}
