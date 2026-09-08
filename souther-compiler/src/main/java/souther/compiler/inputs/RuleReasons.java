package souther.compiler.inputs;

import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the parts of a rule left a question standing on, and whether that is an order anybody wrote.
 *
 * <p>The one place the claim is made. A reason stands at a place somebody wrote, and reasons written
 * in one text stand in the order that text puts them in; reasons written in two texts stand in no
 * order at all, because nothing an author did says which file comes first. Both of those are
 * answers, and a carrier with only the first would have to make the second up.
 *
 * <p><b>Where the places are still in hand.</b> Downstream of here the reasons are words, and a word
 * says nothing about where it was written — which is how an order arrived at by walking came to be
 * published as the author's and was right about it only while one walk produced every member. So
 * the claim is made here, out of the places, and everything after carries what was decided rather
 * than deciding again.
 */
public sealed interface RuleReasons {

    /** What is held, as the words a reader asks for. */
    List<BlockReason.RuleReadingStopped> reasons();

    /** Whether the question stands on nothing its rule left. */
    default boolean isEmpty() {
        return reasons().isEmpty();
    }

    /**
     * Reasons of one text, in the order the places they stand on were written.
     *
     * <p>An order of the model, which is what {@link AuthoredOrder} means and is why it is the
     * thing held rather than a list beside a flag.
     */
    record AsWritten(AuthoredOrder<BlockReason.RuleReadingStopped> order) implements RuleReasons {

        public AsWritten {
            if (order == null) {
                throw new IllegalArgumentException("an authored order is some order");
            }
        }

        @Override
        public List<BlockReason.RuleReadingStopped> reasons() {
            return order.written();
        }
    }

    /**
     * Reasons written across more than one text, which no order of anybody's runs across.
     *
     * <p>Steady, so that one compiler over one source says the same thing twice; and steady is all
     * it is. What settles it is not a fact about the model, so nothing may be read off it — a reader
     * acting on this order is acting on which text this compiler compared first.
     */
    record NoSingleAuthoredOrder(List<BlockReason.RuleReadingStopped> reasons)
            implements RuleReasons {

        public NoSingleAuthoredOrder {
            reasons = List.copyOf(reasons);
        }
    }

    /**
     * One reason and the place it stands on.
     *
     * @param writtenAt where the part that raised it was written
     * @param reason what it left, in the vocabulary a question stands on
     */
    record Placed(SourcePos writtenAt, BlockReason.RuleReadingStopped reason) {

        public Placed {
            if (writtenAt == null || reason == null) {
                throw new IllegalArgumentException("a reason stands on a place, and says which");
            }
        }
    }

    /**
     * These, in the order they were written where that is an order.
     *
     * <p><b>Which order it is, is decided before anything is compared.</b> What may be compared is
     * what the answer turns out to be: a line and a column are a place inside one text and two
     * numbers outside it, so a walk that sorted first and asked afterwards would have ordered the
     * ones it had no order for and then said so. So the texts are counted first and each branch
     * compares only what it has.
     */
    static RuleReasons from(List<Placed> these) {
        return oneTextHoldsThem(these) ? inOneText(these) : acrossTexts(these);
    }

    /**
     * Whether one source of this compile holds every one of these.
     *
     * <p>Asked of the source and not of the text a position says it is in. A position read from no
     * source of this compile is not in a text of anybody's — it is a pair of numbers this compiler
     * minted — and two of those are not in one text together however alike they compare. Asked the
     * other way, an order somebody wrote would be claimed over numbers nobody wrote, which is what
     * this whole carrier is here to stop.
     */
    private static boolean oneTextHoldsThem(List<Placed> these) {
        Set<SourceId> sources = new LinkedHashSet<>();
        for (Placed each : these) {
            if (!(each.writtenAt().quotedFrom()
                    instanceof QuotedFrom.ASourceThisCompileHolds(SourceId source))) {
                return false;
            }
            sources.add(source);
        }
        return sources.size() == 1;
    }

