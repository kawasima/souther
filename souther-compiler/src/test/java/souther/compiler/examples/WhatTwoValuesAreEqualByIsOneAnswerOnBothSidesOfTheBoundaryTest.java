package souther.compiler.examples;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.Comparisons;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Limits;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.Position;
import souther.compiler.observe.ValueTypes;
import souther.compiler.types.Type;
import souther.runtime.Values;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fake's table picks the same row on both sides of the boundary.
 *
 * <p>A row that ran through a table ran through the row that table's dispatch picked, and it picked
 * it by {@link Values#equal} over the values the run had. An output holding that row picks a row for
 * itself, out of values it was handed, by {@link Comparisons#same}. The two are different code and
 * have to be: one compares what a class loader built, the other what an observation carries, and
 * making the run observe every argument it dispatches on would put the limits an observation is held
 * to into what a fake answers.
 *
 * <p>So they are held to answering the same. Where they part, a row holds in this compile against an
 * answer the output's run of it never produces — the fake answering one thing here and another
 * there — and nothing about the row says so.
 *
 * <p>At the place the value stands, which is what the law is about. Which of a list and a set a
 * sequence is, is what the declaration reading it says on one side and what the class carrying it is
 * on the other; the two agree because a stand-in is asked at what the dependency declares it takes,
 * and a pair read at some other place is not what either side is asked.
 */
class WhatTwoValuesAreEqualByIsOneAnswerOnBothSidesOfTheBoundaryTest {

    private static final Type INT = Type.Prim.named("Int");
    private static final Type DECIMAL = Type.Prim.named("Decimal");
    private static final Type STRING = Type.Prim.named("String");
    private static final Type BOOL = Type.Prim.named("Bool");
    private static final Type DATE = Type.Prim.named("Date");

    /** Two values of one declared type, and where a value of it stands. */
    private record Pair(String says, Type at, Object left, Object right) {}

    private static final List<Pair> PAIRS = pairs();

    private static List<Pair> pairs() {
        List<Pair> pairs = new ArrayList<>();
        pairs.add(new Pair("one number and the same", INT, 1L, 1L));
        pairs.add(new Pair("one number and another", INT, 1L, 2L));
        pairs.add(new Pair("an amount and the same amount written to another scale", DECIMAL,
                new BigDecimal("1.50"), new BigDecimal("1.5")));
        pairs.add(new Pair("an amount and another", DECIMAL,
                new BigDecimal("1.50"), new BigDecimal("1.51")));
        pairs.add(new Pair("a text and the same", STRING, "ada", "ada"));
        pairs.add(new Pair("a text and another", STRING, "ada", "grace"));
        pairs.add(new Pair("a text and one that differs by case", STRING, "ada", "Ada"));
        pairs.add(new Pair("both of the two truths", BOOL, true, true));
        pairs.add(new Pair("one truth and the other", BOOL, true, false));
        pairs.add(new Pair("a date and the same", DATE,
                LocalDate.parse("2026-01-31"), LocalDate.parse("2026-01-31")));
        pairs.add(new Pair("a date and another", DATE,
                LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-01")));
        pairs.add(new Pair("a list and the same list", Type.list(INT),
                List.of(1L, 2L), List.of(1L, 2L)));
        pairs.add(new Pair("a list and the same elements in another order", Type.list(INT),
                List.of(1L, 2L), List.of(2L, 1L)));
        pairs.add(new Pair("a list and a longer one starting the same way", Type.list(INT),
                List.of(1L, 2L), List.of(1L, 2L, 3L)));
        pairs.add(new Pair("a list of lists and the same", Type.list(Type.list(INT)),
                List.of(List.of(1L), List.of(2L)), List.of(List.of(1L), List.of(2L))));
        pairs.add(new Pair("a list of lists differing inside one of them",
                Type.list(Type.list(INT)),
                List.of(List.of(1L), List.of(2L)), List.of(List.of(1L), List.of(3L))));
        pairs.add(new Pair("a set and the same elements met in another order", Type.set(INT),
                set(1L, 2L), set(2L, 1L)));
        pairs.add(new Pair("a set and one holding something else", Type.set(INT),
                set(1L, 2L), set(1L, 3L)));
        pairs.add(new Pair("a map and the same entries written in another order",
                Type.map(STRING, INT), map("a", 1L, "b", 2L), map("b", 2L, "a", 1L)));
        pairs.add(new Pair("a map and one answering differently under a key",
                Type.map(STRING, INT), map("a", 1L, "b", 2L), map("a", 1L, "b", 3L)));
        pairs.add(new Pair("a map and one that does not hold a key at all",
                Type.map(STRING, INT), map("a", 1L, "b", 2L), map("a", 1L, "c", 2L)));
        pairs.add(new Pair("a map holding lists and the same", Type.map(STRING, Type.list(INT)),
                Map.of("a", List.of(1L, 2L)), Map.of("a", List.of(1L, 2L))));
        return List.copyOf(pairs);
    }

    /**
     * Whatever the run says of two values, an output holding them says the same.
     *
     * <p>Both ways round as well, since one of the two walks the left value and the other the right:
     * a rule reading only what it is given first is one that answers by which value it was asked
     * about.
     */
    @Test
    void whatTheRunPicksARowByAndWhatAnOutputPicksOneByAgree() {
        for (Pair pair : PAIRS) {
            boolean equal = Values.equal(pair.left(), pair.right());
            Position at = Position.at(pair.at());

            assertEquals(equal, Comparisons.same(observe(pair.left()), observe(pair.right()),
                            declarations(), at),
                    () -> pair.says() + ", read at " + pair.at());
            assertEquals(equal, Comparisons.same(observe(pair.right()), observe(pair.left()),
                            declarations(), at),
                    () -> pair.says() + ", read at " + pair.at() + ", the other way round");
        }
    }

    /**
     * And the pairs say both things.
     *
     * <p>A law over pairs that were all equal, or all not, is one a rule answering the same to
     * everything would keep. What makes the check a check is that the two sides part on the same
     * pairs, so both answers have to be among them.
     */
    @Test
    void andThePairsAreBothTheSameValueAndNot() {
        List<String> same = new ArrayList<>();
        List<String> differ = new ArrayList<>();
        for (Pair pair : PAIRS) {
            (Values.equal(pair.left(), pair.right()) ? same : differ).add(pair.says());
        }

        assertTrue(same.size() >= 8, () -> "pairs of one value: " + same);
        assertTrue(differ.size() >= 8, () -> "pairs of two: " + differ);
    }

    private static Set<Object> set(Object... elements) {
        return new LinkedHashSet<>(List.of(elements));
    }

    private static Map<String, Object> map(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    /** No module is being read, so nothing here declares a data whose fields could be asked for. */
    private static ValueTypes declarations() {
        return ValueTypes.over(FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null)));
    }

    private static ObservedValue observe(Object live) {
        Symbols symbols = Symbols.none(DefaultStdlib.get());
        return ObservedValues.of(live, symbols,
                new NeutralForm(symbols,
                        FieldTypes.over(new CheckedDeclarations(_ -> null, _ -> null))),
                Limits.DEFAULT);
    }
}
