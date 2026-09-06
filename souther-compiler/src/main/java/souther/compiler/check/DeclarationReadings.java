package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.values.StringMachineAnswers;

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
     * The canonical reading of {@code declaration} under {@code policy}, where one has been made in
     * this revision and this lends readings, or null where it lends none and the caller reads for
     * itself. A different policy is a different reading, not the same one under other terms.
     */
    default InvariantChecker.Seeded seeded(TypeKey declaration, ReadingPolicy policy) {
        return null;
    }

    /**
     * Told of a canonical reading of {@code declaration} that was just made under {@code policy},
     * so that whoever lends readings can lend this one for the rest of the revision. Told by the
     * reader that made it, at the one place a canonical reading is made; a lender that keeps none
     * ignores it.
     */
    default void made(TypeKey declaration, ReadingPolicy policy, InvariantChecker.Seeded seeded) {
    }

    /**
     * What a reading borrows while the answer about {@code named}'s machines is being made: nothing,
     * and what it makes is lent from here afterwards.
     *
     * <p>Nothing, because the answer being made is the one there would be to borrow. The
     * declaration's own machines are {@code recorder}, so that what the reading builds is what the
     * answer comes to hold; every other declaration's are worked out by the reading itself, since
     * asking for another declaration's answer from inside this one is how two declarations that
     * reach each other come to wait on each other.
     *
     * <p>And what it makes is lent, because the reading that answer is made by is the declaration's
     * canonical reading: the borrower who asked for the answer is handed that one rather than making
     * a second beside it. Handing it on asks nothing, so the circle above stays closed.
     */
    default DeclarationReadings whileTheAnswerIsMade(TypeKey named, StringMachineAnswers recorder) {
        DeclarationReadings lender = this;
        return new DeclarationReadings() {

            @Override
            public StringMachineAnswers of(TypeKey declaration) {
                return declaration.equals(named) ? recorder : StringMachineAnswers.NONE;
            }

            @Override
            public InvariantChecker.Seeded seeded(TypeKey declaration, ReadingPolicy policy) {
                return null;
            }

            @Override
            public void made(TypeKey declaration, ReadingPolicy policy,
                             InvariantChecker.Seeded seeded) {
                lender.made(declaration, policy, seeded);
            }
        };
    }

    /** Nothing made anywhere, for a reading with no store to ask. */
    DeclarationReadings NONE = _ -> StringMachineAnswers.NONE;
}
