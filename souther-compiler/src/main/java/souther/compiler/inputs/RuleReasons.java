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
 * <p><b>Where the places are still in hand.</b> Downstream of here nothing sees a source again, so
 * both things a place answers are settled here: which order the reasons stand in, and where inside
 * the rule each of them sends a reader ({@link WhereInTheRule}). The order was already decided here
 * — a walk deciding it later was right only while one producer supplied every member. The second
 * used to be dropped here, which left a reason about a choice indistinguishable from one about the
 * clause around it.
 *
 * <p>So what is held is {@link Said} and not a word. Two reasons alike about two places inside one
 * rule are two things to lift, and a list of words says they are one.
 */
public sealed interface RuleReasons {

    /** What is held: each reason with where inside the rule it sends a reader. */
    List<Said> said();

    /**
     * The same, as the words alone, each once.
     *
     * <p>For a reader asking what a question stands on rather than where to go about it — whether a
     * wider run gets past it, which is a question about the kinds of thing and not about their
     * places. A caller counting these is counting kinds: two choices of one clause leave one word
     * here and two entries in {@link #said()}.
     */
    default List<BlockReason.RuleReadingStopped> reasons() {
        List<BlockReason.RuleReadingStopped> out = new ArrayList<>();
        for (Said each : said()) {
            if (!out.contains(each.reason())) {
                out.add(each.reason());
            }
        }
        return List.copyOf(out);
    }

    /** Whether the question stands on nothing its rule left. */
    default boolean isEmpty() {
        return said().isEmpty();
    }

    /**
     * One reason, and where inside the rule a reader is sent about it.
     *
     * <p>What tells two of these apart, and both halves are needed for it. Two parts of one clause
     * stopped by one limit are one thing to lift and one of these; one clause whose ends two
     * choices left open is two, and nothing in the word says so.
     *
     * @param sentTo where inside the rule the reader goes — the rule itself for a reason about the
     *               whole of it, and a place in it for one about a part
     * @param reason what the reading was short of, in this compiler's own terms
     */
    record Said(WhereInTheRule sentTo, BlockReason.RuleReadingStopped reason) {

        public Said {
            if (sentTo == null || reason == null) {
                throw new IllegalArgumentException(
                        "a reason stands somewhere in the rule, and says which");
            }
        }
    }

    /**
     * Reasons of one text, in the order the places they stand on were written.
     *
     * <p>An order of the model, which is what {@link AuthoredOrder} means and is why it is the
     * thing held rather than a list beside a flag.
     */
    record AsWritten(AuthoredOrder<Said> order) implements RuleReasons {

        public AsWritten {
            if (order == null) {
                throw new IllegalArgumentException("an authored order is some order");
            }
        }

        @Override
        public List<Said> said() {
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
    record NoSingleAuthoredOrder(List<Said> said) implements RuleReasons {

        public NoSingleAuthoredOrder {
            said = List.copyOf(said);
        }
    }

    /**
     * One reason, the place it stands on, and where inside the rule it sends a reader.
     *
     * <p>Two places and they answer different questions. {@code writtenAt} is what an order among
     * these is taken over and is read against the other members and nothing else. {@code sentTo} is
     * what a reader is given, and it survives this file — which is why a reason about a part of a
     * rule has to arrive here already saying so, rather than being worked out from the position
     * afterwards.
     *
     * @param writtenAt where the part that raised it was written
     * @param sentTo where inside the rule a reader goes about it
     * @param reason what it left, in the vocabulary a question stands on
     */
    record Placed(SourcePos writtenAt, WhereInTheRule sentTo,
                  BlockReason.RuleReadingStopped reason) {

        public Placed {
            if (writtenAt == null || sentTo == null || reason == null) {
                throw new IllegalArgumentException("a reason stands on a place, and says which");
            }
        }

        /** What is kept of it once the order is settled. */
        Said said() {
            return new Said(sentTo, reason);
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
        sorted.sort(Comparator.comparing((Placed each) -> each.writtenAt(),
                        SourcePos.IN_WRITTEN_ORDER)
                .thenComparingInt(each -> canonical(each.reason())));
        return new AsWritten(AuthoredOrder.asWritten(said(sorted)));
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
        return new NoSingleAuthoredOrder(said(sorted));
    }

    /**
     * Each of them once, keeping the first in whatever order they arrive in.
     *
     * <p>Told apart by the reason and by where it sends a reader, which is what makes two choices of
     * one clause two entries. Kept by the word alone — which is what this did while a word was the
     * whole of what travelled — the second of them was dropped as a repeat of the first.
     */
    private static List<Said> said(List<Placed> sorted) {
        Set<Said> out = new LinkedHashSet<>();
        for (Placed each : sorted) {
            out.add(each.said());
        }
        return List.copyOf(out);
    }

    /**
     * One reason, which is in the order it was written by there being nothing to order it against.
     *
     * <p>Here so that {@link AuthoredOrder} is made in this file and nowhere else. A caller holding
     * one reason and reaching for the order itself would be a second place saying what an authored
     * order is, and the next caller to reach for it would have two examples to follow.
     */
    static RuleReasons one(BlockReason.RuleReadingStopped reason) {
        return one(WhereInTheRule.theRuleItself(), reason);
    }

    /** The same, for a reader that has a place inside the rule to send anybody to. */
    static RuleReasons one(WhereInTheRule sentTo, BlockReason.RuleReadingStopped reason) {
        return new AsWritten(AuthoredOrder.asWritten(List.of(new Said(sentTo, reason))));
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
            case BlockReason.ValueRuleLeftOpenByAChoice _ -> 11;
        };
    }
}
