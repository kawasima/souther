package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What narrowing a relation comes to, asked of every relation small enough to ask it of.
 *
 * <p>These are laws about an operation and not claims about a model, so they are asked of every
 * relation over a few blocks and every way its blocks can be left values — which is what makes them
 * hold of the relations nobody wrote down as well as the ones somebody did. A law asserted through a
 * model instead holds of whatever models there happen to be, and a change that broke it elsewhere
 * would be noticed by nothing.
 *
 * <p>The models that show what each of these laws is worth are beside them, in
 * {@link WhatHoldsANarrowingToWhatItIsForTest}. The laws are what a change is held to; the models
 * are what says why a reader should want them.
 */
class ANarrowingAnswersFromTheRelationAndNotFromHowItWasWrittenTest {

    /** Enough values for a relation over three blocks to run out of and not to. */
    private static final List<Value> VALUES =
            List.of(Value.text("A"), Value.text("B"), Value.text("C"));

    /** How many blocks the relations asked about have. Three is where a chain first runs through a
     *  block that was not already at one value, and four is where two removals at one block are
     *  first made by blocks that are not stated to differ from each other. */
    private static final int BLOCKS = 4;

    /**
     * One relation written any way round is answered the same way.
     *
     * <p>Every relation over these blocks, every way of leaving its blocks values, and every
     * writing of its denials — asked as the writings that swap one neighbouring pair with the next,
     * because every ordering of the pairs is a run of those. A rotation and a reversal are two
     * orderings out of as many as there are ways to write the pairs down; these are what says the
     * answer is the same for all of them.
     *
     * <p>What is compared is the whole answer — which blocks were left nothing, and which removals
     * left them so — because a reading that agreed about the verdict and not about the lack would
     * still be answering a report from how the rules were written.
     */
    @Test
    void oneRelationWrittenAnyWayRoundIsAnsweredTheSameWay() {
        overEveryRelation((written, domains) -> {
            Closure<String> said = Narrowing.of(written.relation(), domains);
            List<Apartness<String>> swapped = written.byASwapOfPairs();
            for (int at = 0; at < swapped.size(); at++) {
                int pair = at;
                assertEquals(said, Narrowing.of(swapped.get(at), domains),
                        () -> "the same relation, with its " + pair
                                + "th pair written after the next");
            }
        });
    }

    /**
     * And renaming the blocks renames the answer, which is what no choice among them survives.
     *
     * <p>Stronger than the writing, and what it keeps out is different. A reading that answered by
     * putting the blocks in an order and taking the first would answer alike however the denials
     * were written, and would answer about {@code p} for one relation and about {@code q} for that
     * relation with its blocks swapped — which is a reading that decides a declaration by how its
     * positions are spelled.
     *
     * <p>Asked as the callings that swap one block with the next, and of every relation over these
     * blocks. Every way of calling the blocks by each other's names is a run of those, and what is
     * asked of a relation here is asked of every relation the run passes through — so a law that
     * holds for a swap over the whole of this holds for all of them.
     */
    @Test
    void andRenamingTheBlocksRenamesTheAnswer() {
        overEveryRelation((written, domains) -> {
            Closure<String> said = Narrowing.of(written.relation(), domains);
            List<Called> callings = written.byASwapOfBlocks();
            for (int at = 0; at < callings.size(); at++) {
                Called calling = callings.get(at);
                Map<Sameness.Block<String>, Admits> left = new LinkedHashMap<>();
                domains.byBlock().forEach((block, admits) ->
                        left.put(calling.blocks().get(block), admits));
                int called = at;
                assertSameUnder(said, Narrowing.of(calling.relation(), new Domains<>(left)),
                        calling.blocks(),
                        () -> "the same relation, with its " + called
                                + "th block called by the next one's name");
            }
        });
    }

    /** What {@code block} is called where {@code swapped} and the block after it are called by
     *  each other's names. */
    private static int calling(int block, int swapped) {
        if (block == swapped) {
            return swapped + 1;
        }
        return block == swapped + 1 ? swapped : block;
    }

    /**
     * A narrowing takes values away and never adds one, and taking them again takes none.
     *
     * <p>The two together are what "nothing more can be taken" means. Without the first, a reading
     * could answer with values no rule left a block; without the second, a reading could stop while
     * a value its own rule refuses is still there, and the two arguments after it would be asked
     * about a block holding what nothing leaves room for.
     */
    @Test
    void whatANarrowingLeavesIsInsideWhatItWasHandedAndIsAllItCanTake() {
        overEveryRelation((written, domains) -> {
            if (!(Narrowing.of(written.relation(), domains)
                    instanceof Closure.Stable<String> stable)) {
                return;
            }
            stable.domains().byBlock().forEach((block, admits) -> {
                if (admits instanceof Admits.These left
                        && domains.of(block) instanceof Admits.These was) {
                    assertTrue(was.values().containsAll(left.values()),
                            "no value the block was not left");
                }
            });
            assertEquals(stable, Narrowing.of(written.relation(), stable.domains()),
                    "and narrowing what it left takes nothing");
        });
    }

