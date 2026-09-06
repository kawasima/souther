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

    /** The floor of a coordinate is held by whoever the reading says, asked with that floor. */
    private static List<TypeSymbol.AtModule> holding(NarrowedBounds narrowed) {
        return AReadingOfAPosition.holding(narrowed, EndSide.LOWER);
    }

    private static FieldDomains reading() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        TypeSymbol.AtModule held = TypeSymbols.declared(new TypeKey("demo", "Held"));
        return FieldDomains.of(held,
                RuleReadings.of(compilation, compilation.modules().get(0)),
                ReadAs.THE_COMPILATION_DOES);
    }
}
