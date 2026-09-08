package souther.compiler.partition;

import souther.compiler.check.CoverageObligation;
import souther.compiler.inputs.BlockedDescent;
import souther.compiler.inputs.RulesLeftUnread;
import souther.compiler.inputs.StandingQuestion;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether the reading one measure of coverage depends on came to an end.
 *
 * <p>Two types and not one value shared between them, because the two measures are short of
 * different things. A rule whose line nothing could read leaves the border measure short while the
 * classes either side of it were read in full; a walk that never reached a position's rules leaves
 * both short. Written as one answer about the behavior, the first case reports the partition
 * measure on the strength of what stopped the other one — and the day a third measure arrives it
 * inherits whatever the two of them happened to share.
 *
 * <p><b>Closed is a conclusion, and only this class draws it.</b> Each {@code Closed} has a private
 * constructor and is reached through {@link #of}, which is handed the whole of what a behavior's
 * reading came to. Nowhere else is there a way to make one, so a caller holding one is holding the
 * proof that the reading ran out — the arrangement {@link UndividedPosition.Why.Absent} is under, at
 * the measure rather than at a position.
 *
 * <p><b>The questions, and nothing but the questions and where the walk did not go.</b> What a
 * measure is short of is a rule of the model that left something open: a question about a subject
 * that nothing answered, filed under the measure that answers it
 * ({@link CoverageObligation#answeredBy}), or a rule this compiler did not read far enough to say
 * what it raises, which leaves both open. Counted as "every reader ran to the end" instead, a
 * completeness says the model was read in full for exactly as long as nobody adds a reader.
 *
 * <p><b>And two facts no question carries.</b> A position whose rules were never enumerated
 * raises none, and a set of raised questions all answered is what a reading that never looked also
 * produces ({@link ReadingResidue}). And a rule the model wrote that this could not use is one
 * whose question a reading may well have answered before the position refused the answer — two
 * clauses placing ends on a {@code String}'s two coordinates are read, accounted for, and then
 * both dropped, and the accounting is right to say they were read. What an accounting cannot say
 * is that the measure was then left with nothing, and that is what this reads the refusals for.
 *
 * <p>Which measures each of these costs is each one's own answer, and they do not agree. A position
 * whose rules were never enumerated and one the walk could not reach into leave both short, because
 * what is not known about them is not known for either.
 *
 * <p><b>One stop, one gap, and the fold is on where the gap came from.</b> Two of the things a
 * reading can be short of are one stop said from two ends: the walk could not go into a position,
 * and so the rules it handed on reached nobody. The second is a consequence of the first, and the
 * arm that says so is what this reads to leave it out ({@link #derived}). Never the path — a
 * position the reading did enter and lost a clause of its own is an independent finding sitting at
 * the same place, and a fold on "there is already something here" takes that one with it (#1084).
 *
 * <p><b>And a question stands whatever else was found at its position.</b> A question comes off a
 * rule this compiler read and neither reader answered, so the rules a stop left unread raise none —
 * which means a suppression by position could only ever reach the real ones. What it did reach was a
 * rule about a {@code Map}'s size that nothing answered, dropped because the map's contents are out
 * of reach, which is a fact about other rules entirely.
 *
 * <p><b>A comparison leaves what any other rule leaves.</b> Where the reading of one reached a line,
 * that line is the answer and the comparison leaves nothing; where it did not, what the comparison
 * raises is the part that was not read, and it stands here as that. Which is the same sentence a
 * clause of a declaration gets: what holds a measure open is a question about a rule, and never a
 * word about the model that something read a flag off.
 */
public final class MeasureClosure {

    private MeasureClosure() {}

    /** Whether the partition measure's reading ran out. */
    public sealed interface OfThePartition {

        /**
         * It did: every question this measure answers was accounted for, everywhere.
         *
         * <p>Two of these are one conclusion and compare equal. Costing something to say is about
         * who may say it and not about which instance said it — an answer that never equals its own
         * recomputation is one {@code Db} reports as changed on every run, and everything that read
         * it runs again over a model nobody edited. {@link UndividedPosition.Why.Absent} carries
         * the same pair for the same reason.
         */
        final class Closed implements OfThePartition {

            private Closed() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Closed;
            }

            @Override
            public int hashCode() {
                return Closed.class.hashCode();
            }

            @Override
            public String toString() {
                return "PartitionClosed";
            }
        }

        /**
         * It did not, and this is what was still open when it stopped.
         *
         * <p>Never empty. Something has to have been found for the reading not to have run out, so
         * an open closure carrying nothing is a measure that would come back weaker than complete
         * with no account of why — which is the whole of issue #953.
         */
        record Open(Set<ClosureGap> by) implements OfThePartition {

            public Open {
                by = gaps(by);
            }
        }
    }

    /** Whether the border measure's reading ran out. The same question of the other measure, and a
     *  separate type so that neither can be answered with the other's answer. */
    public sealed interface OfTheBorder {

        /** It did, and two of these are one conclusion — see {@link OfThePartition.Closed}. */
        final class Closed implements OfTheBorder {

            private Closed() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Closed;
            }

            @Override
            public int hashCode() {
                return Closed.class.hashCode();
            }

            @Override
            public String toString() {
                return "BorderClosed";
            }
        }

        /** It did not, and this is what was still open. Never empty, for the reason
         *  {@link OfThePartition.Open} gives. */
        record Open(Set<ClosureGap> by) implements OfTheBorder {

            public Open {
                by = gaps(by);
            }
        }
    }

    /** What the two closures come to, together, so that neither is built from half a reading. */
    record Both(OfThePartition partition, OfTheBorder border) {}

    /**
     * What each measure's reading came to over one behavior.
     *
     * <p>Everything the reading could be short of arrives here at once. Asked measure by measure
     * afterwards, the two would be built from whatever their caller had in hand at the time, which
     * is the second bookkeeping this type exists to prevent.
     *
     * @param positions every position the reading kept, measured or not
     * @param lines   what the line reading of this behavior found and what it made of each, from
     *                both of the producers there are. Here so that a conclusion about the reading is
     *                drawn from what it produced beside what it found, and not from the gaps alone
     *                — see {@link LinesRead#everyLineFoundWasDrawn}
     * @param asked   everything the rules of the model left open, whether or not anything worked
     *                out what each of them raises. A rule this reading set aside is here or is
     *                nowhere: a rule read from end to end that draws no line leaves nothing open,
     *                and one whose reading stopped is a question about a rule that says as much
     */
    static Both of(List<PositionAccount> positions,
                   List<StandingQuestion> asked, LinesRead lines) {
        lines.everyLineFoundWasDrawn();
        Gathering partition = new Gathering();
        Gathering border = new Gathering();
        // Over the positions and not over the measures made of them. What a reading of a position
        // came to is the position's, and a location is measured at as many numbers as the rules
        // name of it: read off the measures, one stop at one location is one entry per number, which
        // is a compiler's own state counted several times.
        for (PositionAccount at : positions) {
            // Read off what the reading settled and never off what the position is still waiting
            // on. A position a body's rule divides keeps no continuation, and was still never
            // entered: asked of the continuation, the one model where the two come apart said
            // nothing at all about the position it could not read (issue #1084).
            BlockedDescent blocked = at.residue().blockedDescent();
            if (blocked != null) {
                ClosureGap gap = new ClosureGap.PositionNotReachedInto(at.behavior(), at.id(),
                        blocked.why());
                partition.add(gap);
                border.add(gap);
            }
            // And the ends a choice left open, which is the border's alone: what the alternatives
            // admit was read, and where they stop was not.
            for (souther.compiler.check.RuleRef rule : at.endsLeftOpenByAChoice()) {
                border.add(new ClosureGap.LineNotDerived(at.behavior(), at.id(), rule));
            }
            for (RulesLeftUnread unread : at.residue().rulesLeftUnread()) {
                if (derived(unread)) {
                    continue;
                }
                ClosureGap gap = new ClosureGap.RulesNotReached(at.behavior(), at.id());
                partition.add(gap);
                border.add(gap);
            }
        }
        // Every question that stands, whatever else was found at the same path. A question is
        // raised by a rule this compiler read and neither reader answered, so the rules a stop left
        // unread raise none — and the only questions a suppression here could ever reach are the
        // real ones. Written as "there is already a finding at this path", a rule about a `Map`'s
        // size that nothing answered went unsaid because the walk could not read what the map holds,
        // which are two facts about two different rules (issue #1084).
        for (StandingQuestion each : asked) {
            ClosureGap gap = ClosureGap.QuestionUnanswered.of(each);
            if (each.holdsOpen(CoverageObligation.Measure.PARTITION)) {
                partition.add(gap);
            }
            if (each.holdsOpen(CoverageObligation.Measure.BOUNDARY)) {
                border.add(gap);
            }
        }
        return new Both(
                partition.isEmpty()
                        ? new OfThePartition.Closed() : new OfThePartition.Open(partition.gaps()),
                border.isEmpty()
                        ? new OfTheBorder.Closed() : new OfTheBorder.Open(border.gaps()));
    }

    /**
     * What one measure's reading was left open by, each fact once.
     *
     * <p>One stop met twice is one stop. Which handle a reader is sent to and what each reading of
     * a question was short of are accumulated rather than kept apart, so that a gap found from two
     * readers is one gap carrying both — and what makes two of these one is
     * {@link ClosureGap#fact()}, asked of the gap rather than decided here.
     *
     * <p>Keyed and not ordered. Nothing here says which gap comes before which, and a reader that
     * puts them in a sequence says which order it means.
     */
    private static final class Gathering {

        private final Map<Object, ClosureGap> byFact = new HashMap<>();

        void add(ClosureGap gap) {
            byFact.merge(gap.fact(), gap, ClosureGap::merged);
        }

        boolean isEmpty() {
            return byFact.isEmpty();
        }

        Set<ClosureGap> gaps() {
            return Set.copyOf(byFact.values());
        }
    }

    /**
     * Whether this way of leaving the rules unread is already reported by a finding beside it.
     *
     * <p><b>The fold, and the whole of it.</b> One way of leaving the rules unread is a consequence
     * of another finding rather than a second thing that went wrong: a handing over nobody took
     * because the walk could not go into the position is the blocked descent written above, said
     * from the other end. Both sentences are true and a reader is told the stop once.
     *
     * <p><b>On the provenance and never on the path.</b> A position the reading did enter and lost a
     * clause of its own is an independent finding, and it can sit at the same path as anything else.
     * Suppressed by "there is already a finding here", that one would go with it — which is why this
     * asks the arm and is handed nothing else to decide from.
     *
     * <p>Exhaustive with no {@code default}: a way of leaving the rules unread added later is
     * answered here or is a compile error, rather than quietly folding into somebody else's finding.
     */
    private static boolean derived(RulesLeftUnread unread) {
        return switch (unread) {
            // The reading was there and lost a rule of its own. Nothing else says so.
            case RulesLeftUnread.ClauseOfThisReadingWasUnread _ -> false;
            case RulesLeftUnread.Handoff handoff -> switch (handoff.why()) {
                case RulesLeftUnread.HandoffUnread.FromBlockedDescent _ -> true;
                // The walk went on and left a recipient with no reading. Nothing else says so.
                case RulesLeftUnread.HandoffUnread.NotFullyAccepted _ -> false;
            };
        };
    }

    /** An open closure's own account of itself: never empty, and in the order the readers found it,
     *  so that two runs over one model produce the same value. */
    private static Set<ClosureGap> gaps(Set<ClosureGap> by) {
        if (by == null || by.isEmpty()) {
            throw new IllegalArgumentException(
                    "a reading that did not run out has something it did not get to");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(by));
    }
}
