package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.EndSide;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading with rules taken away comes to is decided by which rules those are.
 *
 * <p>Not by the name the question was asked at. Which declarations hold an end is answered by
 * leaving a declaration's clauses out and reading again, and a declaration writing about two of a
 * record's names is asked the same counterfactuals at each of them — the same clauses left out, the
 * same reading, twice. So the readings are kept under what was left out, and the second name is
 * answered out of what the first made.
 *
 * <p>The names the two questions come to are compared as well as the readings they took. A question
 * answered out of nothing takes no reading either, and the count alone would not tell that apart
 * from a question answered out of the readings already made.
 */
class ACounterfactualIsReadOncePerSetOfRulesLeftOutTest {

    /**
     * Two declarations write a floor under each of two names, and neither holds one on its own:
     * {@code Common} puts both ten above {@code lo} and {@code Held} puts both five above, so ten
     * above is where each of them stops. Attributing that end asks for the reading without both
     * candidates and for the reading without each of them, which is the same three readings at
     * {@code hi} as at {@code also}.
     */
    private static final String SOURCE = """
            module demo exposing ( Held, keep )

            data Common =
                { lo: Int
                , hi: Int
                , also: Int
                }
                invariant based = lo >= 100
                invariant far = hi >= lo + 10
                invariant alsoFar = also >= lo + 10

            data Held = { ...Common }
                invariant near = hi >= lo + 5
                invariant alsoNear = also >= lo + 5

            behavior keep : (h: Held) -> Held

            let keep (h) = h
            """;

    @Test
    void aSecondNameIsAnsweredOutOfTheReadingsTheFirstMade() {
        FieldDomains reading = reading();
        NarrowedBounds hi = reading.at(RuleKey.of("hi"));
        NarrowedBounds also = reading.at(RuleKey.of("also"));

        long beforeTheFirst = InvariantChecker.readingsMade();
        List<TypeSymbol.AtModule> atHi = holding(hi);
        assertTrue(InvariantChecker.readingsMade() > beforeTheFirst,
                "the first name is answered by reading the declaration again without some of it");

        long beforeTheSecond = InvariantChecker.readingsMade();
        assertEquals(atHi, holding(also),
                "the same declarations hold the floor under the other name");
        assertEquals(beforeTheSecond, InvariantChecker.readingsMade(),
                "and the same clauses left out is the same reading, made once");
    }

    /**
     * A number with an end either way, and a conjunct about it that places neither.
     *
     * <p>{@code hole} places no end, so what the conjuncts about {@code n} were holding is asked by
     * leaving conjuncts out rather than read off the ends they placed. Both ends are asked, and
     * they ask it of the same conjuncts: the reading without all three, and the reading without
     * each one of them.
     */
    private static final String BOTH_ENDS = """
            module demo exposing ( Bounded, keep )

            data Bounded = { n: Int }
                invariant floor = n >= 0
                invariant hole = n /= 0
                invariant ceiling = n <= 100

            behavior keep : (b: Bounded) -> Bounded

            let keep (b) = b
            """;

    @Test
    void theSecondEndOfANumberLeavesTheSameConjunctsOutAsTheFirst() {
        FieldDomains reading = reading(BOTH_ENDS, "Bounded");

        long before = InvariantChecker.readingsMade();
        assertFalse(reading.movedEnds().isEmpty(),
                "the conjuncts about `n` are attributed by leaving them out");
        assertEquals(4, InvariantChecker.readingsMade() - before,
                "four sets are left out over the two ends — all three conjuncts, and each of them"
                        + " alone — and the second end asks for the ones the first already made");
    }

    /** The floor of a coordinate is held by whoever the reading says, asked with that floor. */
    private static List<TypeSymbol.AtModule> holding(NarrowedBounds narrowed) {
        return AReadingOfAPosition.holding(narrowed, EndSide.LOWER);
    }

    private static FieldDomains reading() {
        return reading(SOURCE, "Held");
    }

    private static FieldDomains reading(String source, String declaration) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        TypeSymbol.AtModule of = TypeSymbols.declared(new TypeKey("demo", declaration));
        return FieldDomains.of(of,
                RuleReadings.of(compilation, compilation.modules().get(0)),
                ReadAs.THE_COMPILATION_DOES);
    }
}