    /**
     * And what it leaves is a reading every denial leaves room in.
     *
     * <p>Which is the law the two above are about, said of the pairs rather than of the rounds. A
     * block left a value its neighbour is left only that one of is a block holding what the rules
     * refuse it, and the count and the search after this are asked of what is left as though it
     * were what the rules leave.
     */
    @Test
    void andEveryDenialLeavesRoomForWhatEachOfItsBlocksIsLeft() {
        overEveryRelation((written, domains) -> {
            if (!(Narrowing.of(written.relation(), domains)
                    instanceof Closure.Stable<String> stable)) {
                return;
            }
            for (Apartness.Edge<String> edge : written.relation().edges()) {
                assertTrue(roomForEachOther(stable.domains(), edge.one(), edge.other()),
                        () -> "each of " + edge
                                + " is left something the other leaves room for");
                assertTrue(roomForEachOther(stable.domains(), edge.other(), edge.one()));
            }
        });
    }

    /**
     * A narrowing that leaves a block nothing is a relation nothing satisfies.
     *
     * <p>Held against every assignment there is rather than against another argument, so that what
     * the law is about is the relation and not a second reading of it that could be wrong the same
     * way. The other direction is not asserted: narrowing is not a way of showing that something
     * stands, and the arguments beside it are what a relation it leaves alone is asked of.
     */
    @Test
    void andABlockLeftNothingIsARelationNoAssignmentSatisfies() {
        overEveryRelation((written, domains) -> {
            if (Narrowing.of(written.relation(), domains) instanceof Closure.Contradicted<String>) {
                assertFalse(satisfiable(written, domains),
                        () -> "a block left nothing, and an assignment found: "
                                + written.stated() + " " + domains);
            }
        });
    }

    /**
     * And what took a value held only that value when it went.
     *
     * <p>Said of what the blocker was left and not of when its own values went. The removals of one
     * narrowing run through rounds that only decrease, so a blocker's rounds being earlier is what
     * makes the reading below it readable — but a blocker left {@code A} and {@code B} of which
     * {@code B} never went is one those rounds say nothing about, and it took nothing from
     * anything.
     *
     * <p>So the reading is built: what the block was handed, less every value a round before this
     * one took from it. What that comes to is the one value the removal was blocked by, and a
     * report naming anything else would be describing an argument nobody made.
     */
    @Test
    void andWhatTookAValueHeldOnlyItWhenItWent() {
        overEveryRelation((written, domains) -> {
            if (!(Narrowing.of(written.relation(), domains)
                    instanceof Closure.Contradicted<String> refused)) {
                return;
            }
            Set<Provenance.Removal<String>> removals = refused.provenance().removals();
            for (Provenance.Removal<String> removal : removals) {
                for (Sameness.Block<String> blocker : removal.blockers()) {
                    assertEquals(Set.of(removal.value()),
                            leftBefore(blocker, removal.round(), domains, removals),
                            () -> blocker + " held nothing but " + removal.value()
                                    + " when it took it from " + removal.block());
                }
            }
        });
    }

    /** What {@code block} was left before {@code round}, which is what it was handed less what the
     *  rounds before that took from it. */
    private static Set<Value> leftBefore(Sameness.Block<String> block, int round,
                                         Domains<String> domains,
                                         Set<Provenance.Removal<String>> removals) {
        Set<Value> left = new LinkedHashSet<>(((Admits.These) domains.of(block)).values());
        removals.forEach(removal -> {
            if (removal.block().equals(block) && removal.round() < round) {
                left.remove(removal.value());
            }
        });
        return left;
    }

    /** Whether {@code block} is left a value {@code next} leaves room for, for every value it is
     *  left. */
    private static boolean roomForEachOther(Domains<String> left, Sameness.Block<String> block,
                                            Sameness.Block<String> next) {
        if (!(left.of(block) instanceof Admits.These mine)
                || !(left.of(next) instanceof Admits.These theirs)) {
            return true;
        }
        return mine.values().stream()
                .allMatch(value -> theirs.values().stream().anyMatch(each -> !each.equals(value)));
    }

