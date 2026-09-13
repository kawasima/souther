package souther.compiler.partition;

import souther.compiler.inputs.BlockReason;
import souther.compiler.regex.Meter;

import java.util.ArrayList;
import java.util.List;

/**
 * What the rules about a position's strings left out of the values offered there.
 *
 * <p>Not what the position admits, and not a search that stopped. A value to paste into a row is
 * composed from the rules this compiler read, and where it could not read one, or could not afford
 * the machine for one, the value came from the rules beside it. Every one of those values is still
 * put to the decoder and every row that comes out is a row the model accepts — what is lost is the
 * right to say that what was offered was everything there was to offer.
 *
 * <p><b>Two facts and not one, because an author does different work about them.</b> A rule this
 * compiler cannot read is one they can write differently, or one nobody has taught this compiler
 * yet; an allowance this compiler ran out of is a figure, and the rule may be perfectly ordinary.
 * Held as one, whichever word was chosen sends half of the readers to the wrong place.
 *
 * @param unreadable     one reason for each rule about the strings this compiler did not read
 * @param witnessStopped one for each time working a value out ran past what composing one is
 *                       allowed to spend
 */
record StringOfferShortfall(List<BlockReason.RuleReadingStopped> unreadable,
                            List<WitnessOfferStopped> witnessStopped) {

    static final StringOfferShortfall NONE = new StringOfferShortfall(List.of(), List.of());

    StringOfferShortfall {
        unreadable = List.copyOf(unreadable);
        witnessStopped = List.copyOf(witnessStopped);
    }

    /** Whether the values offered were everything the rules about the strings had to offer. */
    boolean isEmpty() {
        return unreadable.isEmpty() && witnessStopped.isEmpty();
    }

    /** This and {@code those} together, which is what a caller holding two positions' has. */
    StringOfferShortfall and(StringOfferShortfall those) {
        if (those.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return those;
        }
        List<BlockReason.RuleReadingStopped> rules = new ArrayList<>(unreadable);
        rules.addAll(those.unreadable());
        List<WitnessOfferStopped> stopped = new ArrayList<>(witnessStopped);
        stopped.addAll(those.witnessStopped());
        return new StringOfferShortfall(rules, stopped);
    }

    /**
     * Composing a value to write into a row ran past what composing one may spend.
     *
     * <p>Its own thing and not one of the reasons a rule went unread. Every rule here was read: what
     * stopped is the machine this compiler builds to work a string out of them, under the allowance
     * that pays for writing a value rather than the one that pays for reading a position. The two
     * are granted separately and can run out separately, so a word taken from the reading would say
     * a position was read less exactly than it was.
     *
     * <p>Which limit refused it is kept, because an author does different work about the two. One
     * machine larger than a machine may be is a pattern that can be written smaller; an allowance
     * spent is a figure, and the same pattern asked for first would have been built.
     */
    record WitnessOfferStopped(Meter.Stopped stopped) {

        WitnessOfferStopped {
            if (stopped == null) {
                throw new IllegalArgumentException(
                        "a construction that stopped was stopped by a limit");
            }
        }
    }
}
