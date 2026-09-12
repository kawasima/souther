package souther.compiler.check;

import souther.compiler.inputs.BlockReason;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Why a comparison naming a position did not become a line.
 *
 * <p>One rule, asked by every reader that has to say so. A {@code guard}'s comparison and an
 * invariant's clause are two producers of one kind of evidence (spec §example-partition), and what
 * stopped each of them is the same fact about this compiler — so a reader of either is told the
 * same thing (ADR-0090). Written twice, the two came apart at once: {@code x < y + 1} was a
 * comparison between two positions where a body wrote it and a form nobody could read where a
 * declaration did, which sends an author after two different pieces of work for one shape.
 *
 * <p>How a position is looked up stays with each reader, exactly as it does in {@link Relates}. One
 * asks what a body's read of a parameter names, the other asks what a clause's coordinate is
 * called; neither is the other's business. What is here is what the answers come to, which is the
 * part that has to agree.
 *
 * <p><b>Two questions, and which of them is asked turns on whether there is a quantity.</b> Where
 * the arithmetic reached one, the quantity is what the rule is about: it says what the rule leaves
 * and, through {@link #filedAt}, which of the places the walk met are the rule's at all. Where the
 * arithmetic stopped there is no quantity to be about, so each place is asked for itself and the
 * reader says what filing there means ({@link RuleAt}).
 *
 * <p>So no answer here is read off a side of the comparison. One was: the reason was worked out
 * once from whichever side named a position, and every place the comparison mentioned was handed
 * the result — after which a rule about {@code n} told {@code l1.l2.leaf} what {@code n}'s carrier
 * carries, and a position the canonical form had cancelled was told a form nobody could read
 * stopped at it.
 *
 * <p>What a position is called travels with the answers and is never read here, only compared with
 * another of its own. So the readers may hold a position under whatever name each of them uses and
 * still be held to one rule.
 */
public final class UnreadComparison {

    /**
     * What the arithmetic made of the comparison.
     *
     * <p>Two cases at the top and not four, because the two questions below divide here. A reading
     * that reached a quantity has a subject; one that stopped has none, and the difference decides
     * which of them a caller may ask. Flat, a caller with a stopped reading could ask for the
     * quantity's answer and be given one about a subject that was never established.
     *
     * <p><b>Each of them carries what it stands for.</b> A quantity over nothing is not a quantity,
     * and a reading that stopped is not one without somewhere it stopped — held as cases that can be
     * made from nothing, one of them stands in for another and whoever reads it reconstructs the
     * difference from whatever else is to hand.
     */
    public sealed interface Quantity<K> {

        /**
         * The arithmetic read the comparison to the end, and this is the quantity it cuts.
         *
         * <p>Which positions that quantity runs over is what the rule is about. A position the
         * canonical form cancelled is named by the comparison and is no subject of it: {@code a + b
         * - b + c <= 10} is {@code a + c <= 10}, and a note at {@code b} would say the rule relates
         * a position it does not mention.
         */
        sealed interface Read<K> extends Quantity<K> {}

        /**
         * The quantity this comparison cuts is over several positions, so it divides none of them.
         *
         * <p>Several and not one, by the type. A form over one position is a line on that
         * position as far as the arithmetic goes, and what a caller could make of it is a
         * different question with a different answer ({@link OverOne}) — counted as one of these
         * by the size of a set, the two were told apart by whoever held the set.
         */
        record OverSeveral<K>(Set<K> positions) implements Read<K> {

            public OverSeveral {
                positions = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(positions));
                if (positions.size() < 2) {
                    // A quantity over no position is what {@link CutsNothing} says and one over a
                    // single position is {@link OverOne}; saying either here lets a reader answer
                    // one of them for the other.
                    throw new IllegalArgumentException(
                            "a quantity over several positions is over at least two");
                }
            }
        }

        /**
         * The quantity this comparison cuts is over one position: the arithmetic read a line on it.
         *
         * <p>A caller holding one of these and still asking what would have to change is one whose
         * own reading of the line placed none — a reader of ends that takes a coordinate against
         * a written constant, handed {@code x + 1 <= 10}. That reading stopped, on a form the
         * arithmetic reads, and what it stopped on is the position and its carrier: nothing about
         * how the sides were spelled adds to that, and asked of the sides this answered about a
         * shape the arithmetic had already got past.
         */
        record OverOne<K>(K position) implements Read<K> {

            public OverOne {
                if (position == null) {
                    throw new IllegalArgumentException("a quantity over one position names it");
                }
            }
        }

        /**
         * The arithmetic read the whole comparison and there is no quantity to cut: a comparison of
         * constants, or one whose positions cancel.
         *
         * <p>Its own answer and not the one below. {@code a - a > 0} is read to the end and divides
         * no position, which is a fact about the rule; folded together with a reading that stopped,
         * it was reported as a rule written in a form this compiler cannot read — of a form this
         * compiler read completely.
         *
         * <p>The one quantity whose places are not its support. There is nothing in the support to
         * file at, and what makes the rule worth saying at all is that the model names a position
         * there and cuts nothing — so this alone is filed where the comparison names.
         */
        record CutsNothing<K>() implements Read<K> {}

        /**
         * The arithmetic read no form here, and what it was looking at when it stopped.
         *
         * <p>The expression, because the walk that stopped is the only thing that knows which one it
         * was. Told only that there was no form, a reader had to work the difficulty out from the
         * shape of the whole comparison, and the shape it reached for first was whichever side held
         * an operation — so a rule over an operation this reads perfectly well was blamed for a form
         * it could not read beside it, and would go on being blamed as more operations became
         * readable.
         *
         * @param stoppedAt what the expression with no rule here is made of
         */
        record NotRead<K>(ValueOrigin<K> stoppedAt) implements Quantity<K> {

            public NotRead {
                if (stoppedAt == null) {
                    throw new IllegalArgumentException(
                            "a reading that read no form stopped somewhere");
                }
            }
        }
    }

    /**
     * Which of the places a walk met a read comparison is filed at, in the order it met them.
     *
     * <p><b>The quantity says which, and the walk says only which came first.</b> Membership taken
     * from the walk, a rule was filed at positions its own canonical form had cancelled — and the
     * word it left there was the word for the position the quantity does cut. Taken as an
     * intersection, the walk keeps a veto it is not entitled to: a place the quantity runs over
     * that the walk did not name would go quietly missing, and the answer would be a filing the
     * rule is short of with nothing saying so. So a quantity running over a place the walk never
     * met is a disagreement between two readings of one comparison, and it is refused here.
     *
     * <p>{@link Quantity.CutsNothing} is the exception, and a deliberate one: its support is empty
     * and there would be nothing to file, while what the rule is worth saying for is that the model
     * states something at the positions it names and cuts none of them.
     */
    public static <K> List<K> filedAt(Quantity.Read<K> read, List<K> met) {
        return switch (read) {
            case Quantity.CutsNothing<K> _ -> List.copyOf(met);
            case Quantity.OverOne<K> one -> over(met, Set.of(one.position()));
            case Quantity.OverSeveral<K> several -> over(met, several.positions());
        };
    }

    /** The places {@code runsOver} names, in the order the walk met them. */
    private static <K> List<K> over(List<K> met, Set<K> runsOver) {
        List<K> out = new ArrayList<>();
        for (K each : met) {
            if (runsOver.contains(each)) {
                out.add(each);
            }
        }
        if (out.size() != runsOver.size()) {
            // The walk names the places and the quantity says which of them the rule is about, so
            // one of them holding a place the other has never heard of is two readings of one
            // comparison disagreeing. Filed as whatever they have in common, the rule would go out
            // short of a place with nothing to say it was ever expected.
            throw new IllegalStateException(
                    "the quantity runs over " + runsOver + ", which the walk met as " + met);
        }
        return out;
    }

    /**
     * What a rule filed at {@code position} is about, for the reader that chose to file it there.
     *
     * <p><b>The law both readers are held to.</b> Whether the rule states something about the
     * values standing at a place is settled by the comparison — a whole side of it is that position
     * and nothing else — and a reader answering that for itself is how a clause and a body's
     * comparison of one shape came to different words. What a reader supplies is what it calls the
     * place, which is the part only it knows.
     *
     * <p>{@code s < Won} is about the values of {@code s} whatever the reading made of the other
     * side. {@code n < l1.l2.leaf.x} says nothing about the values of {@code l1.l2.leaf}, which is
     * where a walk met a position on its way through an expression it could not take apart.
     *
     * <p>A number taken of the position needs nothing here. Where a reader files at one, the
     * carrier it goes on to ask about is that number's and not the position's underneath —
     * {@code String.length(s)} is asked what lengths are counted on — so the two answers this could
     * give come to the same word. Written as a case of its own, it would be a branch nothing can
     * make a difference to.
     */
    public static <K> RuleAt<K> subjectAt(K position, ValueOrigin<K> left, ValueOrigin<K> right) {
        return isExactly(left, position) || isExactly(right, position)
                ? new RuleAt.AboutOwnValues<>(position)
                : new RuleAt.NotAboutOwnValues<>();
    }

    /** Whether a whole side of the comparison is the position at {@code position}, and nothing
     *  else. */
    private static <K> boolean isExactly(ValueOrigin<K> side, K position) {
        return side instanceof ValueOrigin.IsAPosition<K> one && one.at().equals(position);
    }

    /**
     * What a comparison the arithmetic read to the end leaves, at the places it is filed at.
     *
     * <p>One answer for all of them, because the quantity is one subject. Which places those are is
     * {@link #filedAt}'s, and every one of them is a position this quantity runs over — or, for a
     * quantity that runs over none, a position the rule names and cuts nothing of.
     *
     * @param ordered whether a line can be drawn on what a position carries, asked of the carrier.
     *                Asked of the reader because a position is looked up there, and asked at all
     *                only about the one position a quantity over one is on
     */
    public static <K> BlockReason.RuleWithoutLineReason ofTheQuantity(Quantity.Read<K> read,
                                                                     Predicate<K> ordered) {
        return switch (read) {
            // Read to the end and there is no quantity. Nothing about how it was written adds to
            // that: no reading fell short, so no question about what could not be read arises.
            case Quantity.CutsNothing<K> _ -> new BlockReason.ComparisonCuttingNothing();
            case Quantity.OverSeveral<K> _ -> new BlockReason.ComparisonBetweenPositions();
            // A line on one position that the caller's own reading placed nowhere: that reading
            // stopped, and what it stopped on is the position. The carrier says which limit — a
            // position nothing draws a line on wants the carrier, and one lines are drawn on all
            // through the file wants a reader of the form.
            case Quantity.OverOne<K> one -> whereALineWouldFall(ordered.test(one.position()));
        };
    }

    /**
     * What a rule stating where the values at one place stop is left with, where no line came of
     * it.
     *
     * <p>The carrier and nothing else. A position nothing draws a line on wants that carrier read;
     * one lines are drawn on all through the file wants a reader for the form the rule is written
     * in. Which of the two it is turns on the place and on nothing about the comparison, so a
     * question raised at one coordinate has one answer however many parts of a rule raise it — and
     * that is what lets such a question be answered rather than gathered.
     */
    public static BlockReason.RuleReadingStopped whereALineWouldFall(boolean ordered) {
        return ordered ? new BlockReason.UnreadComparisonForm()
                : new BlockReason.UnreadComparisonDomain();
    }

    /**
     * What a reading that stopped leaves at one place it was filed at.
     *
     * <p>Typed as a stop, because that is what every answer here is: the arithmetic stopped, and
     * what is left to decide is which limit it stopped on. A caller that has met a stop and wants
     * the words for it asks this, and cannot be handed the words for a rule read to the end —
     * those are answers about a quantity, and this is never given one.
     *
     * <p><b>Every answer is about the place, and none about the expression as a whole.</b> The
     * carrier is asked only where the rule states something about the values standing there, which
     * the reader says: asked at every place, a rule about {@code n} answered about what
     * {@code l1.l2.leaf} carries, because that is where a walk met a position on its way through an
     * expression it could not take apart.
     *
     * <p>And whether an operation made the value the rule speaks of is asked only where the rule is
     * not about the values standing here. Asked first, of the stopped expression, it answered over
     * a place whose own values the rule plainly states something about: {@code s < Won} would be a
     * rule about a value an operation made the day an operation appeared beside it.
     *
     * <p><b>So the answer at a place a rule is about the values of is a function of that place.</b>
     * Both of the words such a place can be left with are read off the carrier, and the carrier is
     * the position's. Which is what lets a question raised at one coordinate be answered once
     * rather than gathered from the conjuncts that asked it.
     */
    public static <K> BlockReason.RuleReadingStopped whereItStopped(RuleAt<K> at,
                                                                   Quantity.NotRead<K> notRead,
                                                                   Predicate<K> ordered) {
        return switch (at) {
            // The values here are what the rule speaks of, so what they are carried on says which
            // limit stopped the reading: `at < DateTime(...)` stops because nothing draws a line on
            // a date-time, while `p.x < 1 + 2` stops because the other side is not a form a
            // threshold is read out of and `p.x` is an `Int` — a carrier lines are drawn on all
            // through the file.
            case RuleAt.AboutOwnValues<K> own ->
                    whereALineWouldFall(ordered.test(own.position()));
            // And where they are not, what is left is which of two ways they are not. A value an
            // operation made of what stands here is a rule to be followed back through that
            // operation; anything else is a form this did not read, and which number of the
            // position it was about is the part that went unread.
            case RuleAt.NotAboutOwnValues<K> _ -> notAboutOwnValues(notRead.stoppedAt());
        };
    }

    /**
     * The same at a place the rule states nothing about the values of, from what the walk stopped
     * at and from nothing else.
     *
     * <p>Its own way in because it is asked on both sides of the arrangement: by a reading that
     * gave up on a comparison, and by the classification of a clause, which has to say the same
     * thing about the same expression without asking what any reading came back with. What it reads
     * is the expression — {@link ValueOrigin} is what a side is made of — so both callers are
     * looking at the model.
     */
    public static <K> BlockReason.RuleReadingStopped notAboutOwnValues(ValueOrigin<K> stoppedAt) {
        return madeByAnOperation(stoppedAt)
                ? new BlockReason.RuleAboutADerivedValue()
                : new BlockReason.UnreadComparisonForm();
    }

    /** Whether what a reading stopped at is a value an operation made of a position. */
    private static <K> boolean madeByAnOperation(ValueOrigin<K> stoppedAt) {
        return switch (stoppedAt) {
            case ValueOrigin.Applied<K> _, ValueOrigin.MadeFromAPosition<K> _ -> true;
            // Arithmetic the terms do not take apart — unless what is under it came from a
            // position, which is the same rule about a value made from one with a layer of
            // arithmetic over it.
            case ValueOrigin.Composed<K> composed -> composed.madeFrom() != null;
            // Every value it could be, and not any of them. The word this picks promises the
            // position was found and only following the operation back is missing; where one arm of
            // a choice is a position's own values or a literal, that promise is false and what an
            // author is owed is the other word. What decided the choice is not asked: an operation
            // over the test made none of the values the rule is about.
            case ValueOrigin.OneOf<K> choice ->
                    choice.alternatives().stream().allMatch(UnreadComparison::madeByAnOperation);
            case ValueOrigin.IsAPosition<K> _, ValueOrigin.Written<K> _,
                 ValueOrigin.Unnameable<K> _ -> false;
        };
    }

    private UnreadComparison() {}
}