    /**
     * That one answer is the other about the blocks {@code called} calls these.
     *
     * <p>Held against each other block by block rather than by building the one from the other.
     * What the law says is that the two answers are the same relation's, and a reading made to say
     * so is a reading this walk pays for on every relation it asks about.
     */
    private static void assertSameUnder(Closure<String> said, Closure<String> other,
                                        Map<Sameness.Block<String>, Sameness.Block<String>> called,
                                        Supplier<String> why) {
        if (said instanceof Closure.Stable<String> mine
                && other instanceof Closure.Stable<String> theirs) {
            assertEquals(mine.domains().blocks().size(), theirs.domains().blocks().size(), why);
            mine.domains().byBlock().forEach((block, admits) ->
                    assertEquals(admits, theirs.domains().of(called.get(block)), why));
            return;
        }
        if (said instanceof Closure.Contradicted<String> mine
                && other instanceof Closure.Contradicted<String> theirs) {
            assertEquals(mine.leftNothing().size(), theirs.leftNothing().size(), why);
            mine.leftNothing().forEach(block ->
                    assertTrue(theirs.leftNothing().contains(called.get(block)), why));
            assertEquals(mine.provenance().removals().size(),
                    theirs.provenance().removals().size(), why);
            mine.provenance().removals().forEach(removal -> {
                Set<Sameness.Block<String>> blockers = new LinkedHashSet<>();
                removal.blockers().forEach(block -> blockers.add(called.get(block)));
                assertTrue(theirs.provenance().removals().contains(new Provenance.Removal<>(
                                called.get(removal.block()), removal.value(),
                                removal.round(), blockers)),
                        why);
            });
            return;
        }
        assertEquals(said.getClass(), other.getClass(), why);
    }

    /**
     * Whether some way of giving the blocks values tells every stated pair apart.
     *
     * <p>Walked over the blocks as the numbers a relation was built from, so that nothing is made
     * while a branch is being tried. What an assignment is asked about is which pairs are stated,
     * and a walk that built a block to ask that of would be measuring itself.
     */
    private static boolean satisfiable(Written written, Domains<String> domains) {
        List<Sameness.Block<String>> blocks = written.blocks();
        Value[][] left = new Value[blocks.size()][];
        for (int at = 0; at < blocks.size(); at++) {
            left[at] = ((Admits.These) domains.of(blocks.get(at))).values().toArray(new Value[0]);
        }
        return given(written.apart(), left, new Value[blocks.size()], 0);
    }

    /** Whether the blocks from {@code at} on can be given values no pair stated between them
     *  shares. */
    private static boolean given(boolean[][] apart, Value[][] left, Value[] taken, int at) {
        if (at == taken.length) {
            return true;
        }
        for (Value value : left[at]) {
            boolean clash = false;
            for (int other = 0; other < at; other++) {
                if (apart[at][other] && value.equals(taken[other])) {
                    clash = true;
                    break;
                }
            }
            if (!clash) {
                taken[at] = value;
                if (given(apart, left, taken, at + 1)) {
                    return true;
                }
            }
        }
        taken[at] = null;
        return false;
    }

    /**
     * Every relation over {@link #BLOCKS} blocks and every way of leaving each of them values.
     *
     * <p>Every non-empty set of values apiece, so that a block left one value, a block left two and
     * a block left all of them are each asked about beside every other. A block left none is not
     * among them: that is the block's own answer and is reached before a relation is asked what its
     * denials come to.
     *
     * <p>What every reading of one relation is held against is built once for that relation, which
     * is what these laws are about: the relation written another way round, and the relation with
     * two of its blocks called by each other's names. Built inside the readings, each would be made
     * again for every way its blocks can be left values, and what a run measured would be how long
     * it takes to write a relation down.
     */
    private static void overEveryRelation(Asked asking) {
        for (Written written : EVERY_RELATION) {
            List<Sameness.Block<String>> blocks = written.blocks();
            int[] pick = new int[blocks.size()];
            while (true) {
                Map<Sameness.Block<String>, Admits> left = new LinkedHashMap<>();
                for (int at = 0; at < blocks.size(); at++) {
                    left.put(blocks.get(at), THESE.get(pick[at]));
                }
                asking.of(written, new Domains<>(left));
                int at = 0;
                while (at < pick.length && ++pick[at] == THESE.size()) {
                    pick[at] = 0;
                    at++;
                }
                if (at == pick.length) {
                    break;
                }
            }
        }
    }

    /** Every relation over {@link #BLOCKS} blocks, beside what each of these laws holds it
     *  against. */
    private static final List<Written> EVERY_RELATION = everyRelation();

    /** Every non-empty set of values a block may be left. What a law is asked of is which values a
     *  block holds, and a set built for each reading would be one more thing a run measures. */
    private static final List<Admits> THESE = everyValueSet();

