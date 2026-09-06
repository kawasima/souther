package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a coordinate stops is a lookup, and who is holding it reads the declaration again.
 *
 * <p>So the two are not the same price, and they are one answer. Which declaration holds an end is
 * answered by leaving that declaration's clauses out and reading again, once per candidate and per
 * side; where the coordinate stops was worked out when this reading was made. A caller standing a
 * fixture in a field's range wants the second and never asks the first — and it asks a field at a
 * time, of a reading made afresh for each of them, so paying for both is paying per field.
 *
 * <p>What is fixed here is the boundary and not a speed. Reading the ends reads no declaration;
 * asking who holds them reads as many as the question needs.
 */
class WhoHoldsAnEndIsWorkedOutWhenItIsAskedForTest {

    /**
     * {@code hi} is ten above {@code lo}, which is a relation and places no end of its own — so
     * where {@code hi} starts is somewhere a declaration has to be read again to attribute.
     *
     * <p>Two declarations write a lower end on the one coordinate and only one of them holds it:
     * {@code Common} puts {@code hi} ten above {@code lo} and {@code Held} puts it five above, so
     * ten above is where {@code hi} stops and {@code Common} is what put it there. Which is only
     * answerable if leaving a declaration's clauses out leaves out the ones it wrote rather than the
     * ones read where it was: {@code Common}'s rule arrives through {@code Held}'s spread, and read
     * the second way taking {@code Common} away takes nothing away, so neither candidate moves the
     * end on its own and both are named.
     */
    private static final String SOURCE = """
            module demo exposing ( Held, keep )

            data Common =
                { lo: Int
                , hi: Int
                }
                invariant based = lo >= 100
                invariant far = hi >= lo + 10

            data Held = { ...Common }
                invariant near = hi >= lo + 5

            behavior keep : (h: Held) -> Held

            let keep (h) = h
            """;

    @Test
    void readingTheEndsReadsNoDeclarationAndAskingWhoHoldsThemDoes() {
        FieldDomains reading = reading();

        long beforeEnds = InvariantChecker.readingsMade();
        NarrowedBounds hi = reading.at(RuleKey.of("hi"));
        assertNotNull(hi.bounds().min(), "something puts a floor under `hi`");
        assertEquals(beforeEnds, InvariantChecker.readingsMade(),
                "where the coordinate stops was settled when this reading was made");

        long beforeNames = InvariantChecker.readingsMade();
        assertEquals(java.util.List.of("Common"),
                holding(hi).stream().map(TypeSymbol::name).toList(),
                "and ten above is Common's doing: a rule a spread brought in is held by the"
                        + " declaration that wrote it, not the one it was read at");
        assertTrue(InvariantChecker.readingsMade() > beforeNames,
                "which took reading the declaration again without a declaration's clauses");
    }

    /** Asked twice, answered once. What is kept is the answer and not the work. */
    @Test
    void whoHoldsAnEndIsWorkedOutAtMostOnce() {
        NarrowedBounds hi = reading().at(RuleKey.of("hi"));
        holding(hi);

        long before = InvariantChecker.readingsMade();
        assertEquals(holding(hi), holding(hi), "the same answer");
        assertEquals(before, InvariantChecker.readingsMade(), "and no reading to arrive at it again");
    }

    /**
     * The reading that loses a meet is never asked who held its end.
     *
     * <p>Its end is not where the coordinate stops, so its declarations are not holding anything —
     * and finding out which of the two lost is what the ends had to be met for. Worked out before
     * the meet, both readings pay and one of the answers is discarded.
     */
    @Test
    void theReadingThatLostIsNeverAskedWhoHeldItsEnd() {
        // The reading of `Held` puts `hi` at 110, and this other reading puts it at 200. A floor is
        // the greater of the two, so the one that read the declaration is the one that loses.
        NarrowedBounds lost = reading().at(RuleKey.of("hi"));
        NarrowedBounds tighter = NarrowedBounds.of(
                new souther.compiler.numeric.NumericDomain.Bounds(
                        new souther.compiler.numeric.Endpoint(
                                souther.compiler.numeric.Count.of(200), true), null),
                java.util.List.of(TypeSymbols.declared(new TypeKey("demo", "Elsewhere"))),
                java.util.List.of());

        NarrowedBounds met = lost.meet(tighter);
        long before = InvariantChecker.readingsMade();
        assertEquals(java.util.List.of("Elsewhere"),
                holding(met).stream().map(TypeSymbol::name).toList(),
                "200 is where it starts, and only what says 200 is holding it");
        assertEquals(before, InvariantChecker.readingsMade(),
                "and what the losing reading would have named was never worked out");
    }

    /** Who is holding the floor this reading leaves, asked with that floor. */
    private static java.util.List<TypeSymbol.AtModule> holding(NarrowedBounds narrowed) {
        return AReadingOfAPosition.holding(narrowed, souther.compiler.numeric.EndSide.LOWER);
    }

    private static FieldDomains reading() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        TypeSymbol.AtModule held = TypeSymbols.declared(new TypeKey("demo", "Held"));
        return FieldDomains.of(held,
                RuleReadings.of(compilation, compilation.modules().get(0)),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
