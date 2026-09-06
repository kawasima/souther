package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.values.StringMachineAnswers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

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
 * <p>This shares work and not answers. Which questions are recomputed is settled before anything is
 * asked of this, and an answer is never kept here for a reader to find later.
 */
public final class LentReadings implements DeclarationReadings {

    /** A declaration, which source its clauses are read from, and what the reading may spend:
     *  everything that decides what the reading comes to. */
    private record OfDeclarationUnder(TypeKey declaration, RuleReadingSource.Origin source,
                                      ReadingPolicy policy) {}

    /** A reading, and what the store was asked to make it. */
    private record Shared(InvariantChecker.Seeded seeded, StoreWork.Reads reads) {}

    private final DeclarationReadings machines;
    private final LongSupplier revision;
    private final StoreWork work;
    private final Map<OfDeclarationUnder, Shared> lent = new HashMap<>();

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
    public RuleReadingSource theCompilationsOwn(String module, Symbols symbols,
                                                ExpandedClauseLookup clauses) {
        return new RuleReadingSource(symbols, clauses, new AModulesRules(module));
    }

    @Override
    public StringMachineAnswers of(TypeKey declaration) {
        return machines.of(declaration);
    }

    @Override
    public InvariantChecker.Seeded reading(TypeKey declaration, RuleReadingSource source,
                                           ReadingPolicy policy,
                                           java.util.function.Supplier<InvariantChecker.Seeded> read) {
        Shared held = current().get(new OfDeclarationUnder(declaration, source.origin(), policy));
        if (held == null) {
            return readingForAnAnswer(declaration, source, policy, read);
        }
        // What the making read is what whoever is being answered out of it read: they are getting
        // the reading rather than doing it, and an edit to what it was made from has to reach them.
        held.reads().here();
        return held.seeded();
    }

    @Override
    public InvariantChecker.Seeded readingForAnAnswer(TypeKey declaration, RuleReadingSource source,
                                                      ReadingPolicy policy,
                                                      java.util.function.Supplier<InvariantChecker.Seeded> read) {
        StoreWork.Made<InvariantChecker.Seeded> made = work.watching(read);
        current().put(new OfDeclarationUnder(declaration, source.origin(), policy),
                new Shared(made.value(), made.reads()));
        return made.value();
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
        long now = revision.getAsLong();
        if (now != lentAt) {
            lent.clear();
            lentAt = now;
        }
        return lent;
    }
}
