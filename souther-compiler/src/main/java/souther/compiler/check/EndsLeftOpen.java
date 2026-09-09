package souther.compiler.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The positions whose ends this reading did not work out, and what stands between each of them and
 * the walk that raises a rule's questions.
 *
 * <p>One question and one answer: of the ends a part of a clause states, which are the ones this
 * reading did not work out. Whether anybody can be in a branch, which positions a choice leaves as
 * wide as they are, and whether the model raises a question anywhere are three other questions with
 * three other owners, and nothing here decides any of them — what is done with their answers is to
 * strike positions off this one.
 *
 * <p><b>A choice never puts a position in here.</b> {@link #either} keeps only what its alternatives
 * brought to it, so a choice can strike a position off and can add none: it may show that the branch
 * beside an unfollowed one leaves the position at every value, and it has nothing else to say. So a
 * position here was put here by a leaf the reading gave up on, and the choices above it are the
 * road, not the source.
 *
 * <p><b>The road is what says whether anything else is telling a reader.</b> The walk that turns a
 * rule into the questions it raises goes into a conjunction and stops at a choice, so an end left
 * open with no choice between it and that walk is one the rule's own questions already leave
 * standing. What is carried here is that a written choice stands between — whether or not one of
 * them can be named — because that is exactly what nothing else reaches.
 *
 * <p><b>Named and answerable are not the same.</b> A choice one alternative of which nobody can be
 * in is not an alternative any more: what is left is the branch that stands, the walk never went
 * into it, and there is no branch for an author to look at. So such an end is carried with the
 * choice unnamed rather than dropped — the line at the position was not derived either way, and
 * which of the two it is decides what a document may say and not whether the measure is short.
 *
 * <p><b>The choice an end reaches with nothing else to name, and not every choice above.</b> An end
 * arriving at a choice already answerable for it has been named for that one, and naming the choice
 * above as well would send an author to a bracket rather than to a clause. Where the alternative
 * beside it leaves the same end open on its own account, both are named and both have to be lifted:
 * that is a fact about the rule rather than about how it was bracketed, and
 * {@code (a || b) || c} and {@code a || (b || c)} come to the same choices either way round.
 *
 * @param byPosition every position whose end this part left open, each with what stands between it
 *                   and the walk. Empty where the part's ends were all worked out
 */
record EndsLeftOpen(Map<FactSubject, EndsLeftOpen.Behind> byPosition) {

    /**
     * What stands between one end this reading did not work out and the walk that raises a rule's
     * questions.
     *
     * @param named        the choices to send an author to, empty where none can be named
     * @param underAChoice whether a choice an author wrote stands between. False is what says the
     *                     walk reached the part that left this end open, so the questions it raises
     *                     are already telling a reader — and a second sentence about it would be one
     *                     stop said twice
     */
    record Behind(Set<ChoiceSite> named, boolean underAChoice) {

        /** What a leaf leaves: an end nothing has been read past yet, and every leaf leaves it. */
        private static final Behind A_LEAF = new Behind(Set.of(), false);

        Behind {
            // Copied on the way in, as everything a reading publishes is — and an empty one is
            // already what it would be copied to. A leaf is read for every clause of every
            // declaration, so what is made here is made as often as anything in this reading.
            named = named.isEmpty() ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(named));
            if (!named.isEmpty() && !underAChoice) {
                throw new IllegalArgumentException(
                        "a choice was named for an end nothing stands between");
            }
        }

        /** What a leaf leaves: an end nothing has been read past yet. */
        static Behind aLeaf() {
            return A_LEAF;
        }

        /** The same end reached two ways, which is what a conjunction of them comes to. */
        Behind and(Behind other) {
            Set<ChoiceSite> both = new LinkedHashSet<>(named);
            both.addAll(other.named);
            return new Behind(both, underAChoice || other.underAChoice);
        }

        /** The same end under {@code choice}, which names itself where nothing else has. */
        Behind under(ChoiceSite choice) {
            return named.isEmpty() ? new Behind(Set.of(choice), true) : new Behind(named, true);
        }

        /** The same end under a choice one alternative of which nobody can be in. */
        Behind underACollapsedChoice() {
            return new Behind(named, true);
        }
    }

    /** What a part with no end left open comes to, which is most of them. */
    private static final EndsLeftOpen NOTHING = new EndsLeftOpen(Map.of());

    EndsLeftOpen {
        // As {@link Behind} is copied, and empty for the same reason: a leaf whose ends this
        // reading worked out makes one of these, and that is every leaf of every clause.
        byPosition = byPosition.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(byPosition));
    }

    /** A part whose ends this reading worked out, and one no reading has a word for at all. */
    static EndsLeftOpen nothing() {
        return NOTHING;
    }

    /**
     * One leaf, as the reading of ends answered for it.
     *
     * <p>Handed the positions rather than asked for them: whether this reading followed the rule to
     * the end is the reading's own answer ({@code OrderedReading.gaveUpAt}) and which positions the
     * leaf is about is the clause's, and both are in hand where a leaf is read. A leaf it followed
     * brings nothing here, whatever it found — a rule read from end to end that places no end is
     * one this reading answered.
     */
    static EndsLeftOpen at(Set<FactSubject> positions) {
        Map<FactSubject, Behind> out = new LinkedHashMap<>();
        positions.forEach(each -> out.put(each, Behind.aLeaf()));
        return new EndsLeftOpen(out);
    }

    /**
     * Both parts holding at once.
     *
     * <p>The union, and nothing is struck off. A part beside one this reading could not work out
     * still says what it says, and the end it places may be the loose one of the two — so a
     * conjunction leaves the end at a position either of its parts left open still open.
     */
    EndsLeftOpen both(EndsLeftOpen other) {
        if (other.byPosition.isEmpty()) {
            return this;
        }
        if (byPosition.isEmpty()) {
            return other;
        }
        Map<FactSubject, Behind> out = new LinkedHashMap<>(byPosition);
        other.byPosition.forEach((position, behind) -> out.merge(position, behind, Behind::and));
        return new EndsLeftOpen(out);
    }

    /**
     * Either part holding, with the positions the alternative beside them settles struck off.
     *
     * <p>An end one branch left open is one the choice leaves open unless the branch beside it puts
     * every value of the position on the order — a value satisfying that branch stands anywhere, so
     * the choice does too, whatever the branch nothing followed says. That is the one thing a choice
     * can show here, and it is shown by the other branch having been followed to the end and placed
     * no end at the position. Both halves of that are the other branch's own account of itself: what
     * it bounded, and what it left open.
     *
     * <p><b>Asked of the branches and not of what the choice was settled to leave open</b>
     * ({@link Settlement.Width}). That answer is worked out over the positions the branches bounded,
     * so a position no branch bounded is outside it — and a position outside it is one nothing
     * asked, which is not a position it was shown the alternatives preserve. Read as one, a choice
     * both of whose alternatives are forms nothing follows came back as a rule that draws no line,
     * which is the sentence this exists to remove.
     *
     * <p>So this is a filter and never a source. What comes out is contained in what the two
     * branches brought, which is what keeps a choice from inventing a rule nobody could read.
     */
    EndsLeftOpen either(ChoiceSite choice, Adoption<FactSubject, ReadingLanguage.Order> mine,
                        Set<FactSubject> myBounds,
                        EndsLeftOpen other, Adoption<FactSubject, ReadingLanguage.Order> theirs,
                        Set<FactSubject> theirBounds) {
        if (byPosition.isEmpty() && other.byPosition.isEmpty()) {
            return NOTHING;
        }
        Map<FactSubject, Behind> out = new LinkedHashMap<>();
        byPosition.forEach((position, behind) ->
                keptUnder(choice, position, behind, other, theirs, theirBounds, out));
        other.byPosition.forEach((position, behind) ->
                keptUnder(choice, position, behind, this, mine, myBounds, out));
        return new EndsLeftOpen(out);
    }

    /**
     * The same in a branch of a choice one alternative of which nobody can be in.
     *
     * <p>What is left of such a choice is the branch that stands, so nothing here is struck off —
     * there is no alternative beside these ends to have settled them. What the walk did is still
     * what it did: it stopped at the {@code ||} the author wrote, so these ends reach nothing else
     * and are carried with no choice to name.
     */
    EndsLeftOpen underACollapsedChoice() {
        if (byPosition.isEmpty()) {
            return this;
        }
        Map<FactSubject, Behind> out = new LinkedHashMap<>();
        byPosition.forEach((position, behind) ->
                out.put(position, behind.underACollapsedChoice()));
        return new EndsLeftOpen(out);
    }

    /**
     * The same for one position of one branch, against what the branch beside it came to.
     *
     * <p><b>What that branch still holds down, and not what some part of it once said.</b> A
     * constraint is open to being taken back by an alternative beside it, and a choice inside this
     * branch may have taken this one back already — so a branch that put a constraint on the
     * position and then lost it holds the position at every value, and the choice above stops where
     * it would without either alternative. Asked of what was put there ({@link Adoption#read}), a
     * fact this reading has already taken back comes round again a bracket further out, and an end
     * an inner choice settled is left open by an outer one.
     */
    private static void keptUnder(ChoiceSite choice, FactSubject position, Behind behind,
                                  EndsLeftOpen beside,
                                  Adoption<FactSubject, ReadingLanguage.Order> theirs,
                                  Set<FactSubject> theirBounds,
                                  Map<FactSubject, Behind> out) {
        // Whether the branch beside this one holds the number down, asked of whoever answers for
        // that number. Where the values at a position stop is the reading of ends', and where a
        // number an operation answers of one stops is the reading that holds those — a count is
        // never among what the ends bounded and a position is never among these, so which of them
        // is asked is settled by the number rather than by a caller picking one.
        if (!theirs.constrains(position) && !theirBounds.contains(position)
                && !beside.byPosition.containsKey(position)) {
            return;
        }
        out.merge(position, behind.under(choice), Behind::and);
    }
}
