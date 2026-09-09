package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Where a reading was left nothing, and what kind of lack it is.
 *
 * <p><b>Two quantifiers and not one set of blocks.</b> A lack at each of some blocks says of every
 * one of them that it holds nothing; a lack about several of them together says that no assignment
 * to all of them stands, while each of them is left values of its own. Held as one set, a reader
 * putting two lacks together intersects them — which is what the first kind means and is an
 * invention for the second, since two collective lacks over sets that overlap have shown nothing
 * about what the two happened to share.
 *
 * <p><b>And both at once, because the two are not alternatives.</b> A conjunction is refused where
 * either side is, so one side refused at a block beside one refused about blocks together is
 * refused both ways, and neither is what it is instead of the other. Held as one of three things a
 * refusal can be, such a conjunction had to give one of them up — and whichever it gave up, what
 * three sides come to is then not the same when the first two are put together first as when the
 * last two are. So a refusal holds what it was refused at and what it was refused about, and either
 * of them may be nothing.
 *
 * <p>Which leaves the two ways of putting refusals together doing the same thing to each half. A
 * conjunction keeps what either side showed ({@link #eitherShown}) and a choice keeps what both
 * showed ({@link #shownByBoth}), so the first is a union of each half and the second a meet of
 * each — and how the sides were bracketed is not something either can answer from.
 *
 * <p><b>Every witness, and what a reader keeps of them is that reader's question.</b> What is built
 * here is what refused an alternative, all of it: a proof that stopped at one kind because it had
 * found the other is a proof whose content is the order its questions were asked in. Reading it
 * back is where witnesses are given up — {@link #nearest} for the one sentence a report writes,
 * {@link #withoutWhatAPositionAnswers} for the ones a reading is the place to keep. Neither of them
 * is done on the way in.
 *
 * <p>Closed, so that the values here are the ones its makers reach. A refusal handed both halves
 * directly is one whose two halves nothing looked for together, which is what this type exists to
 * rule out; every way in is named below and every one of them is exhaustive.
 *
 * @param <A> what a position is called
 */
public final class Refusal<A> {

    /** Neither half, which is what a reading refused by nothing anything can name was refused by —
     *  see {@link #nowhere()}. */
    private static final Refusal<?> NOWHERE = new Refusal<>(new Parts<>(Set.of(), Lacks.none()));

    private final Parts<A> parts;

    /**
     * What a refusal holds, so that two of them are one where these are.
     *
     * @param atEachOf the blocks each of which is left nothing, which may be none of them
     * @param together what no assignment to some blocks satisfies, which may be nothing
     */
    private record Parts<A>(Set<Sameness.Block<A>> atEachOf, Lacks<A> together) {

        Parts {
            atEachOf = Collections.unmodifiableSet(new LinkedHashSet<>(atEachOf));
        }

        /** What they hold, written in one order — see {@link InOneOrder}. */
        @Override
        public String toString() {
            return InOneOrder.of(atEachOf) + together;
        }
    }

    private Refusal(Parts<A> parts) {
        this.parts = parts;
    }

    /** The blocks each of which is left nothing, which may be none of them. */
    public Set<Sameness.Block<A>> atEachOf() {
        return parts.atEachOf();
    }

    /** What no assignment to some blocks satisfies, which may be nothing. */
    public Lacks<A> together() {
        return parts.together();
    }

    /** Nowhere in particular, which is where a lack no block is answerable for is. */
    @SuppressWarnings("unchecked")
    public static <A> Refusal<A> nowhere() {
        return (Refusal<A>) NOWHERE;
    }

    /** A lack at each of {@code blocks}, which is nowhere where they are none. */
    public static <A> Refusal<A> atEachOf(Set<Sameness.Block<A>> blocks) {
        return blocks.isEmpty() ? nowhere() : new Refusal<>(new Parts<>(blocks, Lacks.none()));
    }

    /**
     * What no assignment to some blocks satisfies, and what showed it.
     *
     * <p>The whole argument and not the blocks alone, because what a report may say about them
     * turns on which argument refused them: a value stated to differ from itself and a set of
     * blocks with fewer values between them than there are blocks are two sentences.
     *
     * <p>Several of them, because one argument can show a lack about several lots of blocks at
     * once and the relation says nothing about which of them to carry.
     */
    public static <A> Refusal<A> ofThemTogether(Lacks<A> lacks) {
        return lacks.isEmpty() ? nowhere() : new Refusal<>(new Parts<>(Set.of(), lacks));
    }

    /**
     * Everything that refused one alternative, both kinds of witness looked for.
     *
     * <p>The two questions and not the two answers. A caller handing over a set of blocks and a
     * set of lacks has already decided which of them to look for, and the thing it had to decide
     * with is the other one — so the refusal it builds names whichever of its witnesses its author
     * asked about first, and an alternative refused both ways comes back saying one. Asked here,
     * neither question can be put to the other: what walks the blocks is this, and what
     * {@code relating} is written over is the relation alone.
     *
     * <p>Both, whatever either says. A relation is read where a side of the alternative was left
     * nothing, and a side is read where the relation refuses everything — an alternative refused
     * both ways is refused both ways, and the reason it is refused twice over is not a reason to
     * write down one of them.
     *
     * <p><b>Every block a witness, this being what refused the alternative and not what the
     * reading keeps of it.</b> A block of one position left nothing is a lack somewhere, and
     * whether it is a lack anything but that position is answerable for is a question about who is
     * holding the proof — {@link #withoutWhatAPositionAnswers} is where a reading asks it. Dropped
     * here, an alternative whose only empty side is one position would be refused by nothing at
     * all, and a reader that took this for the answer would have it standing.
     *
     * <p>What the alternative says each block holds, and not the blocks alone. Every reading that
     * asks this holds the two together, and each of them is about what a block was left — so
     * blocks on their own would be blocks with what they hold looked up again, once per block, on
     * the walk a conjunction takes for every pair of two readings' alternatives.
     *
     * @param at what the alternative says each of its blocks holds
     * @param refusedAt whether the alternative is left nothing at one of them, which is what the
     *                  block holds met with whatever the reader asking is holding it against
     * @param relating what the denials between them show, asked whatever the blocks answered
     * @param <V> what a block is left, which is a set of values or a description of one
     */
    public static <A, V> Refusal<A> ofAnAlternative(Map<Sameness.Block<A>, V> at,
                                                    BiPredicate<Sameness.Block<A>, V> refusedAt,
                                                    WhatARelationShows<A> relating) {
        Set<Sameness.Block<A>> atEachOf = null;
        for (Map.Entry<Sameness.Block<A>, V> each : at.entrySet()) {
            if (refusedAt.test(each.getKey(), each.getValue())) {
                if (atEachOf == null) {
                    atEachOf = LinkedHashSet.newLinkedHashSet(at.size());
                }
                atEachOf.add(each.getKey());
            }
        }
        Lacks<A> together = relating.shows();
        if (atEachOf == null && together.isEmpty()) {
            return nowhere();
        }
        return new Refusal<>(
                new Parts<>(atEachOf == null ? Set.of() : atEachOf, together));
    }

    /**
     * The blocks a report may name, which is what the lack is about and what it was reached
     * through.
     *
     * <p>The route as well as the claim, and here rather than in the lack. What an author is sent
     * to read is the rules that leave the blocks nothing, and a block left nothing because its
     * neighbours were left one value each is a place whose own rules are fine with what they leave
     * it — so a report naming it alone would send the author nowhere useful. What two lacks are
     * compared by is the claim, which is what {@link #shownByBoth} asks and this does not.
     */
    public Set<Sameness.Block<A>> blocks() {
        if (together().isEmpty()) {
            return atEachOf();
        }
        Set<Sameness.Block<A>> out = new LinkedHashSet<>(atEachOf());
        out.addAll(together().blocks());
        return Collections.unmodifiableSet(out);
    }

    /** Whether nothing here names a place. */
    public boolean isNowhere() {
        return atEachOf().isEmpty() && together().isEmpty();
    }

    /**
     * The same refusal less what a position's own rules already answer.
     *
     * <p>What a reading may keep as its own proof. A lone position left no value is what
     * {@link AdmissibleValues#perPosition} answers, and a reading holding a second account of it
     * would be saying one thing twice — so a block of one position is not a witness the reading
     * is the place to name. What is new is a lack no position has on its own: the rules hold
     * several positions as one value and leave that value nothing, while each of them on its own
     * is left something.
     *
     * <p><b>Asked here and not where the proof was built.</b> Which witnesses a holder may keep is
     * a question about the holder, and the alternative was refused by everything that refused it
     * whoever goes on to read it. Answered while the proof was being put together, an alternative
     * left nothing at one position would come back refused by nothing, and whether it stands would
     * turn on which holder was going to be handed the answer.
     *
     * <p>The lack about blocks together is kept whatever it names. Such a lack carries no rule
     * that a block holds nothing, and names blocks of one position wherever the rules relate two
     * positions nothing else holds as one — each is left values of its own, so it is not a second
     * account of what a position's own rules say.
     */
    public Refusal<A> withoutWhatAPositionAnswers() {
        boolean anyToGiveUp = false;
        for (Sameness.Block<A> block : atEachOf()) {
            if (block.isOne()) {
                anyToGiveUp = true;
                break;
            }
        }
        if (!anyToGiveUp) {
            return this;
        }
        Set<Sameness.Block<A>> out = LinkedHashSet.newLinkedHashSet(atEachOf().size());
        for (Sameness.Block<A> block : atEachOf()) {
            if (!block.isOne()) {
                out.add(block);
            }
        }
        return out.isEmpty() && together().isEmpty()
                ? nowhere() : new Refusal<>(new Parts<>(out, together()));
    }

    /**
     * Which sentence a report writes of this.
     *
     * <p>Asked of the whole refusal, and asked once. Both halves can hold and a report writes one
     * thing, so which of them it is about is a rule — and a rule spelled at each place that writes
     * a report is one rule written as many times as there are reports.
     *
     * <p>A block left nothing is what it is: naming it sends an author to the rules that leave that
     * block nothing, where a lack about blocks together sends them to the rules between blocks each
     * of which is left something on its own. So where there is a block to name, that is the
     * sentence.
     */
    public Nearest nearest() {
        if (!atEachOf().isEmpty()) {
            return Nearest.AT_EACH_OF;
        }
        return together().isEmpty() ? Nearest.NOWHERE : Nearest.OF_THEM_TOGETHER;
    }

    /** Which of the things a refusal holds a report is about. */
    public enum Nearest {

        /** Neither: what was shown is about the whole product and no block is why. */
        NOWHERE,

        /** Blocks each of which is left nothing. */
        AT_EACH_OF,

        /** Blocks no assignment to all of them satisfies, each left something on its own. */
        OF_THEM_TOGETHER
    }

    /** The same lack about the blocks {@code naming} calls these. */
    public <B> Refusal<B> renamed(Function<A, B> naming) {
        Set<Sameness.Block<B>> out = new LinkedHashSet<>();
        atEachOf().forEach(block -> out.add(block.renamed(naming)));
        return new Refusal<>(new Parts<>(out, together().renamed(naming)));
    }

    /**
     * Where a conjunction with a side that holds nothing was refused.
     *
     * <p>A different question from {@link #shownByBoth}, and the answer is different. There, two
     * readings of one set of rules both hold nothing and what can be said is what they agree on;
     * here, a conjunction is refused because a side of it is, and where both sides are, both
     * reasons are true of it. So two lacks at blocks are a lack at all of them, and two lacks about
     * blocks together are both of them — a side whose lack is the other's adds nothing, which is
     * what a set of them says without anybody asking.
     *
     * <p>And a side refused at a block beside one refused about blocks together is refused both
     * ways, which is why neither half is given up here.
     */
    public static <A> Refusal<A> eitherShown(Refusal<A> one, Refusal<A> other) {
        Set<Sameness.Block<A>> both = new LinkedHashSet<>(one.atEachOf());
        both.addAll(other.atEachOf());
        return new Refusal<>(new Parts<>(both, one.together().and(other.together())));
    }

    /**
     * Where two readings that both left nothing were both refused.
     *
     * <p>The blocks each was refused at, kept where both were refused there. A block one of them
     * stands at is not one the pair has nothing at, so what can be said is what they agree on —
     * and where they agree on none, what was shown is about the whole product and no block is why.
     *
     * <p><b>And two lacks about blocks together are the ones both readings show.</b> Such a lack is
     * not a lack at each of its blocks, so its blocks are not what is kept — what is, is the lack
     * itself, and a reading that showed it is a reading the pair may be said to have shown it.
     *
     * <p><b>What the two both showed is asked of the lacks and not of how either reached them.</b>
     * Two readings that leave one block no value have shown that block has none, whatever took the
     * values in each of them. Asked of the refusals whole, the two would be different wherever
     * their routes were, and a choice between two readings that both hold nothing would be
     * reported as holding something.
     *
     * <p>Which is why it is asked here and not by whether the two refusals are equal. A refusal is
     * a value and is equal to what it is: two of them that were reached differently are two
     * different values, and what they showed in common is this question rather than that one.
     */
    public static <A> Refusal<A> shownByBoth(Refusal<A> one, Refusal<A> other) {
        Set<Sameness.Block<A>> both = new LinkedHashSet<>(one.atEachOf());
        both.retainAll(other.atEachOf());
        return new Refusal<>(new Parts<>(both, one.together().sharedWith(other.together())));
    }

    /** The same halves, which is what a refusal is. */
    @Override
    public boolean equals(Object said) {
        return said instanceof Refusal<?> it && parts.equals(it.parts);
    }

    /** Its two halves, each in its own place and the number finished here — see {@link ValueHash}.
     *  A refusal is carried inside values that sum what they hold, so a number handed up as
     *  gathered would leave the two halves separable again there. */
    @Override
    public int hashCode() {
        return ValueHash.ofItsParts(Refusal.class, atEachOf().hashCode(), together().hashCode());
    }

    /** What it holds, written in one order — see {@link InOneOrder}. */
    @Override
    public String toString() {
        return "Refusal" + parts;
    }
}
