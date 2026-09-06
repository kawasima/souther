package souther.compiler.partition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every line one behavior's reading of the rules found, and every border it made of one.
 *
 * <p>Two records of one pass, kept apart so that neither is counted from the other. What a measure
 * is short of is what the model raised and nothing answered; that a measure may be called complete
 * at all is a separate claim — that the reading got to the end of what it was reading — and there is
 * nothing in the gaps to say it. A line dropped before it could be set aside leaves no gap behind,
 * so an empty account of what went missing is what a whole reading and a lossy one both produce.
 * That is issue #1079, and holding these two against each other is what refuses it.
 *
 * <p><b>Every producer, and not the two there are today.</b> A line that divides a position leaves
 * its border on the position ({@link Partitions#bordersOf}) and a line between two positions leaves
 * its border beside them ({@link Border#allOf}). They are the same reading and the same kind of
 * loss, and an accounting over one of them says nothing about the other — which is how a check
 * written for the first would have watched a {@code continue} go into the second.
 *
 * <p>Which is why the borders the reading hands back are held against this as well
 * ({@link #returning}). Kept by the producers alone, the account is worth what a producer
 * remembered to write in it, and a third one added later is off the books from the day it is
 * written — the same silence, one level up, and this whole arrangement is against exactly that.
 *
 * <p><b>Identity and not a count.</b> Two lines are one line where they are at one place and drawn
 * by one rule, which is what {@link Line} is. Counted instead, a reading that lost one line and made
 * another twice comes back whole; asked of the lines themselves, neither is possible.
 *
 * <p>Recorded where each thing happens: a line as the reading meets it, and a border where it lands
 * in what the reading returns. Recorded together, the two would agree by construction and the
 * account would prove nothing — what it is for is the day something is written between them.
 */
public final class LinesRead {

    /**
     * One line, told from another by where it is and whose rule drew it.
     *
     * <p>Not {@link BorderObligationId}, which is what several readings of one authored line share
     * and folds the positions it was met at into one (issue #1062). This tells one reading from
     * another, because that is what a reading has to account for: a clause carried by two positions
     * is two lines here and one obligation there, and an accounting keyed on the obligation would
     * call the second of them a duplicate and let it go missing.
     */
    public record Line(BoundaryTarget at, LineOrigin by) {}

    private final Set<Line> found = new LinkedHashSet<>();
    private final List<Border> drawn = new ArrayList<>();
    private final List<Border> returning = new ArrayList<>();

    /**
     * One line the rules draw, as the reading meets it and before anything is made of it.
     *
     * <p>Twice for one line is once. A rule read in two places draws two lines and they are told
     * apart by where they are; one line met twice by one reading is the reading passing the same
     * place again, and a border is made of it once.
     */
    public void found(BoundaryTarget at, LineOrigin by) {
        found.add(new Line(at, by));
    }

    /** The border made of one, recorded where it lands in what the reading returns. */
    public Border drew(Border border) {
        drawn.add(border);
        return border;
    }

    /**
     * The borders this reading is about to hand back, whoever made them.
     *
     * <p>Told at the one place they are assembled rather than by each producer. What a producer
     * volunteers is what a producer remembered to volunteer, and the point of an account is to hold
     * a reading to what it returns.
     */
    public void returning(java.util.Collection<Border> borders) {
        returning.addAll(borders);
    }

    /**
     * That every line this reading found became a border, and no border came from nowhere.
     *
     * <p>The claim {@code Closed} rests on. Said as three sentences and not one count: a line found
     * and not drawn is a reading that lost part of the model, a line drawn and not found is a border
     * this reading cannot account for, and one line drawn twice asks for the same row twice under
     * two names.
     */
    void everyLineFoundWasDrawn() {
        Map<Line, Integer> made = new LinkedHashMap<>();
        for (Border border : drawn) {
            made.merge(new Line(border.cut(), border.origin()), 1, Integer::sum);
        }
        List<Line> twice = made.entrySet().stream().filter(each -> each.getValue() > 1)
                .map(Map.Entry::getKey).toList();
        if (!twice.isEmpty()) {
            throw new IllegalStateException(
                    "a line drawn more than once: " + said(twice));
        }
        List<Line> lost = found.stream().filter(each -> !made.containsKey(each)).toList();
        if (!lost.isEmpty()) {
            throw new IllegalStateException(
                    "a line this reading found and did not draw: " + said(lost)
                            + " — what a measure is short of cannot be counted from what a reading"
                            + " lost");
        }
        List<Line> unaccounted = made.keySet().stream().filter(each -> !found.contains(each))
                .toList();
        if (!unaccounted.isEmpty()) {
            throw new IllegalStateException(
                    "a border this reading cannot account for: " + said(unaccounted));
        }
        // And what the reading returns is what it wrote down. A border reaching a caller off the
        // books is a producer this account does not know about, and one written down and not
        // returned is a loss between the two — both leave the closure resting on a reading it did
        // not see the whole of.
        List<Line> offTheBooks = returning.stream()
                .map(each -> new Line(each.cut(), each.origin()))
                .filter(each -> !made.containsKey(each)).distinct().toList();
        if (!offTheBooks.isEmpty()) {
            throw new IllegalStateException(
                    "a border returned by a reading that did not write it down: "
                            + said(offTheBooks));
        }
        List<Line> dropped = made.keySet().stream()
                .filter(each -> returning.stream()
                        .noneMatch(had -> new Line(had.cut(), had.origin()).equals(each)))
                .toList();
        if (!dropped.isEmpty()) {
            throw new IllegalStateException(
                    "a border this reading drew and did not return: " + said(dropped));
        }
    }

    private static String said(List<Line> lines) {
        return lines.stream()
                .map(each -> each.at().label() + " (" + each.by().saidWithoutAPlace() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
