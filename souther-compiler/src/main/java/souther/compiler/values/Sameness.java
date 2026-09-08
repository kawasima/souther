package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which positions of one reading hold one value.
 *
 * <p>A reading is a product, and what it is a product over is settled here. A rule stating that two
 * positions are equal does not narrow either of them: it says the two are one subject, so a rule
 * about the first is a rule about the second and neither of them has an answer of its own any more.
 * Held as a relation beside the sets, that fact reaches whoever remembers to ask; held as what the
 * product is indexed by, every reader of the product has it without asking.
 *
 * <p>So this is a coordinate system and not a domain. Nothing here says which values stand
 * anywhere. What it says is what an answer is an answer about, and the sets, the promises, the
 * proofs of precision and the allowances that pay for machines are all filed under a
 * {@link Block} rather than under a position. A position is what an author wrote, and it reaches
 * those answers through {@link #blockOf}.
 *
 * <p>Two readings of one declaration may hold different ones. An alternative that states an
 * equality is a product over the block it makes; the alternative beside it need not state it. So a
 * partition belongs to the alternative and not to the reading, and what a reading can say about a
 * position across its alternatives is what every one of them holds ({@link #common}).
 *
 * @param <A> what a position is called
 */
public final class Sameness<A> {

    /** Every position held as one with another, filed under the block it is in. A position in no
     *  block is absent, since a block of one is what a position is on its own. */
    private final Map<A, Block<A>> blocks;

    private Sameness(Map<A, Block<A>> blocks) {
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }

    /** No two positions held as one, which is what a reading that read no equality is a product
     *  over. */
    public static <A> Sameness<A> discrete() {
        return new Sameness<>(Map.of());
    }

    /** The two positions held as one, which is what an equality between them says. */
    public static <A> Sameness<A> of(A one, A other) {
        return Sameness.<A>discrete().joining(one, other);
    }

    /**
     * The relation these blocks are the classes of, in one pass.
     *
     * <p>For a caller that already holds the classes — a product is indexed by them, so reading the
     * relation off it is reading the keys. Built by {@link #joining} instead, a block of several
     * positions costs a copy of the whole relation for each of them, and a reading is asked what it
     * holds as one every time a position is looked up in it.
     *
     * <p><b>The one way in from blocks, so that what comes out is a relation.</b> Blocks that share
     * a position are not the classes of any: read as one, {@code p} would be held with {@code q}
     * while the two of them answered to different classes, which is not a partition and is not
     * something the reading below it can be asked about. Refusing them where a product is made and
     * building the relation somewhere else would be two contracts to keep in step, and the second
     * one is what a caller with blocks in hand reaches for.
     *
     * <p>Refused rather than closed. Putting two overlapping blocks together means meeting what
     * each of them was stated to admit, which is a set somebody has to build, and there is no
     * allowance where a product is made.
     */
    public static <A> Sameness<A> of(Collection<Block<A>> blocks) {
        Map<A, Block<A>> out = new LinkedHashMap<>();
        Set<A> seen = new LinkedHashSet<>();
        blocks.forEach(block -> block.members().forEach(each -> {
            if (!seen.add(each)) {
                throw new IllegalArgumentException(
                        "two classes of one relation hold " + each + " between them: " + blocks);
            }
            if (!block.isOne()) {
                out.put(each, block);
            }
        }));
        return out.isEmpty() ? discrete() : new Sameness<>(out);
    }

    /** Whether no two positions are held as one. */
    public boolean isDiscrete() {
        return blocks.isEmpty();
    }

    /**
     * Refuses an answer filed under a block this relation does not have.
     *
     * <p>What a block holds is one value, and which positions are one value is the reading's own.
     * A conjunction leaves a coarser relation and a choice a finer one, so an operation has to say
     * what each side's answers come to in the relation it leaves — and one that carries them across
     * by hand is one somebody writes without carrying them.
     *
     * <p>Refused rather than moved. Two promises arriving at one block promise what both promise,
     * which is a set somebody has to build and there is no allowance where a reading is made; and
     * an answer quietly refiled is a producer left wrong with nothing saying so. What it costs
     * unrefused is that a reader asking about a position looks under the block it is on, finds
     * nothing, and is told the reading promised nothing and widened nowhere.
     *
     * <p>Here rather than in each reading. The two of them hold the same answers a step apart and
     * share no type to say it once, so the rule written in both is the rule a third one is added
     * without.
     */
    @SafeVarargs
    public final void filing(Set<Block<A>>... these) {
        for (Set<Block<A>> filed : these) {
            for (Block<A> block : filed) {
                Block<A> here = blockOf(block.members().iterator().next());
                if (!block.equals(here)) {
                    throw new IllegalArgumentException("an answer at " + block
                            + " is filed under a coordinate this reading does not answer in,"
                            + " which holds those positions as " + here);
                }
            }
        }
    }

    /**
     * The block {@code position} is in, which is a block of one where nothing holds it with
     * anything.
     *
     * <p>Total, and answering for positions this never heard of. A reading is a product over every
     * position there is, and a position no rule of it mentioned is one it says nothing about — so
     * the coordinate of such a position exists and is its own.
     */
    public Block<A> blockOf(A position) {
        Block<A> held = blocks.get(position);
        return held != null ? held : Block.of(position);
    }

    /** The blocks of more than one position, in the order they were made. */
    public Collection<Block<A>> joined() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(blocks.values()));
    }

    /** Every position held as one with another. */
    public Set<A> positions() {
        return blocks.keySet();
    }

    /** This, with {@code one} and {@code other} held as one value, and everything either of them
     *  was already held with. */
    public Sameness<A> joining(A one, A other) {
        if (blockOf(one).equals(blockOf(other))) {
            return this;
        }
        Set<A> made = new LinkedHashSet<>(blockOf(one).members());
        made.addAll(blockOf(other).members());
        return withBlock(Block.of(made));
    }

    /**
     * Both readings' equalities holding at once, which is a conjunction of what they say.
     *
     * <p>The closure of the two and not their union: {@code p == q} beside {@code q == r} says the
     * three are one, and a reader left the pairs would find no rule saying so of {@code p} and
     * {@code r}. Written here, so that a caller conjoining two readings never holds a relation that
     * is not transitive.
     */
    public Sameness<A> meet(Sameness<A> other) {
        if (other.isDiscrete()) {
            return this;
        }
        if (isDiscrete()) {
            return other;
        }
        Sameness<A> out = this;
        for (Block<A> block : other.joined()) {
            List<A> members = new ArrayList<>(block.members());
            for (int each = 1; each < members.size(); each++) {
                out = out.joining(members.get(0), members.get(each));
            }
        }
        return out;
    }

    /**
     * What both readings hold, which is what a choice between them leaves.
     *
     * <p>Two positions are one value under a choice only where each alternative says so. Read the
     * other way round, a branch stating an equality would lend it to the branch beside it, and the
     * choice would hold a rule neither alternative states.
     *
     * <p>Not a union of the blocks either. {@code p ~ q ~ r} beside {@code p ~ q} leaves
     * {@code p ~ q}, so a block is cut down rather than kept or dropped whole: positions stay
     * together where they are together on both sides.
     */
    public Sameness<A> common(Sameness<A> other) {
        if (isDiscrete() || other.isDiscrete()) {
            return discrete();
        }
        Map<List<Block<A>>, Set<A>> together = new LinkedHashMap<>();
        for (A position : blocks.keySet()) {
            if (other.blocks.containsKey(position)) {
                together.computeIfAbsent(List.of(blockOf(position), other.blockOf(position)),
                        _ -> new LinkedHashSet<>()).add(position);
            }
        }
        Sameness<A> out = discrete();
        for (Set<A> members : together.values()) {
            if (members.size() > 1) {
                out = out.withBlock(Block.of(members));
            }
        }
        return out;
    }

    /** The same relation between the same positions, under the names {@code naming} gives them.
     *
     *  <p>A change of vocabulary and not a fold: the naming names two positions two positions, so
     *  no two members of one block arrive under one name and no two blocks are merged. */
    public <B> Sameness<B> renamed(Function<A, B> naming) {
        Sameness<B> out = Sameness.discrete();
        for (Block<A> block : joined()) {
            Set<B> members = new LinkedHashSet<>();
            block.members().forEach(each -> members.add(naming.apply(each)));
            out = out.withBlock(Block.of(members));
        }
        return out;
    }

    private Sameness<A> withBlock(Block<A> block) {
        Map<A, Block<A>> out = new LinkedHashMap<>(blocks);
        block.members().forEach(each -> out.put(each, block));
        return new Sameness<>(out);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Sameness<?> it && blocks.equals(it.blocks);
    }

    /** Which block each position is on — see {@link ValueHash}. */
    @Override
    public int hashCode() {
        return ValueHash.ofWhatItHolds(Sameness.class, blocks.hashCode(), blocks.size());
    }

    @Override
    public String toString() {
        return joined().toString();
    }

    /**
     * The positions one answer is about, which is one position wherever no equality was read.
     *
     * <p>What a reading holds a set at, promises values of, and pays for a machine at. Equal by its
     * members and by nothing else, so the same positions written together in two orders are one
     * coordinate: {@code p == q && q == r} and {@code q == r && p == q} name one block, hold one
     * set, and spend from one purse.
     *
     * <p>Its members are read in the order they are spelled in, so that where their spellings tell
     * them apart, what is written out of a reading — a proof naming the positions that must hold
     * one value among them — does not read differently for the order the equalities behind it were
     * written in. Where two of them are spelled alike it says nothing: those two keep the order
     * they arrived in, which is the order it was there to keep out. Nothing is filed, compared or
     * hashed under any of it.
     */
    public static final class Block<A> {

        private final Set<A> members;

        /**
         * What this block is asked for whenever an answer about it is looked up, worked out where
         * the block is made.
         *
         * <p><b>Held rather than worked out, because a block is asked far more often than one is
         * made.</b> A relation's answers are filed under blocks — what each is left, what each is
         * promised, what each spends — so a round of a narrowing reads a map of them for every
         * block it walks, and a reading asks which block a position is on for every position it
         * reads. Derived from the members on each of those, the answer is a walk of the set and a
         * hash of every position in it, over and over, for a value that cannot change.
         *
         * <p><b>And taken as what a collection holds rather than as their sum.</b> A set of blocks
         * is what a refusal names and what a lack is about, and a set hashes what it holds by
         * adding them up — so a block that handed up the sum over its own members would leave
         * {@code {{p}, {q, r}}} and {@code {{p, q}, {r}}} at one number whatever those positions
         * are, and the grouping a block exists to state would be the one thing it does not say.
         * {@link ValueHash} is where that is closed and why.
         */
        private final int hash;

        private Block(Set<A> members) {
            this.members = members;
            this.hash = ValueHash.ofWhatItHolds(Block.class, members.hashCode(), members.size());
        }

        /**
         * The block one position is on its own.
         *
         * <p>Built without ordering anything. One member is in one order, and the order below is
         * what several of them are read in.
         */
        public static <A> Block<A> of(A position) {
            return new Block<>(Set.of(position));
        }

        /** The block these positions are held as one in. */
        public static <A> Block<A> of(Set<A> members) {
            return new Block<>(ordered(members));
        }

        /** The positions this answer is about. */
        public Set<A> members() {
            return members;
        }

        /** Whether this is one position on its own. */
        public boolean isOne() {
            return members.size() == 1;
        }

        /** Whether {@code position} is one of the positions this answers for. */
        public boolean holds(A position) {
            return members.contains(position);
        }

        /** The same block, of what {@code naming} calls each of its positions. A naming names two
         *  positions two positions, so this has as many members as it had. */
        public <B> Block<B> renamed(Function<A, B> naming) {
            Set<B> out = new LinkedHashSet<>();
            members.forEach(each -> out.add(naming.apply(each)));
            return of(out);
        }

        /**
         * Equal by its members and by nothing else.
         *
         * <p>The hash is asked first, and it is an answer about the members, so two blocks it tells
         * apart are told apart without reading them. Which comparisons that reaches depends on
         * where they came from: one made through a map has had the hashes compared already and
         * arrives only where they agree, and one asked of two blocks in hand — whether both ends of
         * a pair are one block, whether an answer is filed under a coordinate the reading has —
         * arrives with nothing compared. It is those this turns away.
         *
         * <p>Where the hashes agree the members are read, because two blocks with one hash are
         * still two blocks unless the same positions are in both.
         */
        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Block<?> it
                            && hash == it.hash && members.equals(it.members));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return isOne() ? String.valueOf(members.iterator().next()) : members.toString();
        }

        /**
         * The members in the order they are spelled in, which is the order a block reads them in.
         *
         * <p>How they are spelled is all a position of any kind can be ordered by here, since what
         * a position is is the caller's. One of them is in one order already, which is what the
         * block of a single position is built by.
         */
        private static <A> Set<A> ordered(Set<A> members) {
            if (members.isEmpty()) {
                throw new IllegalArgumentException("an answer is about at least one position");
            }
            if (members.size() == 1) {
                return Set.of(members.iterator().next());
            }
            List<A> sorted = new ArrayList<>(members);
            sorted.sort(Comparator.comparing(String::valueOf));
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }
    }
}
