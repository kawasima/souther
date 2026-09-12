package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Limits;
import souther.compiler.observe.ObservedValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The walk that stops and the predicate that judges say the same thing about one value.
 *
 * <p>Two readings of one set of numbers. {@code ObservedValues} reads a live value under limits and
 * says where it stopped; {@link Limits#admits} is asked of a value already read whole and says
 * whether those numbers would have kept it. Nothing holds them to each other but this: they count
 * the same things, and a value that stands at a bound has to be kept by one exactly where it is
 * admitted by the other.
 *
 * <p>Why it matters that they agree. What a row states is read whole, because a comparison is made
 * against what was written and not against as much of it as fits; what a row's inputs are is read
 * under the limits, because an answer holds them for as long as it is memoised. So one row's two
 * halves reach the same question by the two routes, and a row whose input was dropped while an
 * expectation of the same size was kept would be saying that one value is two sizes.
 */
class WhatALimitAdmitsIsWhatAWalkUnderItKeptTest {

    /** Wide enough to read any value here whole, which is what the predicate is asked about. */
    private static final Limits WHOLE =
            new Limits(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    @Test
    void aCollectionAtTheElementBound() {
        agree(Limits.DEFAULT, longs(63));
        agree(Limits.DEFAULT, longs(64));
        agree(Limits.DEFAULT, longs(65));
    }

    @Test
    void aMapAtTheEntryBound() {
        agree(Limits.DEFAULT, entries(64));
        agree(Limits.DEFAULT, entries(65));
    }

    @Test
    void aValueAtTheDepthBound() {
        agree(Limits.DEFAULT, nested(11));
        agree(Limits.DEFAULT, nested(12));
        agree(Limits.DEFAULT, nested(13));
    }

    @Test
    void aTextAtTheCharacterBound() {
        agree(Limits.DEFAULT, "x".repeat(1023));
        agree(Limits.DEFAULT, "x".repeat(1024));
        agree(Limits.DEFAULT, "x".repeat(1025));
    }

    /**
     * A value at the node bound, counted through nesting.
     *
     * <p>Under narrow limits, because the node budget is reached through a collection and how many
     * elements one collection keeps is the limit that would stop such a value first. Nine elements
     * inside a list is ten nodes, which is the budget exactly.
     */
    @Test
    void aValueAtTheNodeBound() {
        Limits ten = new Limits(12, 10, 64, 1024);
        agree(ten, longs(8));
        agree(ten, longs(9));
        agree(ten, longs(10));
    }

    /**
     * An optional that holds a value is the value it holds, and costs what that value costs.
     *
     * <p>A present optional is not a node of the value: what is read back is what it holds, and
     * {@link ObservedValue.Absent} is what stands where it holds nothing. So a value written inside
     * one and the same value written without one are one value, and the budget one of them spends
     * cannot be the budget the other spends — a reading that charged for the wrapper would make how
     * much of a value is kept depend on how it was handed over.
     */
    @Test
    void anOptionalIsTheValueItHolds() {
        Limits four = new Limits(12, 4, 64, 1024);
        agree(four, List.of(new Option.Some(1L), new Option.Some(2L)));
        agree(four, List.of(1L, 2L));
        assertEquals(observed(WHOLE, List.of(1L, 2L)),
                observed(WHOLE, List.of(new Option.Some(1L), new Option.Some(2L))),
                "the two are one value");
    }

    /** An optional holding nothing is a value of its own, and costs one node like any other. */
    @Test
    void andAnEmptyOneIsAValueOfItsOwn() {
        Limits four = new Limits(12, 4, 64, 1024);
        agree(four, List.of(new Option.None(), new Option.None(), new Option.None()));
    }

    /**
     * A value deeper than a comparison reads arrives stopped rather than flattened.
     *
     * <p>The limit an answer is read under for a comparison is wide, not absent. What is past it has
     * to arrive as something the comparison refuses to call equal — which is what it does with a
     * value that was stopped, and what it could not do with a value silently read as its own prefix.
     */
    @Test
    void aValueDeeperThanTheComparisonReadsIsStoppedRatherThanFlattened() {
        Object deep = 1L;
        for (int i = 0; i < FixtureReader.WHOLE.maxDepth() + 2; i++) {
            deep = List.of(deep);
        }
        ObservedValue at = observed(FixtureReader.WHOLE, deep);
        while (at instanceof ObservedValue.Sequence s) {
            at = s.elements().get(0);
        }
        assertEquals(new ObservedValue.Truncated(), at, "the walk stops rather than reading on");
    }

    /**
     * Both readings of {@code live} under {@code limits}, held to each other.
     *
     * <p>One side reads it under the limits and asks whether anything was dropped; the other reads
     * it whole and asks whether the limits would have kept it. The first is asked with a predicate
     * that cannot itself drop anything, so what it answers is about the walk and not about a second
     * bound.
     */
    private static void agree(Limits limits, Object live) {
        boolean keptByTheWalk = WHOLE.admits(observed(limits, live));
        boolean admitted = limits.admits(observed(WHOLE, live));
        assertEquals(keptByTheWalk, admitted,
                () -> "the walk kept " + keptByTheWalk + " and the limits admit " + admitted
                        + " of " + shown(live));
    }

    private static ObservedValue observed(Limits limits, Object live) {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        // No module is being read, so nothing here declares a data whose fields could be asked for.
        return ObservedValues.of(live, symbols,
                new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                        ScopedDeclarations.kindsOf(symbols),
                        FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))), limits);
    }

    private static String shown(Object live) {
        String written = String.valueOf(live);
        return written.length() <= 40 ? written : written.substring(0, 40) + "…";
    }

    private static List<Object> longs(int count) {
        List<Object> out = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            out.add(i);
        }
        return out;
    }

    private static Map<Object, Object> entries(int count) {
        Map<Object, Object> out = new LinkedHashMap<>();
        for (long i = 0; i < count; i++) {
            out.put(i, i);
        }
        return out;
    }

    /** A value whose deepest node stands at {@code depth}, the root being at zero. */
    private static Object nested(int depth) {
        Object at = 1L;
        for (int i = 0; i < depth; i++) {
            at = List.of(at);
        }
        return at;
    }
}

/**
 * What an optional looks like to a reading of a run's values.
 *
 * <p>A reading names the two cases by the class an optional arrives as, which is how a value built
 * by a compile's own classes says which of them it is. Written here so that a test can hand one
 * over without a compile behind it.
 */
class Option {

    record Some(Object value) {}

    record None() {}
}
