package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.values.KnownExtents;
import souther.compiler.values.StringMachineAnswers;
import souther.compiler.values.TextExtent;
import souther.compiler.values.ValueSet;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A lender of canonical readings, over whatever answers a reading's other question.
 *
 * <p>What is lent is the declaration's own reading: its rules read whole, nothing settled at a
 * value and nothing left out at any name it reaches. Every question that reaches the declaration
 * and supposes nothing of its own asks for that reading, and there is nothing to tell two of them
 * apart — one declaration, one world, one set of terms. So the first is made and the rest are lent
 * it.
 *
 * <p>For a revision and no longer. A reading is not a value: what it holds is named by identity, so
 * nothing keeps it as an answer and comparing two would say nothing. What makes lending it sound is
 * narrower than what an answer needs and is met here — within one revision there is one world, and
 * the reading is of that world. {@code revision} is what says which one is current; when it moves,
 * what was lent under the old one is dropped rather than carried into a world it was not read from.
 *
 * <p>What was worked out about the sets those readings met is held here on the same terms and under
 * a key of its own. Where a set's strings stop is settled by the set, so it is not one declaration's
 * to lend to another — it is what this revision has found out, and every reading made under the
 * revision asks the one table.
 *
 * <p>What a reading decides on its own goes with it. The ends a declaration's conjuncts moved are
 * read by asking what its rules leave without each of them, and each of those is a reading of the
 * declaration — so what a lent reading has been asked is lent along with it, and the borrowers of
 * one reading put such a question once between them ({@link DeclarationReading}).
 *
 * <p>This shares work and not answers. Which questions are recomputed is settled before anything is
 * asked of this, and an answer is never kept here for a reader to find later.
 *
 * <p><b>Confined to the thread its store's walk is on.</b> Nothing here is synchronised and nothing
 * lent from here is: a store answers its questions on the one thread holding it, and what is lent
 * is reached through the store. That is ownership rather than a property of any one object, so a
 * store answered from two threads is not made sound by synchronising what it lends.
 */
public final class LentReadings implements DeclarationReadings {

    /** A declaration, which source its clauses are read from, and what the reading may spend:
     *  everything that decides what the reading comes to. */
    private record OfDeclarationUnder(TypeKey declaration, RuleReadingSource.Origin source,
                                      ReadingPolicy policy) {}

    /** A reading, and what the store was asked to make it. */
    private record Shared(DeclarationReading reading, StoreWork.Reads reads) {}

    private final DeclarationReadings machines;
    private final LongSupplier revision;
    private final StoreWork work;
    private final Map<OfDeclarationUnder, Shared> lent = new HashMap<>();
    /** Where the sets met under this revision were found to stop. Beside the readings because the
     *  lifetime is the same one, and apart from them because it is keyed by the set and by nothing
     *  about who met it. */
    private final Map<ValueSet, TextExtent> extents = new HashMap<>();

    /** The revision the readings in hand were made under. */
    private long lentAt;

    /**
     * Lends the readings made against {@code machines}, for as long as {@code revision} says the
     * world they were read from is the current one.
     */
    public LentReadings(DeclarationReadings machines, LongSupplier revision, StoreWork work) {
        this.machines = machines;
        this.revision = revision;
        this.work = work;
        this.lentAt = revision.getAsLong();
    }

    @Override
    public StringMachineAnswers of(TypeKey declaration) {
        return machines.of(declaration);
    }

    @Override
    public KnownExtents extents() {
        return known;
    }

    /**
     * What this revision has worked out about where sets stop, as the readings made under it ask
     * and answer it.
     *
     * <p>One object over a table that is dropped when the revision moves, rather than one made per
     * revision: what holds it is the reading a question was handed long before anybody asks it
     * anything, and a lender that handed out the table itself would have handed out one the next
     * revision no longer keeps.
     */
    private final KnownExtents known = new KnownExtents() {

        @Override
        public TextExtent of(ValueSet set) {
            return currentExtents().get(set);
        }

        @Override
        public void remember(ValueSet set, TextExtent extent) {
            currentExtents().put(set, extent);
        }
    };

    @Override
    public DeclarationReading reading(TypeKey declaration, RuleReadingSource source,
                                      ReadingPolicy policy,
                                      Supplier<InvariantChecker.Seeded> read) {
        Shared held = current().get(new OfDeclarationUnder(declaration, source.origin(), policy));
        if (held == null) {
            return readingForAnAnswer(declaration, source, policy, read);
        }
        // What the making read is what whoever is being answered out of it read: they are getting
        // the reading rather than doing it, and an edit to what it was made from has to reach them.
        held.reads().here();
        return held.reading();
    }

    @Override
    public DeclarationReading readingForAnAnswer(TypeKey declaration, RuleReadingSource source,
                                                 ReadingPolicy policy,
                                                 Supplier<InvariantChecker.Seeded> read) {
        StoreWork.Made<InvariantChecker.Seeded> made = work.watching(read);
        DeclarationReading reading = DeclarationReading.of(made.value());
        current().put(new OfDeclarationUnder(declaration, source.origin(), policy),
                new Shared(reading, made.reads()));
        return reading;
    }

    /**
     * What is lendable now: nothing, where the world has moved on since these were read.
     *
     * <p>Looked at when something is borrowed rather than announced by whatever moved. What a
     * reading was made from is the world of a revision, and a revision that has moved is a world
     * that may have; asking here is what keeps everything else from having to know that anything is
     * lent at all.
     */
    private Map<OfDeclarationUnder, Shared> current() {
        atTheCurrentRevision();
        return lent;
    }

    /** The same for what was worked out about sets, which is dropped by the same move. */
    private Map<ValueSet, TextExtent> currentExtents() {
        atTheCurrentRevision();
        return extents;
    }

    /** Drops what was shared under a revision that has been left behind. */
    private void atTheCurrentRevision() {
        long now = revision.getAsLong();
        if (now != lentAt) {
            lent.clear();
            extents.clear();
            lentAt = now;
        }
    }
}
