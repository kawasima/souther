package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Why a reading cannot speak for what stands at a position, and what to tell a reader about it.
 *
 * <p><b>Two kinds of thing, and they are not the same kind.</b> A rule the reading gave up on is
 * one an author wrote and can lift. A position an alternative nothing could read left open is a
 * consequence of one: the choice binds nothing there because a value satisfying the branch nobody
 * read owes the branch that was read nothing, and what an author would go and look at is the clause
 * inside that branch rather than anything written at the position. Both say this reading cannot
 * promise what stands there, so both are weighed when that is asked; only the first is what a
 * reader is told, where there is one.
 *
 * <p>Held apart rather than appended under a rule. Said as one list, which of them a reader is
 * shown is decided by which arrived first — and a walk reaching them the other way round tells an
 * author about the choice above a form they could have lifted.
 *
 * <p>One entry per rule the reading gave up on, holding every position that rule named. Which is
 * what the reading was handed: a rule about two positions is one rule, and a rule about one is one
 * rule, and either way what stopped the reading stopped it once. Filed as a map from position to
 * reasons, that fact came apart on the way in — a rule naming two places became two entries, and
 * the order the author wrote their rules in was recoverable only inside one place.
 *
 * <p><b>Only a producer's face is public.</b> What is here is read to answer whether a reading
 * speaks for what stands at a position and, where it does not, what stopped it
 * ({@link AdmissibleValues#speaksFor}, {@link AdmissibleValues#whyUnread}). Those two are one
 * derivation over the positions that hold one value, and a reader that could take the entries out
 * and look at one position would be answering the same question a second way — which is how a
 * position held as one value with another came to be reported as fully read. So a caller may make
 * one of these and add to it, and everything that reads it is here or beside it.
 *
 * @param <A> what a position is called
 */
public final class Standing<A> {

    /**
     * One rule this reading could not take in, and the places it is about.
     *
     * <p>The positions together, because that is what was given up on: a rule relating two of them
     * is one rule, and telling a reader about it at one place and again at the other would be two
     * accounts of one thing.
     *
     * <p><b>What it is about and not where it was written.</b> Those are the same for a rule
     * somebody wrote at the places it names. They part where a machine is refused: it is made for
     * the one value a block of positions share, so what stands at every one of them is wider than
     * the rules — and the pattern that asked for it was written at one of them. Which written place
     * asked is a different question with a different reader ({@code AdmissibleReading.askedAt}),
     * and it is answered there rather than recovered from here.
     */
    private record Entry<A>(Set<A> positions, UnreadReason why) {

        private Entry {
            positions = Collections.unmodifiableSet(new LinkedHashSet<>(positions));
        }
    }

    /** In the order the reading met them, which is the order the rules were written. */
    private final List<Entry<A>> entries;
    /**
     * The positions a choice's unread alternative left open, in no order anybody may read.
     *
     * <p>A set and not entries, because there is no rule here to be one of: what an author would
     * look at is the clause inside the branch, and where the choice is is a fact about a rule
     * ({@code StatedByClauses.RuleShortfall}) rather than about this place.
     */
    private final Set<A> openedByAlternative;

    private Standing(List<Entry<A>> entries, Set<A> openedByAlternative) {
        this.entries = List.copyOf(entries);
        this.openedByAlternative =
                Collections.unmodifiableSet(new LinkedHashSet<>(openedByAlternative));
    }

    /** Nothing was left standing, which is what a reading that read everything holds. */
    public static <A> Standing<A> nothing() {
        return new Standing<>(List.of(), Set.of());
    }

    /**
     * One rule that went unread, and the positions it named.
     *
     * <p>Nothing where it named none. A rule reaching no position this reading can name is still a
     * rule it did not read, and what that costs is a fact about the clause somebody wrote rather
     * than about any place here ({@code Adoption}): nothing can be asked of an entry that names
     * nowhere, so holding one would make {@link #isEmpty} mean "holds an entry" where every reader
     * of it means "has something to say".
     */
    public static <A> Standing<A> of(Set<A> named, UnreadReason why) {
        return named.isEmpty() ? nothing()
                : new Standing<>(List.of(new Entry<>(named, why)), Set.of());
    }

    /** Whether this reading can promise what stands everywhere, by either kind of evidence. */
    public boolean isEmpty() {
        return entries.isEmpty() && openedByAlternative.isEmpty();
    }

    /**
     * Both accounts, each rule kept and said once.
     *
     * <p>Appended and not chosen between, in the order the two were read. Two parts of one clause
     * stop a reading at one position in two ways and each is a rule of the author's to act on, so
     * dropping either would tell them about whichever part happened to be read first.
     */
    public Standing<A> and(Standing<A> other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Standing<>(joined(entries, other.entries),
                both(openedByAlternative, other.openedByAlternative));
    }

    /** This, with one more rule that went unread at {@code positions}. */
    public Standing<A> alsoAt(Set<A> positions, UnreadReason why) {
        return and(Standing.of(positions, why));
    }

    /**
     * This, with {@code these} left open by an alternative nothing could read.
     *
     * <p>Recorded and not weighed against what is already here. Which of the two a reader is told
     * about is settled where the question is asked ({@link #across}), so it turns on what this
     * holds rather than on the order the two arrived in — appended under a rule instead, a walk
     * that met the choice first told an author about it and left the form they could have lifted
     * unmentioned.
     *
     * <p>Which positions those are is not worked out here. It is a fact about the alternatives an
     * author wrote and about which branches anybody can be in, decided where both are known
     * ({@code StatedByClauses.AlternativeOpening}) and handed here.
     */
    Standing<A> alsoOpenedAt(Set<A> these) {
        return these.isEmpty() ? this
                : new Standing<>(entries, both(openedByAlternative, these));
    }

    /**
     * What stopped this reading at {@code position}, in the order the rules were written.
     *
     * <p>Every one of them and each once. Two rules stopped by the same limit are one thing for a
     * reader to lift, and which of them were the parts is the clause's rather than this reading's.
     */
    List<UnreadReason> at(A position) {
        return across(Set.of(position));
    }

    /**
     * The same over several positions at once, which is what a value held at more than one place
     * is asked.
     *
     * <p>A rule naming any of them is a rule about the value all of them hold, so what comes back
     * is every reason once, in the order the rules were written. Which is why the entries are kept
     * whole: read out of a map filed by position, an order over two places would be one this
     * compiler invented.
     *
     * <p><b>A rule of the positions themselves outranks the choice above them.</b> {@code n /= 5 ||
     * f(n)} leaves {@code n} open because of the form nothing reads, and the choice carried that
     * outward rather than introducing anything: a reader told both would lift the form and find the
     * question still there. Asked of the block and not of one position, for the reason the whole of
     * this is — a rule about the value two positions share is a rule about each of them, so a
     * direct rule at either is nearer than a choice that opened the other.
     */
    List<UnreadReason> across(Set<A> positions) {
        List<UnreadReason> out = new ArrayList<>();
        for (Entry<A> each : entries) {
            if (!Collections.disjoint(each.positions(), positions) && !out.contains(each.why())) {
                out.add(each.why());
            }
        }
        if (out.isEmpty() && !Collections.disjoint(openedByAlternative, positions)) {
            return List.of(UnreadReason.ALTERNATIVE_NOT_READ);
        }
        return Collections.unmodifiableList(out);
    }

    /** Every position this reading is short of something at, by either kind of evidence. */
    Set<A> positions() {
        Set<A> out = new LinkedHashSet<>();
        entries.forEach(each -> out.addAll(each.positions()));
        out.addAll(openedByAlternative);
        return Collections.unmodifiableSet(out);
    }

    /** The same account of the same rules, under the names {@code naming} gives the positions. */
    <B> Standing<B> renamed(Function<A, B> naming) {
        List<Entry<B>> out = new ArrayList<>();
        entries.forEach(each -> {
            Set<B> positions = new LinkedHashSet<>();
            each.positions().forEach(position -> positions.add(naming.apply(position)));
            out.add(new Entry<>(positions, each.why()));
        });
        Set<B> opened = new LinkedHashSet<>();
        openedByAlternative.forEach(position -> opened.add(naming.apply(position)));
        return new Standing<>(out, opened);
    }

    /**
     * The same account, said in the order {@code read} were read rather than the order the work
     * was done in.
     *
     * <p>Only the writing down. Every rule here is one of theirs or one the work added, and which
     * they are does not change — what changes is that a reader is shown them in the order the rules
     * were written.
     */
    Standing<A> inTheOrderOf(List<Standing<A>> read) {
        List<Entry<A>> out = new ArrayList<>();
        read.forEach(each -> out.addAll(each.entries));
        // The positions a choice opened are not put in an order, having none: what a reader is told
        // about one of them is a fact about a choice and is written where the choice is.
        return new Standing<>(joined(out, entries), openedByAlternative);
    }

    private static <A> List<Entry<A>> joined(List<Entry<A>> these, List<Entry<A>> those) {
        List<Entry<A>> out = new ArrayList<>(these);
        those.forEach(each -> {
            if (!out.contains(each)) {
                out.add(each);
            }
        });
        return out;
    }

    private static <A> Set<A> both(Set<A> these, Set<A> those) {
        if (those.isEmpty()) {
            return these;
        }
        Set<A> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Standing<?> it && entries.equals(it.entries)
                && openedByAlternative.equals(it.openedByAlternative);
    }

    /** What it holds and what an alternative opened, each in its own place — see
     *  {@link ValueHash}. */
    @Override
    public int hashCode() {
        return ValueHash.ofItsParts(Standing.class, entries.hashCode(),
                openedByAlternative.hashCode());
    }

    @Override
    public String toString() {
        return openedByAlternative.isEmpty() ? entries.toString()
                : entries + " opened at " + openedByAlternative;
    }
}
