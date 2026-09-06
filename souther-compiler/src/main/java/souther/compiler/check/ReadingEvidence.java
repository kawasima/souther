package souther.compiler.check;

import souther.compiler.values.AdmissibleValues;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which readings took each clause into their own semantics.
 *
 * <p>Recorded where each reading runs, and by that reading. A caller working out from a clause's
 * spelling which reading ought to have managed it is guessing at another reader's semantics — which
 * is the defect this exists to stop, one level over from the one it was written for: an ordering
 * bound is unreadable to the reading that turns clauses into sets of values and is read whole by two
 * others, and the shape says nothing about which of them managed it.
 *
 * <p>Per clause, because that is the granularity a question has. Asked per position, one clause's
 * failure is the account of every clause beside it: {@code invariant floor = value >= 1} leaves the
 * reading of values short at a position, and {@code invariant seven = value == 7} written beside it
 * was taken in whole and came back reported as unread.
 *
 * <p>Filed under {@link RuleRef} and not under the clause reference the walk had in hand. Every
 * clause reaching here is an invariant's, so the two would key the same map today — and the value
 * recorded here is carried to a report, where what names a rule is the rule. Weakened on the way
 * out, each reader downstream had to work the identity back out for itself.
 *
 * <p>Success and not attempt. A reading that recognised part of a clause and gave up took nothing
 * in, so nothing here records it — what is written down is the point at which a reading adopted the
 * clause into what it holds.
 *
 * <p>Every part of the clause, and not one of them. A conjunction is one rule the author wrote and
 * is read one conjunct at a time; a clause half of which nothing took in is a clause nothing took
 * in, and answering it on the strength of the half that was read is how {@code value >= 1 &&
 * value * value >= 4} came back with nothing to say while the same two rules written apart were
 * reported. The same reading {@code Predicates.Owed.and} makes of a clause it could not read all
 * of.
 *
 * <p><b>Every reading, and each one asked at its own adoption point.</b> There are more of them than
 * a reader notices: {@code value * 2 >= 4} is beyond the reading of ends and beyond the reading of
 * values, and the reading that builds the numeric constraints takes it in whole. An accounting that
 * consulted two of the three would report that clause exactly the way #842 reports a bound — as a
 * rule this compiler could not read, about a model it had read.
 */
final class ReadingEvidence {

    /** Where each reading took a clause in. */
    private final Map<RuleRef.Invariant, Set<FactSubject>> spokenFor = new LinkedHashMap<>();

    /** Where a part of a clause was taken in by nothing, which no other part makes up for. */
    private final Map<RuleRef.Invariant, Set<FactSubject>> left = new LinkedHashMap<>();

    /**
     * What stopped the reading of values at each position of each rule.
     *
     * <p>Under the rule, which is what makes it an account of a rule rather than of a place. Two
     * clauses reach one position and stop this reading in two ways — a pattern nothing takes apart
     * beside an ordering it has no set for — and a reason filed under the position alone answers
     * for whichever of them a report happens to ask about. The rule that was actually short of it
     * is then named beside a limit that belongs to its neighbour.
     *
     * <p>Empty for a rule this reading took in, and empty as well for one it was short of without
     * recording why. The second is what {@link #stoppedBy} answers for.
     *
     * <p><b>Facts and not a map of positions to reasons.</b> Each of these says the written place
     * the reading decided it at as well as the position and the reason, and a map keyed by position
     * has nowhere to put the third — two choices of one rule each offering an alternative nothing
     * could read leave one position open, and an author has two of them to look at. Held as reasons
     * per position they were one, and which of the two a reader was sent to was whichever the walk
     * met first.
     *
     * <p>A set, because nothing here is in an order anybody may read. Which of two an author wrote
     * first is the source's to say and is asked where a document is written.
     */
    private final Map<RuleRef.Invariant, Set<RuleShortfall>> stopped = new LinkedHashMap<>();

