package souther.compiler.values;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Text;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;

/**
 * Where a language stops on the order the strings it holds are measured on.
 *
 * <p>The one place a set of strings and the order a position's values are counted on are brought
 * together. A {@link Language} is its strings and knows nothing about positions; {@link Text} is
 * where a string sits on the carrier's order and holds one string at a time. What a rule leaves a
 * position between is a question about both, and answering it anywhere else would be answering it
 * with whichever half was to hand.
 *
 * <p><b>Apart from {@link Sets}, which is the algebra of two sets.</b> This takes one and asks
 * something about the order — a different question, and the one that costs machines nobody else
 * needs. Written there, a caller meeting two sets would be holding an operation that projects.
 *
 * <p><b>What a run is established by.</b> A language is a run when the strings above its least that
 * it does not admit are everything from some string upwards. That is asked of the languages
 * themselves — the ones above a string, the ones this does not hold, and whether two of them are
 * one set — so nothing here decides what "between" means a second time. Convexity is not a word
 * used or wanted: a language that is not one run is one whichever way it fails to be, and a reading
 * that answered with the reason would be answering about the shape rather than about the edge.
 *
 * <p><b>All of it or none of it.</b> A reading stopped part way answers {@link
 * TextExtent.NotBuilt}, and never with the end it had reached. Half an answer would put a line on a
 * position when the machine for the other half happened to be affordable, which makes what a report
 * says about a model turn on what this compiler could build that day.
 */
public final class TextExtents {

    /**
     * Where the strings {@code set} holds begin and end, or why they name no run.
     *
     * <p>The way in, and a set that somebody has already built rather than a plan for one. Which
     * strings a rule leaves is what the position's own reading answers, under the position's own
     * allowance; a caller here is asking where those strings stop, which is a question a report
     * asks of an answer it was handed. Given the plan, this would be making the machine a second
     * time — and whether the model is read to admit anything at a position would have two answers,
     * one of them a diagnostic's.
     *
     * <p>What it does spend is its own. Turning a set into the machine this walk needs, and the
     * machines the walk makes of it, are the cost of the question being asked here — so they come
     * out of an allowance of this question's ({@link PatternPlan.Budget#OF_AN_ORDERED_EXTENT}) and
     * never out of what the position's answer is bounded by. A whole one per set: a caller with
     * several rules pays for each of them on its own, so that one expensive rule does not take the
     * line another rule drew.
     *
     * <p>What a run is and what a caller should do about it are two questions, and only the first is
     * answered here. A run holding one string and a run holding every string are runs; whether
     * either is a boundary somebody is owed a row at is read off the run by whoever draws lines.
     */
    public static TextExtent of(ValueSet set) {
        Meter meter = PatternPlan.Budget.OF_AN_ORDERED_EXTENT.meter();
        Language admitted = languageOf(set, meter);
        return admitted == null
                ? new TextExtent.NotBuilt(stopped(meter)) : of(admitted, meter);
    }

    /**
     * The strings {@code set} holds, or null past what {@code meter} allows.
     *
     * <p>Every set of values a rule about a string can leave is a set of strings, so every one of
     * them is a language: the ones a pattern named, the ones written out, everything but the ones
     * written out, all of them, and none. Which of those the answer came to is how it is held and
     * not what the rule said — so the question below is asked of a language whichever it was, and
     * the shape a set happens to be written in decides nothing.
     *
     * <p>Which is what keeps the run out of that decision. Read off the shape, a rule naming one
     * string would have no geometry while a pattern accepting that same string had one, and where
     * the values stop would turn on how the answer got written down.
     *
     * <p>Which strings a set of values holds is {@link Sets}', asked here rather than picked out
     * again: a set stands at one position and a position holds values of its type, so what a set
     * beside a rule about a string holds is strings, and a second reader deciding that for itself
     * is a second answer to it.
     */
    private static Language languageOf(ValueSet set, Meter meter) {
        return switch (set) {
            case ValueSet.Matching it -> it.language();
            case ValueSet.Finite it -> Language.ofWords(Sets.textsIn(it.values()), meter);
            case ValueSet.Cofinite it ->
                    Language.EVERY_STRING.without(Sets.textsIn(it.excluded()), meter);
        };
    }

    /**
     * Where {@code language} begins and ends, or why it names no run.
     *
     * <p>For a caller holding the language already, which is what a reader here does.
     */
    static TextExtent of(Language language) {
        return of(language, PatternPlan.Budget.OF_AN_ORDERED_EXTENT.meter());
    }

