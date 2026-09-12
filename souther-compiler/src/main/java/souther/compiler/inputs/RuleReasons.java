package souther.compiler.inputs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the parts of a rule left a question standing on.
 *
 * <p><b>And no order.</b> Reasons stand in the order their author wrote the things they are about,
 * and saying which that is needs the source: what is here is which construct each of them is about
 * ({@link RuleSite}), counted over what the author wrote and holding no place at all. A carrier
 * that claimed an order would be claiming one over numbers nobody wrote — the ordinals a construct
 * is identified by are a function of the owner's syntax and are deliberately not the order it is
 * written in ({@code SourceConstructOrigin}) — so the claim is made where the places are, which is
 * the one boundary that has them ({@code AdequacyReport}).
 *
 * <p>It used to be made here, out of the positions each reason stood on. That is what put a place
 * inside every answer a reading published, and what made an edit moving a declaration an edit that
 * changed what the model says.
 *
 * <p>Held in the order they were met all the same, and that order is asserted of nothing: it is
 * what keeps one compiler over one source publishing one document. A copy free to reorder would
 * have the same compiler over the same source publish two.
 *
 * <p>So what is held is {@link Said} and not a word. Two reasons alike about two things inside one
 * rule are two things to lift, and a list of words says they are one.
 *
 * @param said each reason, what it is about, and where inside the rule it sends a reader
 */
public record RuleReasons(List<Said> said) {

    public RuleReasons {
        said = List.copyOf(said);
    }

    /**
     * The same, as the words alone, each once.
     *
     * <p>For a reader asking what a question stands on rather than where to go about it — whether a
     * wider run gets past it, which is a question about the kinds of thing and not about their
     * places. A caller counting these is counting kinds: two choices of one clause leave one word
     * here and two entries in {@link #said()}.
     */
    public List<BlockReason.RuleReadingStopped> reasons() {
        List<BlockReason.RuleReadingStopped> out = new ArrayList<>();
        for (Said each : said) {
            if (!out.contains(each.reason())) {
                out.add(each.reason());
            }
        }
        return List.copyOf(out);
    }

    /** Whether the question stands on nothing its rule left. */
    public boolean isEmpty() {
        return said.isEmpty();
    }

    /**
     * One reason, what it is about, and where inside the rule a reader is sent about it.
     *
     * <p>What tells two of these apart, and all three are needed for it. Two parts of one clause
     * stopped by one limit are one thing to lift and one of these; one clause whose ends two
     * choices left open is two, and nothing in the word says so.
     *
     * <p>{@code about} and {@code sentTo} are two answers and not one. What a reading decided about
     * is the construct its author wrote, which is what puts two of these in the order they were
     * written; where a reader goes about it is the whole rule for most of what this compiler is
     * short of, and a part of one for the rest. Held as one, a reason about a form nothing reads
     * would either send a reader inside a rule they have to rewrite whole, or lose the only thing
     * that says which form it was.
     *
     * @param about  what the reading decided it about, as its author wrote it
     * @param sentTo where inside the rule the reader goes — the rule itself for a reason about the
     *               whole of it, and something in it for one about a part
     * @param reason what the reading was short of, in this compiler's own terms
     */
    public record Said(RuleSite about, RuleSite sentTo, BlockReason.RuleReadingStopped reason) {

        public Said {
            if (about == null || sentTo == null || reason == null) {
                throw new IllegalArgumentException(
                        "a reason is about something, and says where a reader goes about it");
            }
        }
    }

    /**
     * These, each once, in a steady order that is nobody's.
     *
     * <p>Told apart by the reason, by what it is about and by where it sends a reader, which is
     * what makes two choices of one clause two entries. Kept by the word alone — which is what this
     * did while a word was the whole of what travelled — the second of them was dropped as a repeat
     * of the first.
     *
     * <p><b>Steady, and that is all it is.</b> Nothing here says which of two reasons an author
     * wrote first; what settles that is where they wrote them, and that is asked where the places
     * are. What the order must not be is the one a walk happened to meet them in: a projection out
     * of this reaches a document, so a sequence left to the walk would have one compiler over one
     * source publish two. So the members are put in the order their vocabulary declares them
     * ({@link BlockReason.RuleReadingStopped#inASteadyOrder}), and two alike in the word fall back
     * to the order they arrived in.
     */
    public static RuleReasons from(List<Said> these) {
        List<Said> sorted = new ArrayList<>(new LinkedHashSet<>(these));
        sorted.sort(Comparator.comparingInt(
                each -> BlockReason.RuleReadingStopped.inASteadyOrder(each.reason())));
        return new RuleReasons(List.copyOf(sorted));
    }

    /** One reason about the whole of its rule, which is most of what this compiler is short of. */
    public static RuleReasons one(BlockReason.RuleReadingStopped reason) {
        return one(RuleSite.theRuleItself(), reason);
    }

    /** The same, for a reader that has something inside the rule to send anybody to. */
    public static RuleReasons one(RuleSite sentTo, BlockReason.RuleReadingStopped reason) {
        return new RuleReasons(List.of(new Said(sentTo, sentTo, reason)));
    }
}