    private static List<Written> everyRelation() {
        List<Pair> pairs = new ArrayList<>();
        for (int one = 0; one < BLOCKS; one++) {
            for (int other = one + 1; other < BLOCKS; other++) {
                pairs.add(new Pair(one, other));
            }
        }
        List<Written> out = new ArrayList<>();
        for (int mask = 1; mask < (1 << pairs.size()); mask++) {
            List<Pair> stated = new ArrayList<>();
            for (int at = 0; at < pairs.size(); at++) {
                if ((mask & (1 << at)) != 0) {
                    stated.add(pairs.get(at));
                }
            }
            out.add(written(stated));
        }
        return out;
    }

    private static Written written(List<Pair> stated) {
        Apartness<String> relation = relating(stated);
        List<Apartness<String>> byASwapOfPairs = new ArrayList<>();
        for (int at = 0; at + 1 < stated.size(); at++) {
            List<Pair> swapped = new ArrayList<>(stated);
            Collections.swap(swapped, at, at + 1);
            byASwapOfPairs.add(relating(swapped));
        }
        List<Called> byASwapOfBlocks = new ArrayList<>();
        for (int swapped = 0; swapped + 1 < BLOCKS; swapped++) {
            Map<String, String> naming = new LinkedHashMap<>();
            Map<Sameness.Block<String>, Sameness.Block<String>> called = new LinkedHashMap<>();
            for (int each = 0; each < BLOCKS; each++) {
                naming.put(named(each), named(calling(each, swapped)));
                called.put(blockOf(each), blockOf(calling(each, swapped)));
            }
            List<Pair> under = new ArrayList<>();
            for (Pair pair : stated) {
                under.add(new Pair(calling(pair.one(), swapped), calling(pair.other(), swapped)));
            }
            byASwapOfBlocks.add(new Called(relating(under), naming, called));
        }
        List<Sameness.Block<String>> blocks = new ArrayList<>(relation.blocks());
        boolean[][] apart = new boolean[blocks.size()][blocks.size()];
        for (Pair pair : stated) {
            int one = blocks.indexOf(blockOf(pair.one()));
            int other = blocks.indexOf(blockOf(pair.other()));
            apart[one][other] = true;
            apart[other][one] = true;
        }
        return new Written(stated, relation, byASwapOfPairs, byASwapOfBlocks, blocks, apart);
    }

    private static List<Admits> everyValueSet() {
        List<Admits> out = new ArrayList<>();
        for (int which = 1; which < (1 << VALUES.size()); which++) {
            Set<Value> values = new LinkedHashSet<>();
            for (int bit = 0; bit < VALUES.size(); bit++) {
                if ((which & (1 << bit)) != 0) {
                    values.add(VALUES.get(bit));
                }
            }
            out.add(new Admits.These(values));
        }
        return out;
    }

    private static Apartness<String> relating(List<Pair> stated) {
        Apartness<String> out = Apartness.nothing();
        for (Pair pair : stated) {
            out = out.and(Apartness.of(named(pair.one()), named(pair.other())));
        }
        return out;
    }

    private static Sameness.Block<String> blockOf(int block) {
        return Sameness.Block.of(named(block));
    }

    private static String named(int block) {
        return "p" + block;
    }

    /**
     * One relation, and what each of these laws holds it against.
     *
     * @param stated the pairs it was built from
     * @param relation the relation itself
     * @param byASwapOfPairs it, written with one neighbouring pair after the next
     * @param byASwapOfBlocks it, with two neighbouring blocks called by each other's names
     * @param blocks its blocks, in the order the pairs name them
     * @param apart which of those blocks are stated to differ, indexed as they are
     */
    private record Written(List<Pair> stated, Apartness<String> relation,
                           List<Apartness<String>> byASwapOfPairs, List<Called> byASwapOfBlocks,
                           List<Sameness.Block<String>> blocks, boolean[][] apart) {}

    /** One relation under one calling of its blocks, and the calling. */
    private record Called(Apartness<String> relation, Map<String, String> naming,
                          Map<Sameness.Block<String>, Sameness.Block<String>> blocks) {}

    /** What each relation and each way of leaving its blocks values is asked. */
    @FunctionalInterface
    private interface Asked {
        void of(Written written, Domains<String> domains);
    }

    /** Two blocks stated to differ, as the numbers a relation is built from here. */
    private record Pair(int one, int other) {}

    /** And a relation over no pair at all is one nothing is taken from. */
    @Test
    void andARelationStatingNothingTakesNothingFromAnything() {
        Domains<String> domains = new Domains<>(Map.of(
                blockOf(0), new Admits.These(Set.of(VALUES.getFirst()))));

        assertEquals(new Closure.Stable<>(domains),
                assertInstanceOf(Closure.Stable.class,
                        Narrowing.of(Apartness.nothing(), domains)));
    }
}