    /**
     * The same under a meter handed in, for a reader here that wants to see what running out comes
     * to.
     *
     * <p>Not the way in. A meter carries which limit refused the last thing built out of it, so one
     * that has been spent on something else answers this reading with a reason belonging to that —
     * which is why what a caller outside gets is the one above, with an allowance of its own.
     */
    static TextExtent of(Language language, Meter meter) {
        String first = language.least();
        if (first == null) {
            // Either it holds nothing, or its strings descend without stopping and what is below
            // all of them is not a string. Neither is a run with a string at its lower end, which
            // is what is being asked for, and neither is a limit of this compiler.
            return new TextExtent.NoNamedRun();
        }
        Language above = notBefore(first, meter);
        Language outside = above == null ? null : language.not(meter);
        // What the language leaves out from its least string upwards. Where that is nothing, the
        // language is everything from the least upwards; where it is everything from some string
        // upwards, the language is what lies between the two.
        Language left = outside == null ? null : above.and(outside, meter);
        if (left == null) {
            return new TextExtent.NotBuilt(stopped(meter));
        }
        if (left.isEmpty()) {
            return new TextExtent.One(Text.of(first), null);
        }
        String after = left.least();
        if (after == null) {
            return new TextExtent.NoNamedRun();
        }
        Language beyond = notBefore(after, meter);
        if (beyond == null) {
            return new TextExtent.NotBuilt(stopped(meter));
        }
        return beyond.equals(left)
                ? new TextExtent.One(Text.of(first), Text.of(after))
                : new TextExtent.NoNamedRun();
    }

    /**
     * Whether any string {@code language} admits lies inside {@code held}.
     *
     * <p>The range said as the strings it holds, which is what makes this an answer about the
     * strings rather than about the ends: a language is its strings, and two of them share a value
     * or they do not. Said as a projection of the language onto the ends instead, a rule whose
     * strings are not one stretch of the order would have no ends to be compared with.
     *
     * <p>{@code held} is where the strings are on the order already met with what the order
     * holds, so its ends are strings; {@link Emptiness#UNDECIDED} where {@code meter} refused a
     * machine this needed.
     */
    public static Emptiness inside(Language language, OrderedInterval held, Meter meter) {
        Language inside = within(language, held, meter);
        if (inside == null) {
            return Emptiness.UNDECIDED;
        }
        return inside.isEmpty() ? Emptiness.EMPTY : Emptiness.NONEMPTY;
    }

    /**
     * The strings {@code set} holds that lie inside {@code held}, or null past what {@code meter}
     * allows.
     *
     * <p>{@link #inside} answering with the strings rather than with whether there are any, for a
     * caller that has to write one down. The same two machines either way — a set is a language and
     * a range is the strings between its ends — and the question of which strings a position holds
     * inside a run of the order is one thing, so it is put together here. Answered a second time by
     * whoever wanted a string out of it, a value would be offered at a position that the reading
     * beside it had already said holds nothing there.
     *
     * <p>{@code held} is where the strings are on the order already met with what the order holds,
     * so its ends are strings.
     */
    public static Language stringsIn(ValueSet set, OrderedInterval held, Meter meter) {
        Language admitted = languageOf(set, meter);
        return admitted == null ? null : within(admitted, held, meter);
    }

    /** {@code language} less the strings outside {@code held}, or null past what {@code meter}
     *  allows. */
    private static Language within(Language language, OrderedInterval held, Meter meter) {
        Language inside = language;
        if (held.high() != null) {
            Text at = (Text) held.high().at();
            Language below = Language.before(
                    held.high().inclusive() ? at.justAbove().at() : at.at(), meter);
            inside = below == null ? null : inside.and(below, meter);
        }
        // A low end at the string of nothing, included, is below every string, so what lies
        // above it is the language as it stands; the machines that would say so are the empty
        // language, its complement and a meet with everything, and none of them is built.
        boolean lowestOfAll = held.low() != null && held.low().inclusive()
                && ((Text) held.low().at()).at().isEmpty();
        if (inside != null && held.low() != null && !lowestOfAll) {
            Text at = (Text) held.low().at();
            Language under = Language.before(
                    held.low().inclusive() ? at.at() : at.justAbove().at(), meter);
            Language above = under == null ? null : under.not(meter);
            inside = above == null ? null : inside.and(above, meter);
        }
        return inside;
    }

    /** Every string from {@code from} upwards, or null past what {@code meter} allows. */
    private static Language notBefore(String from, Meter meter) {
        Language before = Language.before(from, meter);
        return before == null ? null : before.not(meter);
    }

    /**
     * Which limit refused the machine that did not come back.
     *
     * <p>Read off the meter rather than guessed at: a language larger than one machine may be is one
     * an author wrote and can write differently, and a build that had already spent this answer's
     * allowance is not. A meter that came back with nothing and names no limit is this compiler
     * having lost the reason on the way, which is a mistake here and not a model anybody can write.
     */
    private static Meter.Stopped stopped(Meter meter) {
        Meter.Stopped why = meter.stoppedBy();
        if (why == null) {
            throw new IllegalStateException(
                    "a machine was not built and the allowance refused nothing");
        }
        return why;
    }

    private TextExtents() {}
}
