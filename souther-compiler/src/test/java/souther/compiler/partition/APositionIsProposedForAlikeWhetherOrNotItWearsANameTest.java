package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A position and the same position under a name are proposed for alike.
 *
 * <p>One route. The position is read, and the value is chosen from the shape that reading came to —
 * so what a name does is decide how the value is spelled and nothing else. A {@code Bool} divides
 * into two and an {@code Option} into present and absent whether or not a name is worn over it, and
 * which of those the position stands for is settled before any name goes back on.
 *
 * <p>Written down before the routes were joined, when there were two. A position whose type was
 * written as a primitive was answered from what the primitive is, and one wearing a name was asked
 * what it divides into first — so the two chose the value by different readings and nothing said
 * they agreed. They did, and pinning that is what made joining them a change that keeps the answers
 * rather than one that quietly picks a different row.
 */
class APositionIsProposedForAlikeWhetherOrNotItWearsANameTest {

    private static final RuleReadingSource RULES = RuleReadings.ofSource("""
            module demo

            data F = Bool

            data O = Option<String>
            """);

    private static List<String> proposedFor(Type type) {
        return Partitions.representativesOf(type,
                        RuleReadingContext.unshared(RULES, ReadAs.THE_COMPILATION_DOES),
                        null, Set.of())
                .stream().map(FixtureTemplate::text).toList();
    }

    private static Type named(String type) {
        return Type.ref(TypeSymbols.declared(new TypeKey(RULES.symbols().module(), type)));
    }

    /** A flag, which the type divides into two: the same one of them either way. */
    @Test
    void aFlagIsProposedForAlikeUnderANameAndWithoutOne() {
        assertEquals(List.of("true"), proposedFor(Type.BOOL));
        assertEquals(List.of("F(true)"), proposedFor(named("F")));
    }

    /** An option, which divides into absent and present: the same one of them either way. */
    @Test
    void anOptionIsProposedForAlikeUnderANameAndWithoutOne() {
        assertEquals(List.of("None"), proposedFor(Type.option(Type.STRING)));
        assertEquals(List.of("O(None)"), proposedFor(named("O")));
    }
}
