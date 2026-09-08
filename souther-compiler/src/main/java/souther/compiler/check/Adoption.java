package souther.compiler.check;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where one reading took a clause in, composed over the clause the way its state is.
 *
 * <p>The same connectives {@link souther.compiler.values.AdmissibleValues} composes its values by,
 * over a different carrier and answering a different question. That one is about the values: which
 * of them may stand at a position, and whether anything left unread is why the answer is as wide as
 * it is. This one is about the rules: which reading took a clause in. A rule that could have
 * narrowed a position and turned out not to be needed is no part of the first answer and is very
 * much part of the second.
 *
 * <p><b>So this has no absorbing element and that one does.</b> A choice one of whose alternatives
 * admits every value at a position admits every value at it, so an unread alternative beside it
 * takes nothing back — which {@code AdmissibleValues.join} reads off the values it arrived at. Here
 * there is nothing to read it off: what is held is where a clause was taken in, not what came of
 * it, and a rule nothing had a word for was taken in by nothing however little the values needed
 * it. Made to agree, this would report a rule as read on the evidence that it did not matter. The
 * two answers are pinned together on one model by
 * {@code ARuleNoAlternativeNeededIsStillOneNobodyReadTest}.
 *
 * <p>So the connectives are not one operation. Under a conjunction, a part nothing could read
 * leaves the parts beside it saying what they said — {@code value >= 1 && f(value)} still bounds the
 * value. Under a choice it need not: a value satisfying the branch nothing could read is under no
 * obligation from the other, so {@code x == 7 || f(y)} says nothing about {@code x}.
 *
 * <p><b>Whether it does is not worked out here.</b> Whether the branch beside an unread one still
 * holds a position down is a question about what the two branches leave, and the answer is the
 * settlement's ({@link Settlement.WidthDependency}); what arrives here is that answer
 * ({@link Opening}), and all this does with it is apply it. Derived from {@link #hasUnreadPart}
 * instead, the rule that comes out is that a branch narrowing a position its neighbour narrows the
 * same way binds nothing there — and two alternatives holding a position to the same values hold it
 * there whether or not either of them could be read to the end.
 *
 * <p><b>So two things are held and they are not mixed.</b> {@link #read}, {@link #settled},
 * {@link #missed} and {@link #hasUnreadPart} are what this reading did with the clause.
 * {@link #opened} is what a choice above it was settled to do to that evidence, and it is contained
 * in what was read — there is nothing to open about a position this clause put no constraint on.
 * Folded into {@code missed}, the two would be one set answering for both, and which of them a
 * reader was being shown would be gone.
 *
 * <p>What is recorded is that the reading settled what the clause does to a position, which is not
 * the same as its having narrowed anything there. A branch shown to admit nothing settles every
 * position it named: the choice is the other branch, so what this clause does to a position only
 * that branch spoke of is nothing at all — an answer, and one only a reading that got to the end of
 * the branch could give.
 *
 * <p>Which is why that answer is not filed as a constraint. A constraint is open to being widened by
 * an alternative nothing could read; "this clause imposes nothing here" is not, since a further
 * choice imposes nothing either. Held as one, whether the answer survived turned on where the
 * brackets fell in a chain of choices — {@code (a || b) || c} and {@code a || (b || c)} are one
 * clause, and a report that reads them differently is reading the tree rather than the rule.
 *
 * <p>Composed rather than collected. A set filled as the leaves go by is a fact about the walk and
 * not about the clause, and it cannot be undone by what a later branch failed to read.
 *
 * @param <A>           what a position is called here. A set algebra and nothing else, so the
 *                      readings' own name for a position is the caller's business
 * @param <L>           which reading this is the account of. Nothing here reads it: what it is for
 *                      is that an answer worked out about one reading cannot be applied to the
 *                      other, which is otherwise the same Java type and composes without a
 *                      complaint ({@link ReadingLanguage})
 * @param read          the positions a part of this clause put a constraint on
 * @param settled       the positions a dead alternative named, which the choice imposes nothing on.
 *                      Not {@link #read}, and this is what keeps the choice associative: a
 *                      constraint can be widened by an alternative beside it, and "this clause
 *                      imposes nothing here" cannot — a further choice imposes nothing extra
 *                      either. Folded into {@code read}, whether it survived turned on where the
 *                      brackets fell
 * @param missed        the positions a part of it was about and this reading could not work out
 * @param hasUnreadPart whether a part of this clause went unread anywhere in it, which is what a
 *                      choice needs in order to ask what its alternatives leave. What that part was
 *                      about is not carried: a branch nothing could read is a branch a value can
 *                      satisfy instead, whichever positions it happens to name
 * @param opened        the positions a choice above was settled to have left open, out of those
 *                      this clause put a constraint on. The one thing here that is not this
 *                      reading's own work, and contained in {@link #read} so that it cannot become
 *                      a second account of what the clause was about
 */
record Adoption<A, L extends ReadingLanguage>(Set<A> read, Set<A> settled, Set<A> missed,
                                              boolean hasUnreadPart, Set<A> opened) {

    Adoption {
        read = Set.copyOf(read);
        settled = Set.copyOf(settled);
        missed = Set.copyOf(missed);
        opened = Set.copyOf(opened);
        if (!read.containsAll(opened)) {
            throw new IllegalArgumentException(
                    "a choice opens what a clause constrained, and this clause constrained none of "
                            + opened);
        }
    }

    /** What a clause this reading has no word for comes to. */
    static <A, L extends ReadingLanguage> Adoption<A, L> nothing() {
        return new Adoption<>(Set.of(), Set.of(), Set.of(), false, Set.of());
    }

    /**
     * The same account, for a part of a branch nobody can be in.
     *
     * <p>Nothing satisfies the branch, so nothing it said narrows a value of this type — and
     * nothing it missed is missing from what a value of this type is under either. What is left is
     * that the positions it named are settled: the choice does nothing to them, which is an answer
     * and not a gap. The same rule {@link #bothDead} states for a whole choice, said of one part of
     * one branch of it.
     *
     * <p>What a choice above left open goes with the constraint it was about. There is no
     * constraint here for an alternative to have widened.
     */
    Adoption<A, L> inADeadBranch() {
        return new Adoption<>(Set.of(), mentions(), Set.of(), false, Set.of());
    }

    /**
     * The same account, with the positions this reading could not work out given up on.
     *
     * <p>What a leaf says it adopted is said before anything is built: a pattern is named there and
     * the machine for it is made later, out of the position's allowance, once every rule that
     * reaches the position has arrived. So a position can be one this reading recognised every rule
     * of and still be one whose answer it did not build — and until this is applied, the account
     * says the clause was taken in whole while the values beside it say the position holds every
     * value because nobody worked it out.
     *
     * <p>Given up on and not merely unsaid. A reader asking what answered a rule at such a position
     * has to be told nothing did, because what stands there is what stands at a position no reading
     * reached: everything.
     *
     * <p>And what a choice left open at such a position goes too. What is held is the opening
     * applied to a constraint that is still standing, not a record of every opening there has been;
     * kept after the constraint it was about is gone, it would name a position nothing here reads.
     */
    Adoption<A, L> unbuiltAt(Set<A> positions) {
        if (positions.isEmpty() || mentions().stream().noneMatch(positions::contains)) {
            return this;
        }
        Set<A> stillRead = new LinkedHashSet<>(read);
        stillRead.removeAll(positions);
        Set<A> stillSettled = new LinkedHashSet<>(settled);
        stillSettled.removeAll(positions);
        Set<A> stillOpened = new LinkedHashSet<>(opened);
        stillOpened.removeAll(positions);
        Set<A> lost = new LinkedHashSet<>(missed);
        mentions().forEach(each -> {
            if (positions.contains(each)) {
                lost.add(each);
            }
        });
        return new Adoption<>(stillRead, stillSettled, lost, hasUnreadPart, stillOpened);
    }

    /**
     * One leaf: what it was about, what this reading produced of it, and whether it gave up on it.
     *
     * <p>{@code failed} is the reading's own, and not the emptiness of what it produced: a leaf
     * about no position of this value produces nothing and is not a leaf a reading gave up on —
     * though a reading that has no word for it did give up, which is what each of them says for
     * itself.
     */
    static <A, L extends ReadingLanguage> Adoption<A, L> at(Set<A> mentions, Set<A> produced,
                                                            boolean failed) {
        Set<A> missed = new LinkedHashSet<>(mentions);
        missed.removeAll(produced);
        Set<A> took = new LinkedHashSet<>(mentions);
        took.retainAll(produced);
        return new Adoption<>(took, Set.of(), missed, failed, Set.of());
    }

    /**
     * Both parts holding at once.
     *
     * <p>Nothing spoils anything: a part nothing read leaves the parts beside it saying what they
     * said, since all of them hold.
     *
     * <p>What a choice inside either of them left open comes along. A conjunction beside a choice
     * does not put back what the choice left open — {@code (x == 7 || f(y)) && z > 1} still says
     * nothing about {@code x} — so an opening reached here outlives the conjunction it is under.
     */
    Adoption<A, L> both(Adoption<A, L> other) {
        return new Adoption<>(union(read, other.read), union(settled, other.settled),
                union(missed, other.missed), hasUnreadPart || other.hasUnreadPart,
                union(opened, other.opened));
    }

    /**
     * Either part holding, under what the choice between them was settled to leave open.
     *
     * <p>{@code opening} is the whole of what the choice does to what these two branches said, and
     * it arrives worked out. Taken at the positions either branch put a constraint on: a position
     * neither of them constrained is one there is nothing to open about, and the settlement answers
     * over the branches as they were met rather than over this one written part, so it may well
     * name one.
     */
    Adoption<A, L> either(Opening<A, L> opening, Adoption<A, L> other) {
        Set<A> constrained = union(read, other.read);
        Set<A> nowOpen = new LinkedHashSet<>(union(opened, other.opened));
        opening.positions().forEach(each -> {
            if (constrained.contains(each)) {
                nowOpen.add(each);
            }
        });
        return new Adoption<>(constrained, union(settled, other.settled),
                union(missed, other.missed), hasUnreadPart || other.hasUnreadPart, nowOpen);
    }

    /** Whether this reading settled what the whole of the clause does to {@code position}. */
    boolean took(A position) {
        return (read.contains(position) || settled.contains(position))
                && !missed.contains(position) && !opened.contains(position);
    }

    /**
     * Whether this reading put a constraint on {@code position} that binds.
     *
     * <p>{@link #read} and neither of the other two sets: a position a dead alternative settled is
     * one this imposes nothing on, which is an answer and not a constraint, and a position this
     * reading could not work out is one it has nothing to say about.
     *
     * <p>And {@link #opened} taken off it, because a constraint an alternative nothing could read
     * stands beside is one a value can satisfy the other way. Here rather than at a caller, so that
     * the subtraction is made wherever the question is asked: left to a reader to make,
     * {@code value /= 5 || f(value)} with {@code f} unread answers that the clause holds the
     * position away from five.
     */
    boolean constrains(A position) {
        return read.contains(position) && !missed.contains(position)
                && !opened.contains(position);
    }

    /** The positions any part of the clause was about. */
    Set<A> mentions() {
        return union(union(read, settled), missed);
    }

    /**
     * This branch of a choice, beside one shown to admit nothing.
     *
     * <p>The dead branch settles the positions it named: nothing satisfies it, so what the choice
     * does to a position only it spoke of is nothing, which is an answer. Its own misses do not
     * come with it — a rule it could not read is a rule about a branch nobody can take — and
     * neither does its {@link #hasUnreadPart}, for the same reason.
     *
     * <p>What this branch missed still wins. {@code (s < "") || f(x)} leaves {@code x} open however
     * dead the first branch is, so the surviving branch's account is the one that outranks.
     */
    Adoption<A, L> beside(Adoption<A, L> dead) {
        return new Adoption<>(read, union(settled, dead.mentions()), missed, hasUnreadPart, opened);
    }

    /**
     * Two branches of a choice, both shown to admit nothing.
     *
     * <p>Then the choice admits nothing, which settles every position either of them named: the
     * values there are exactly none. No branch is left to have missed anything, and none to have
     * had a constraint of its own widened.
     */
    Adoption<A, L> bothDead(Adoption<A, L> other) {
        return new Adoption<>(Set.of(), union(mentions(), other.mentions()), Set.of(), false,
                Set.of());
    }

    private static <A> Set<A> union(Set<A> these, Set<A> those) {
        if (those.isEmpty()) {
            return these;
        }
        Set<A> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }
}
