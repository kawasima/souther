package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.values.StringMachineAnswers;

import java.util.function.Supplier;

/**
 * Where a reading of a declaration gets what somebody has already made of the declaration.
 *
 * <p>Two things, for two lifetimes. The answers about a declaration's string machines
 * ({@link #of}) are borrowed from the store's own answer for the declaration, which outlives an
 * edit that leaves the declaration's clauses as they were. The declaration's canonical reading
 * ({@link #seeded}) — its rules read whole, with nothing settled and nothing left out — is lent for
 * the revision it was made in and no longer: it is not a value, so no store answer holds it, and
 * what makes lending it sound is that within one revision every world it could be read from is the
 * same world. What is shared is the work, not an answer.
 *
 * <p>A capability and not a value, either way. What comes back from {@link #of} is the thing a
 * reading asks as it goes ({@link StringMachineAnswers}), so this is made where a store is to hand,
 * passed along with the reading, and never kept in anything a store answers with. A reading that
 * holds nothing to ask is given {@link #NONE} and works everything out itself.
 */
@FunctionalInterface
public interface DeclarationReadings {

    /** The answers for a reading of {@code declaration}'s string machines. */
    StringMachineAnswers of(TypeKey declaration);

    /**
     * The declaration's canonical reading as {@code source} and {@code policy} decide it: the one
     * somebody has already made under those terms, or the one {@code read} makes, kept for whoever
     * asks next. A lender that keeps none does the reading every time.
     *
     * <p>Borrowing and making are one act because what is shared is one thing. A reading is work,
     * and the work was done by reading what the store answers; a question handed the result read
     * none of that itself, so it is recorded as having read what the making read
     * ({@link StoreWork}). Handed the result alone it would be kept over an edit to what the making
     * read, and would answer about rules the author has since changed.
     *
     * <p>All three terms, because all three decide what the reading comes to. A policy is what a
     * reading may spend, and a reading made under other terms is another reading. A source is where
     * the clauses are read from and what the names in them mean: a reader may hand over one that
     * answers for fewer clauses than the store holds — a count asking what it would come to without
     * one declaration's rules does exactly that — and what comes back is a reading of what that
     * source left, which is not the declaration's own.
     */
    default InvariantChecker.Seeded reading(TypeKey declaration, RuleReadingSource source,
                                            ReadingPolicy policy,
                                            Supplier<InvariantChecker.Seeded> read) {
        return read.get();
    }

    /**
     * The same for the reader whose answer the reading is: it makes one and is lent none.
     *
     * <p>The answer being made is the one there would be to lend, and what the making watches is
     * what the answer comes to hold — so a reading lent to it would leave the answer holding
     * nothing. What it makes is kept, because it is the declaration's canonical reading and the
     * question that asked for the answer is the next to want it.
     */
    default InvariantChecker.Seeded readingForAnAnswer(TypeKey declaration, RuleReadingSource source,
                                                       ReadingPolicy policy,
                                                       Supplier<InvariantChecker.Seeded> read) {
        return read.get();
    }

    /**
     * The source a compilation reads {@code module}'s rules under, over the scope and the clauses it
     * reads them with.
     *
     * <p>Stamped here because what it says — that a reading made from it is the declaration's own as
     * this compilation reads it — is a thing only whatever lends the readings can say. A lender that
     * lends none says nothing, and answers with a source of its own that nothing shares; so does
     * this default, which is what a reading with no store to ask is given.
     */
    default RuleReadingSource theCompilationsOwn(String module, Symbols symbols,
                                                 ExpandedClauseLookup clauses) {
        return new RuleReadingSource(symbols, clauses);
    }

    /**
     * What a reading borrows while the answer about {@code named}'s machines is being made:
     * nothing, and what it makes is kept here afterwards.
     *
     * <p>Nothing, because the answer being made is the one there would be to borrow. The
     * declaration's own machines are {@code recorder}, so that what the reading builds is what the
     * answer comes to hold; every other declaration's are worked out by the reading itself, since
     * asking for another declaration's answer from inside this one is how two declarations that
     * reach each other come to wait on each other.
     *
     * <p>And what it makes is kept, because the reading that answer is made by is the declaration's
     * canonical reading: the question that asked for the answer is the next to want it, and is
     * handed this rather than making a second beside it.
     */
    default DeclarationReadings whileTheAnswerIsMade(TypeKey named, StringMachineAnswers recorder) {
        DeclarationReadings lender = this;
        return new DeclarationReadings() {

            @Override
            public StringMachineAnswers of(TypeKey declaration) {
                return declaration.equals(named) ? recorder : StringMachineAnswers.NONE;
            }

            @Override
            public InvariantChecker.Seeded reading(TypeKey declaration, RuleReadingSource source,
                                                   ReadingPolicy policy,
                                                   Supplier<InvariantChecker.Seeded> read) {
                return lender.readingForAnAnswer(declaration, source, policy, read);
            }

            @Override
            public InvariantChecker.Seeded readingForAnAnswer(TypeKey declaration,
                                                              RuleReadingSource source,
                                                              ReadingPolicy policy,
                                                              Supplier<InvariantChecker.Seeded> read) {
                return lender.readingForAnAnswer(declaration, source, policy, read);
            }
        };
    }

    /** Nothing made anywhere, for a reading with no store to ask. */
    DeclarationReadings NONE = _ -> StringMachineAnswers.NONE;
}
