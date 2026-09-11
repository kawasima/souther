package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Shape;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two promises about a count, kept apart.
 *
 * <p>A caller with a line drawn at a count needs that count; a caller with a floor to clear needs a
 * value above it. They read one build and neither is written in terms of the other, so what they have
 * in common is a property of the builder and not a promise either makes to the other.
 *
 * <p>Held here rather than through the report, because a count larger than a row is worth carrying is
 * one the measure calls undecided before the generator is asked, and the report has no way to show
 * which half of the answer was given.
 */
class WhatBuildsASizeSaysWhatItCouldNotBuildTest {

    private static final RuleReadingSource NONE =
            RuleReadings.ofNoClauseFiled(Symbols.none(DefaultStdlib.get()));

    private static final RuleReadingContext READING =
            RuleReadingContext.unshared(NONE, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    /** What a position of this type is, which is what the builder is asked about. */
    private static Shape shape(Type type) {
        return TypeView.of(type, NONE.symbols()).shape();
    }

    @Test
    void aStringOfTheSizeAskedForIsBuilt() {
        assertEquals(List.of("\"xxx\""),
                Witnesses.ofSize(shape(Type.STRING), 3, READING, Set.of()).values().stream()
                        .map(FixtureTemplate::text).toList());
    }

    /**
     * A count of none is the empty value and not the absence of an answer.
     *
     * <p>A line drawn at zero is an edge a row stands on, and the empty string is what stands there.
     */
    @Test
    void aSizeOfZeroIsTheEmptyValue() {
        assertEquals(List.of("\"\""),
                Witnesses.ofSize(shape(Type.STRING), 0, READING, Set.of()).values().stream()
                        .map(FixtureTemplate::text).toList());
        assertEquals(List.of("[]"),
                Witnesses.ofSize(shape(new Type.ListOf(Type.INT)), 0, READING, Set.of()).values().stream()
                        .map(FixtureTemplate::text).toList());
    }

    /**
     * A floor of none asks for no value in particular.
     *
     * <p>Where the two promises come apart. A rule saying a collection holds at least nothing has said
     * nothing about the value, and the position's own chooser answers; a line at zero is a place a row
     * is owed at. One method cannot mean both, which is why there are two.
     */
    @Test
    void aFloorOfNoneAsksForNothingWhereASizeOfZeroAsksForTheEmptyValue() {
        assertEquals(List.of(), Witnesses.holding(shape(Type.STRING), 0, READING, Set.of()));
        assertTrue(!Witnesses.ofSize(shape(Type.STRING), 0, READING, Set.of()).values().isEmpty());
    }

    /** A count past what a row is worth carrying is one nothing composes, and says which it is. */
    @Test
    void aSizeNoRowWouldCarrySaysNothingComposesOne() {
        assertEquals(List.of(), Witnesses.ofSize(shape(Type.STRING), 100_000, READING, Set.of()).values());
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                Witnesses.reasonForSize(shape(Type.STRING), 100_000, READING));
    }

    /** A carrier nothing counts has no size to build at, and says which silence that is. */
    @Test
    void aCarrierNothingCountsSaysNothingComposesOne() {
        assertEquals(List.of(), Witnesses.ofSize(shape(Type.INT), 3, READING, Set.of()).values());
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                Witnesses.reasonForSize(shape(Type.INT), 3, READING));
    }

    /**
     * A set is as many values as the count asks for, no two of them equal, or it is nothing.
     *
     * <p>The type may not have that many. Two booleans are what a floor of three is offered, since a
     * value the rule refuses is an answer the decoder gives in the rule's own terms; they are not
     * three of anything, and a line drawn at three met by a row of two is a row standing somewhere
     * else. What is asked for and what was made are two numbers here, and the caller that needs them
     * equal is the one that checks.
     */
    @Test
    void aSetIsNothingWhereTheTypeHasFewerValuesThanTheCountAsksFor() {
        Shape set = shape(new Type.SetOf(Type.BOOL));

        assertEquals(List.of("[true, false]"), Witnesses.ofSize(set, 2, READING, Set.of()).values().stream()
                .map(FixtureTemplate::text).toList());
        assertEquals(List.of(), Witnesses.ofSize(set, 3, READING, Set.of()).values());
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                Witnesses.reasonForSize(set, 3, READING));
    }

    /** A floor goes on being offered what the type has, which is the value its rule refuses. */
    @Test
    void aFloorIsStillOfferedTheValueTheTypeCanReach() {
        assertEquals(List.of("[true, false]"),
                Witnesses.holding(shape(new Type.SetOf(Type.BOOL)), 3, READING, Set.of()).stream()
                        .map(FixtureTemplate::text).toList());
    }

    /** A map counts its entries, and a key that cannot be told from the others is not one more. */
    @Test
    void aMapIsNothingWhereItsKeysRunOutBeforeTheCount() {
        Shape map = shape(new Type.MapOf(Type.BOOL, Type.INT));

        assertTrue(!Witnesses.ofSize(map, 2, READING, Set.of()).values().isEmpty(),
                "two keys are two the type has");
        assertEquals(List.of(), Witnesses.ofSize(map, 3, READING, Set.of()).values());
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                Witnesses.reasonForSize(map, 3, READING));
    }
}