    /** A reading took {@code rule} in at {@code position}. */
    void record(RuleRef.Invariant rule,FactSubject position) {
        spokenFor.computeIfAbsent(rule, _ -> new LinkedHashSet<>()).add(position);
    }

    /**
     * Whether a part of {@code rule} about one of {@code positions} was taken in by nothing.
     *
     * <p>Outranks every other answer about the rule. A clause half of which nothing read is a clause
     * nothing read, however well the other half went — so an end placed by one conjunct does not
     * answer for the conjunct beside it.
     */
    boolean anyLeftStanding(RuleRef.Invariant rule,Collection<FactSubject> positions) {
        Set<FactSubject> standing = left.get(rule);
        return standing != null && positions.stream().anyMatch(standing::contains);
    }

    /** A part of {@code rule} was taken in by nothing, of the positions it named. */
    void leftStanding(RuleRef.Invariant rule,Set<FactSubject> positions) {
        left.computeIfAbsent(rule, _ -> new LinkedHashSet<>()).addAll(positions);
    }

    /**
     * What the reading of values made of {@code rule}, as that reading recorded it.
     *
     * <p>Taken from the reading of this one clause and before it is met with the rest. What is met
     * is a set of values, and the reasons of every clause meet with it — so a caller asking the
     * whole what stopped it at a position is asking about the position and hearing whichever rule
     * reached it.
     *
     * <p><b>What the reading wrote down where it wrote it, and never a position's answer read
     * back.</b> What arrives is a {@link RuleShortfall}, made where the reading still had the
     * written place in hand. {@link AdmissibleValues#standing} answers for the position and is
     * deliberately not the source of this: it holds the reasons of every rule that reached the
     * place and names none of them, so an account built out of it is a list of reasons and no
     * clause. {@link AdmissibleValues#whyUnread} reads that record against the set the alternatives
     * arrived at and answers a third question — whether the set at a position is as narrow as the
     * rules leave it — and parts company with both exactly where a choice covers a position: the
     * set is exact and nothing is answerable for it, and the rule is still one nobody took in.
     *
     * <p><b>And only what a rule is answerable for.</b> Everything filed here is filed under a rule,
     * so a reason about no rule may not arrive: an allowance run down by everything a position
     * admits is a fact about the answer, and the same rules in another order would have been built.
     * Nothing is asked here because nothing of the wrong kind can be made: what arrives is refused
     * where it would be built, which is one fact away from where a caller could have written it.
     */
    void stoppedBy(RuleRef.Invariant rule,Set<RuleShortfall> read) {
        stopped.computeIfAbsent(rule, _ -> new LinkedHashSet<>()).addAll(read);
    }

    /**
     * Everything the reading of values was stopped by, of {@code rule} at any of {@code positions}.
     *
     * <p>Every name the position answers to, as {@link #tookIn} asks: a clause reaching it is filed
     * under whichever the reading recognised. Empty where this reading recorded nothing of the
     * rule there, which a caller has to answer for rather than fill in from the position.
     */
    Set<RuleShortfall> stoppedBy(RuleRef.Invariant rule,Collection<FactSubject> positions) {
        Set<RuleShortfall> here = stopped.get(rule);
        if (here == null) {
            return Set.of();
        }
        Set<RuleShortfall> out = new LinkedHashSet<>();
        here.forEach(each -> {
            if (positions.contains(each.position())) {
                out.add(each);
            }
        });
        return out;
    }

    /**
     * Whether anything took {@code rule} in, of a position named by any of {@code positions}.
     *
     * <p>Every name the position answers to, since a number is called one thing by the interval
     * algebra and another by everything else, and a clause reaching it is filed under whichever the
     * reading recognised.
     */
    boolean tookIn(RuleRef.Invariant rule,Collection<FactSubject> positions) {
        if (anyLeftStanding(rule, positions)) {
            return false;
        }
        Set<FactSubject> here = spokenFor.get(rule);
        return here != null && positions.stream().anyMatch(here::contains);
    }
}
