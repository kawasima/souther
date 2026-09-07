package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relations that say what each part of a narrowing is there for.
 *
 * <p>Beside the laws in {@link ANarrowingAnswersFromTheRelationAndNotFromHowItWasWrittenTest},
 * which are what a change is held to. A law asked of every relation over a few blocks says that
 * something holds and never why anybody wanted it, so each of these is a relation on which one part
 * of the answer is the only thing that reaches it — and a reader deciding whether that part earns
 * its place has a model to read rather than a note.
 */
class WhatHoldsANarrowingToWhatItIsForTest {

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");
    private static final Value C = Value.text("C");

    /**
     * A round after the first is what refuses a triangle one of whose blocks holds one value.
     *
     * <p>{@code r} holds {@code A}, which leaves {@code p} and {@code q} holding {@code B} — and
     * each of those then leaves the other no room for it. Nothing is left with no value while the
     * first round is being worked out, so a narrowing that stopped there would hand a reading in
     * which every block still holds something on to the arguments after it.
     *
     * <p>Which is also why the answer is two lacks. {@code p} and {@code q} are left nothing by the
     * same round and the relation is the same relation with the two of them swapped, so naming one
     * would be an answer about how the pairs were written.
     */
    @Test
    void aTriangleWithABlockAtOneValueIsRefusedByTheRoundAfterTheFirst() {
        Apartness<String> triangle = Apartness.of("p", "q")
                .and(Apartness.of("p", "r")).and(Apartness.of("q", "r"));

        Closure.Contradicted<String> refused = refusing(triangle, holding(Map.of(
                block("p"), Set.of(A, B), block("q"), Set.of(A, B), block("r"), Set.of(A))));

        assertEquals(Set.of(block("p"), block("q")), refused.leftNothing());
        assertEquals(2, latestRound(refused),
                "and nothing is left without a value until the round after the first");
    }