    /**
     * Of one text, in the order the places were written.
     *
     * <p>Sorted by the place first and folded second, which is what makes the entry the earliest
     * place a reason stands on rather than the first the walk met. Two reasons at one place are
     * told apart by nothing an author wrote, so they fall back to the order below.
     */
    private static RuleReasons inOneText(List<Placed> these) {
        List<Placed> sorted = new ArrayList<>(these);
        sorted.sort(Comparator.comparingInt((Placed each) -> each.writtenAt().line())
                .thenComparingInt(each -> each.writtenAt().column())
                .thenComparingInt(each -> canonical(each.reason())));
        return new AsWritten(AuthoredOrder.asWritten(distinct(sorted)));
    }

    /**
     * Of more than one, in the order this file declares and in nothing of anybody's.
     *
     * <p>No place is looked at. There is no order across texts to find, and comparing the numbers
     * anyway would put a reason of one file before a reason of another for no reason at all — then
     * hand the result over under a name that says as much, which is a value telling the truth about
     * a sequence arrived at by a comparison that means nothing.
     */
    private static RuleReasons acrossTexts(List<Placed> these) {
        List<Placed> sorted = new ArrayList<>(these);
        sorted.sort(Comparator.comparingInt(each -> canonical(each.reason())));
        return new NoSingleAuthoredOrder(distinct(sorted));
    }

    /** Each reason once, keeping the first of them in whatever order they arrive in. */
    private static List<BlockReason.RuleReadingStopped> distinct(List<Placed> sorted) {
        List<BlockReason.RuleReadingStopped> out = new ArrayList<>();
        for (Placed each : sorted) {
            if (!out.contains(each.reason())) {
                out.add(each.reason());
            }
        }
        return out;
    }

    /**
     * One reason, which is in the order it was written by there being nothing to order it against.
     *
     * <p>Here so that {@link AuthoredOrder} is made in this file and nowhere else. A caller holding
     * one reason and reaching for the order itself would be a second place saying what an authored
     * order is, and the next caller to reach for it would have two examples to follow.
     */
    static RuleReasons one(BlockReason.RuleReadingStopped reason) {
        return new AsWritten(AuthoredOrder.asWritten(List.of(reason)));
    }

    /**
     * The order this file puts two reasons in when nothing an author wrote tells them apart.
     *
     * <p>Written out, and that is the point of it. What is wanted here is an order, and a sealed
     * type is a set of members rather than a sequence of them — {@code getPermittedSubclasses} says
     * so itself, answering in no order it specifies, so a walk reading its array as a sequence is
     * taking an order from something that has none. That is the mistake this whole carrier exists
     * to stop, one level down.
     *
     * <p>A switch and no {@code default}, so a reason added to the vocabulary is placed by whoever
     * adds it rather than arriving wherever the runtime happened to put it. What the numbers mean
     * is nothing beyond which comes first: they are read only against each other, and only where
     * the source has already been asked and had nothing to say.
     */
    private static int canonical(BlockReason.RuleReadingStopped reason) {
        return switch (reason) {
            case BlockReason.UnreadComparisonForm _ -> 0;
            case BlockReason.UnreadComparisonDomain _ -> 1;
            case BlockReason.ValueRuleRelatingTwoPositions _ -> 2;
            case BlockReason.CasePairingNotDetermined _ -> 3;
            case BlockReason.RuleAboutADerivedValue _ -> 4;
            case BlockReason.UnreadValueRule _ -> 5;
            case BlockReason.PatternTooDeeplyNested _ -> 6;
            case BlockReason.PatternTooCostly _ -> 7;
            case BlockReason.OrderedExtentTooCostly _ -> 8;
            case BlockReason.RuleAboutAnElementOfSeveralSequences _ -> 9;
            case BlockReason.EndLeftOpenByAChoice _ -> 10;
        };
    }
}
