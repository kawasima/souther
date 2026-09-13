package souther.compiler.partition;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.regex.Meter;

import java.util.ArrayList;
import java.util.List;

/**
 * What did not reach the values offered at a position, and what stopped each of them.
 *
 * <p>Not what the position admits, and not a search that stopped. A value to paste into a row is
 * composed from the rules this compiler read, and where one of them gave nothing the value came
 * from the rules beside it. Every one of those values is still put to the decoder and every row
 * that comes out is a row the model accepts — what is lost is the right to say that what was
 * offered was everything there was to offer.
 *
 * <p><b>One entry per thing that gave nothing, and each says which thing.</b> A position carrying
 * two rules about its strings, one of them written in a construct this compiler does not enter, is
 * a position an author fixes by rewriting that one — and a shortfall saying only that some rule
 * here could not be read sends them to read both. So the rule is carried, in the identity a report
 * already names rules by, and the words for it are the report's.
 *
 * <p><b>And what stopped it, in the vocabulary that already tells those apart.</b> A rule written
 * in a construct nothing here reads and one written more deeply than this reads are different work
 * for an author, and a run allowed more reaches the second and not the first
 * ({@link BlockReason.RuleReadingStopped}). Flattened to "could not be read", a carrier put here to
 * stop two states sharing a word would have gone on to share one of its own.
 */
public record StringOfferShortfall(List<NotOffered> these) {

    static final StringOfferShortfall NONE = new StringOfferShortfall(List.of());

    public StringOfferShortfall {
        these = List.copyOf(these);
    }

    /** One of a single thing that gave nothing. */
    static StringOfferShortfall of(NotOffered one) {
        return new StringOfferShortfall(List.of(one));
    }

    /** Whether everything the rules about the strings had to offer reached the offer. */
    public boolean isEmpty() {
        return these.isEmpty();
    }

    /** This and {@code those} together, which is what a caller holding two readings has. */
    StringOfferShortfall and(StringOfferShortfall those) {
        if (those.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return those;
        }
        List<NotOffered> out = new ArrayList<>(these);
        out.addAll(those.these());
        return new StringOfferShortfall(out);
    }

    /**
     * One thing that gave the offer no value, and what stopped it.
     *
     * <p>{@code part} is the rule it was, or nothing where what gave nothing was the meeting of the
     * rules rather than any one of them. Nobody wrote that meeting, so an author sent to a rule for
     * it would be sent to one that reads perfectly.
     */
    public record NotOffered(PartId<RuleRef.Invariant> part, Why why) {

        public NotOffered {
            if (why == null) {
                throw new IllegalArgumentException("something that gave nothing says what stopped");
            }
        }

        /** One about a rule the author wrote. */
        static NotOffered ofARule(PartId<RuleRef.Invariant> part, Why why) {
            if (part == null) {
                throw new IllegalArgumentException("a rule that gave nothing is some rule");
            }
            return new NotOffered(part, why);
        }

        /** One about what the rules come to together, which is nobody's rule. */
        static NotOffered ofTheirMeeting(Why why) {
            return new NotOffered(null, why);
        }
    }

    /**
     * What stopped it, in this compiler's own terms.
     *
     * <p>Two, and they are not one fact. A rule this compiler could not read is one somebody may be
     * able to write another way, and what would let it through is a wider reading; a machine it
     * could not afford is a figure, and the rule may be perfectly ordinary.
     */
    public sealed interface Why {

        /**
         * This compiler does not read the rule, in the words it already has for that.
         *
         * <p>The reading's own answer carried over rather than reworded. Which of them it was
         * decides what an author does and whether a wider run reaches it, and a second vocabulary
         * for the same reasons is one that goes stale the first time the first one gains a word.
         */
        record NotRead(BlockReason.RuleReadingStopped why) implements Why {

            public NotRead {
                if (why == null) {
                    throw new IllegalArgumentException("a reading that stopped was stopped by"
                            + " something");
                }
            }
        }

        /**
         * Working a value out of it ran past what composing one for a row may spend.
         *
         * <p>Which limit refused it is kept: one machine larger than a machine may be is a pattern
         * that can be written smaller, and an allowance already spent is not — the same pattern
         * asked for first would have been built.
         */
        record TooCostly(Meter.Stopped stopped) implements Why {

            public TooCostly {
                if (stopped == null) {
                    throw new IllegalArgumentException("a construction that stopped was stopped by"
                            + " a limit");
                }
            }
        }
    }
}