    /**
     * And a relation whose sets a count says nothing about is refused by narrowing alone.
     *
     * <p>Four blocks in a ring stated to differ across it and not around it, so every set of blocks
     * all stated to differ is a pair and no pair is short. {@code p1} holds {@code A}, which leaves
     * {@code p2} at {@code C} and {@code p3} at {@code B} — and those two together leave {@code p0}
     * nothing, while neither of them is stated to differ from the other.
     *
     * <p>So this is the relation the count cannot be given instead of the round: the blocks that
     * take the last two values are not a set the count is taken of, and the pair each of them makes
     * with {@code p0} is left values enough.
     */
    @Test
    void andARelationWhoseSetsAreAllPairsIsRefusedByNarrowingAlone() {
        Apartness<String> across = Apartness.of("p0", "p2").and(Apartness.of("p0", "p3"))
                .and(Apartness.of("p1", "p2")).and(Apartness.of("p1", "p3"));
        Apartness.WhatABlockAdmits<String> left = holding(Map.of(
                block("p0"), Set.of(B, C), block("p1"), Set.of(A),
                block("p2"), Set.of(A, C), block("p3"), Set.of(A, B)));

        assertTrue(across.everySetWorthWalkingFor().orElseThrow().stream()
                        .allMatch(each -> each.size() == 2),
                "every set a count is taken of is a pair");

        Closure.Contradicted<String> refused = refusing(across, left);
        assertEquals(Set.of(block("p0")), refused.leftNothing());
        assertEquals(2, latestRound(refused));

        // And what a reading is handed is that lack and nothing the arguments after it would have
        // said, since a narrowing that leaves a block nothing is what the relation comes to.
        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(block("p0"))),
                lacksOf(across.reduce(left)));
    }

    /**
     * And it is refused where the search is past what it looks through, which is what makes the
     * round the only argument that answers.
     *
     * <p>The same four blocks beside enough others to put every assignment past what is looked
     * through. Nothing is stated between the two lots, so what the added blocks change is how much
     * there is to search and nothing about what the rules leave.
     *
     * <p>Asserted with the relation that stands beside it, because "refused" on its own does not
     * say that the search was out of reach: the same blocks with {@code p1} left a value to spare
     * are satisfiable, and what comes back is that this reduction did not settle it.
     */
    @Test
    void andItIsRefusedWhereTheSearchIsPastWhatItLooksThrough() {
        Apartness<String> across = Apartness.of("p0", "p2").and(Apartness.of("p0", "p3"))
                .and(Apartness.of("p1", "p2")).and(Apartness.of("p1", "p3"));
        Apartness<String> padded = across;
        for (int each = 0; each < 8; each++) {
            padded = padded.and(Apartness.of("f" + each, "g" + each));
        }
        Map<Sameness.Block<String>, Set<Value>> refused = new LinkedHashMap<>(Map.of(
                block("p0"), Set.of(B, C), block("p1"), Set.of(A),
                block("p2"), Set.of(A, C), block("p3"), Set.of(A, B)));
        Map<Sameness.Block<String>, Set<Value>> standing = new LinkedHashMap<>(refused);
        standing.put(block("p1"), Set.of(A, B));

        assertEquals(Set.of(new RelationalLack.NoValueLeftForIt<>(block("p0"))),
                lacksOf(padded.reduce(holdingElseMany(refused))));
        assertInstanceOf(Apartness.Reduction.NotKnown.class,
                padded.reduce(holdingElseMany(standing)),
                "and at this size nothing else answers, so a relation the round leaves alone is"
                        + " one this did not settle");
    }

    /**
     * And what a narrowing leaves is what the count is taken of.
     *
     * <p>Three blocks all stated to differ over three values are not short, and a fourth block
     * holding one of those values takes it from one of the three. What is left is three blocks over
     * two values, which is a shortage — so the count is answering about what the narrowing left and
     * not about what the relation was handed.
     */
    @Test
    void andWhatANarrowingLeavesIsWhatTheCountIsTakenOf() {
        Apartness<String> triangle = Apartness.of("a", "b").and(Apartness.of("a", "c"))
                .and(Apartness.of("b", "c")).and(Apartness.of("a", "d"));
        Apartness.WhatABlockAdmits<String> left = holding(Map.of(
                block("a"), Set.of(A, B, C), block("b"), Set.of(B, C),
                block("c"), Set.of(B, C), block("d"), Set.of(A)));

        assertInstanceOf(Closure.Stable.class, Narrowing.of(triangle,
                Domains.of(triangle.blocks(), left, triangle.blocks().size())),
                "nothing is left without a value");

        assertEquals(Set.of(new RelationalLack.TooFewValuesBetweenThem<>(
                        Set.of(block("a"), block("b"), block("c")), Set.of(B, C))),
                lacksOf(triangle.reduce(left)),
                "and the three are short once the fourth has taken its value");
    }

    /**
     * And blocks a round leaves nothing together are that many lacks.
     *
     * <p>Three blocks each holding one value, two of them stated to differ from the first. Every
     * one of them is left nothing by the first round, and swapping the two that are not stated to
     * differ from each other is the same relation — so an answer naming one of the three would name
     * a different block for a relation that is this one.
     */
    @Test
    void andBlocksARoundLeavesNothingTogetherAreThatManyLacks() {
        Apartness<String> both = Apartness.of("p", "q").and(Apartness.of("p", "r"));
        Apartness.WhatABlockAdmits<String> left = holding(Map.of(
                block("p"), Set.of(A), block("q"), Set.of(A), block("r"), Set.of(A)));

        assertEquals(Set.of(block("p"), block("q"), block("r")), refusing(both, left).leftNothing());

        Apartness<String> swapped = Apartness.of("p", "r").and(Apartness.of("p", "q"));
        assertEquals(lacksOf(both.reduce(left)), lacksOf(swapped.reduce(left)),
                "and the same relation with those two swapped is answered the same way");
    }

    /**
     * And a block that came to hold one value in the round that emptied its neighbour is no reason
     * for it.
     *
     * <p>{@code x} loses {@code A} to {@code y} and {@code B} to {@code u}, and is left nothing by
     * the first round. {@code z} loses {@code A} to {@code w} in that same round, so it holds
     * {@code B} alone once the narrowing has stopped — and it is a neighbour of {@code x}, holding
     * the value {@code x} lost to {@code u}.
     *
     * <p>Read off where the narrowing stopped, {@code z} would be offered as why {@code x} lost
     * {@code B}. It was left two values when {@code B} went, so it took nothing from anything, and
     * a report naming it would send an author to a rule that had not yet said anything.
     */
    @Test
    void andABlockThatCameToOneValueInThatRoundIsNoReasonForIt() {
        Apartness<String> around = Apartness.of("x", "y").and(Apartness.of("x", "z"))
                .and(Apartness.of("x", "u")).and(Apartness.of("z", "w"));
        Apartness.WhatABlockAdmits<String> left = holding(Map.of(
                block("x"), Set.of(A, B), block("y"), Set.of(A), block("z"), Set.of(A, B),
                block("w"), Set.of(A), block("u"), Set.of(B)));

        Closure.Contradicted<String> refused = refusing(around, left);

        assertEquals(Set.of(block("x")), refused.leftNothing());
        assertEquals(Set.of(block("y"), block("u")),
                refused.provenance().restingOn(block("x")),
                "the blocks that held one value when x lost its own, and not the one that came to"
                        + " hold one in the same round");
    }

    /** What narrowing {@code relation} against what {@code left} says its blocks hold comes to,
     *  where that leaves a block nothing. */
    private static Closure.Contradicted<String> refusing(
            Apartness<String> relation, Apartness.WhatABlockAdmits<String> left) {
        Closure<String> said = Narrowing.of(relation,
                Domains.of(relation.blocks(), left, relation.blocks().size()));
        assertInstanceOf(Closure.Contradicted.class, said);
        return (Closure.Contradicted<String>) said;
    }

    /** Which round of a narrowing took a value last. */
    private static int latestRound(Closure.Contradicted<String> refused) {
        return refused.provenance().removals().stream()
                .mapToInt(Provenance.Removal::round).max().orElseThrow();
    }

    private static Set<RelationalLack<String>> lacksOf(Apartness.Reduction<String> said) {
        assertInstanceOf(Apartness.Reduction.Nothing.class, said);
        return ((Apartness.Reduction.Nothing<String>) said).lacks();
    }

    private static Sameness.Block<String> block(String position) {
        return Sameness.Block.of(position);
    }

    private static Apartness.WhatABlockAdmits<String> holding(
            Map<Sameness.Block<String>, Set<Value>> these) {
        return (block, _) -> new Admits.These(these.get(block));
    }

    /** The same, where a block nothing was said of holds more values than a small relation counts,
     *  which is what puts an assignment over them past what is looked through. */
    private static Apartness.WhatABlockAdmits<String> holdingElseMany(
            Map<Sameness.Block<String>, Set<Value>> these) {
        Set<Value> many = new LinkedHashSet<>();
        for (int each = 0; each < 8; each++) {
            many.add(Value.text("v" + each));
        }
        return (block, atMost) -> Admits.of(these.getOrDefault(block, many), atMost);
    }
}
