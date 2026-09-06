package souther.compiler.partition;

import souther.compiler.check.ComparisonClaim;
import souther.compiler.coverage.ComparisonOccurrence;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Towards;
import souther.compiler.reading.Factor;
import souther.compiler.reading.Interaction;
import souther.compiler.reading.Outcome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The combinations a group of decisions has, as the classes a row filling one may sit in.
 *
 * <p>Two vocabularies meeting. A decision is read off the body and says which way a comparison came
 * out or which case a union was matched at; a row is written at classes of the input positions. The
 * two are about the same rules — the comparison a factor names is the comparison a cut was drawn
 * off — so this is a lookup rather than a second derivation of where the model divides.
 *
 * <p>A condition is a constraint and not a choice of class. {@code n > 10} leaves every class above
 * the line, and picking one of them here would answer for the condition alone and be wrong as soon
 * as a second one arrives: under {@code if n > 10 { if n > 0 { … } }} the way in is both, and a
 * reading that wrote a class down for each would keep the last. So a cell carries which classes each
 * position may hold, one condition narrows it, and a cell nothing is left at is not a combination.
 *
 * <p>A group is offered only where every condition it is made of narrows something. What a cell is
 * for is a row that takes the path to the meeting and settles each factor at the outcome the cell
 * names, and a condition with no classes to put a row at leaves both open: the row may go the other
 * way round the fork above, or take a different outcome of the factor, and either way it is offered
 * for a combination it does not sit in. A row already written reads the same way — the cell would be
 * taken for covered by a row that never reaches the operator. So the group goes, and what is left is
 * the pair space, which is where such a behavior already was.
 *
 * <p>A factor whose outcomes leave the same classes goes for the second half of the same reason: no
 * row can be steered from one to the other, so the cells over it are one row asked for several times.
 */
public final class InteractionCells {

    private InteractionCells() {}

    /**
     * Which classes of each position a row may sit in, as far as something has said.
     *
     * @param allowed one flag per class of each position, in the order the axes are ordered
     */
    public record Cell(boolean[][] allowed) {

        /** Nothing said yet: every class of every position is still open. */
        public static Cell anything(List<Axis> axes) {
            boolean[][] allowed = new boolean[axes.size()][];
            for (int i = 0; i < axes.size(); i++) {
                allowed[i] = new boolean[axes.get(i).classes().size()];
                Arrays.fill(allowed[i], true);
            }
            return new Cell(allowed);
        }

        /** Both, or null where they leave a position nothing. */
        public Cell and(Cell other) {
            boolean[][] both = new boolean[allowed.length][];
            for (int i = 0; i < allowed.length; i++) {
                both[i] = allowed[i].clone();
                boolean any = false;
                for (int c = 0; c < both[i].length; c++) {
                    both[i][c] &= other.allowed[i][c];
                    any |= both[i][c];
                }
                if (!any && both[i].length > 0) {
                    return null;
                }
            }
            return new Cell(both);
        }

        /** Whether this says anything about the position, which is what a row is named for. */
        public boolean narrows(int axis) {
            for (boolean each : allowed[axis]) {
                if (!each) {
                    return true;
                }
            }
            return false;
        }

        public boolean admits(int axis, int cls) {
            return cls >= 0 && cls < allowed[axis].length && allowed[axis][cls];
        }

        /**
         * Whether a row sitting at {@code where} — one class per position, in the order the axes
         * are ordered — is one this leaves room for.
         *
         * <p>A position this says nothing about admits whatever the row has there. What it does not
         * admit is a class it narrowed away, which is the whole of what a cell is.
         */
        public boolean holds(int[] where) {
            for (int axis = 0; axis < allowed.length; axis++) {
                if (narrows(axis) && !(axis < where.length && admits(axis, where[axis]))) {
                    return false;
                }
            }
            return true;
        }

        public boolean sameAs(Cell other) {
            return Arrays.deepEquals(allowed, other.allowed);
        }
    }

    /**
     * What one way of settling something leaves open, and what a run that settled it that way would
     * be seen to have done.
     *
     * <p>The pair travels together from here on. Kept apart, the classes and the claims would be two
     * lists indexed alike, and an outcome dropped from one of them for narrowing nothing would leave
     * the other saying what a different outcome takes.
     */
    public record Placed(Cell cell, List<souther.compiler.coverage.ControlClaim> claims) {

        public Placed {
            claims = List.copyOf(claims);
        }
    }

    /**
     * One group's combinations, as somewhere to read them from rather than as a list.
     *
     * <p>Nothing is built until it is asked for. Which combinations get offered is the row budget's
     * to decide, and a list built beforehand would have to guess how much of it the budget reaches:
     * a combination a written row already sits in costs no row, so a caller that stopped enumerating
     * at the budget would leave the ones past that point untried while the budget still held.
     *
     * @param reach    what the path to the meeting leaves open, which every combination is under
     * @param byFactor what each outcome of each factor leaves open, one list per factor
     */
    public record Group(Placed reach, List<List<Placed>> byFactor) {

        public Group {
            byFactor = List.copyOf(byFactor);
        }

        /**
         * How many ways there are to choose one outcome from each factor.
         *
         * <p>Not how many combinations the group has: two factors reading one position have choices
         * that leave it nothing, and no row is written at those. This is the space the choices are
         * counted off, and {@link #at} says which of them are cells.
         *
         * <p>The product itself, with nothing to saturate at. A group exists only where {@link #of}
         * found the product within what the compilation allows, so the number fits an {@code int}
         * by having been checked before this was built — and a limit carried in here to be reached
         * would be the budget living on in a value the budget already admitted.
         */
        public int size() {
            int size = 1;
            for (List<Placed> factor : byFactor) {
                size *= factor.size();
            }
            return size;
        }

        /**
         * The {@code index}th choice as a cell, or null where the factors it takes leave a position
         * nothing.
         *
         * <p>Leaving a position nothing is not a combination the body refuses. There is no path
         * through the body that takes both, so there is nothing there to ask for.
         */
        public CellSelection at(int index) {
            Cell cell = reach.cell();
            List<souther.compiler.coverage.ControlClaim> claims =
                    new ArrayList<>(reach.claims());
            int left = index;
            for (List<Placed> factor : byFactor) {
                Placed taken = factor.get(left % factor.size());
                cell = cell.and(taken.cell());
                if (cell == null) {
                    return null;
                }
                claims.addAll(taken.claims());
                left /= factor.size();
            }
            return new CellSelection(cell, claims);
        }

        /** How many cells the group has from {@code from} on, which is what a stopped search left. */
        public int left(int from) {
            int left = 0;
            for (int index = from; index < size(); index++) {
                if (at(index) != null) {
                    left++;
                }
            }
            return left;
        }
    }

    /**
     * A group the limit kept from being offered, and what a run that took it would have been seen
     * to do.
     *
     * <p>The claims and not the cells. Which combinations the group has is the product this declined
     * to walk; which control points any of them could claim is the union over the way in and every
     * outcome of every factor, which is one pass over what {@link #factorsOf} already built. So the
     * arms behind the limit can be named without doing the work the limit exists to refuse.
     *
     * <p>A union and so a superset: no single combination claims all of these, and one of them may
     * be claimed by another group that was offered. A caller reads it for what is left owed after
     * every offered group has been searched, which is where the difference stops mattering.
     */
    public record NotOffered(List<souther.compiler.coverage.ControlClaim> claims) {

        public NotOffered {
            claims = List.copyOf(claims);
        }
    }

    /**
     * The groups to offer, and the ones the limit kept back.
     *
     * <p>Two answers because they are two facts and the second used to be neither returned nor
     * recorded. A group dropped for its width claims arms, and what was not walked at those arms is
     * something a caller says rather than something a reader has to notice going missing.
     */
    public record Offered(List<Group> groups, List<NotOffered> notOffered) {

        public Offered {
            groups = List.copyOf(groups);
            notOffered = List.copyOf(notOffered);
        }
    }

    /**
     * Where a row that comes one way may sit, and what a run that came it would be seen to do, or
     * null where the way places at no class.
     *
     * <p>The same lookup a group's way in goes through, asked of one way on its own. A way in to an
     * arm and the reach of a meeting are the same kind of thing — decisions that hold on the way
     * somewhere — so what they leave open is read here once and not twice.
     *
     * <p>Null where any condition of the way narrows nothing, for the reason a group is dropped for
     * it: what a cell is for is steering a row along the way, and a condition with no classes to
     * put a row at leaves the row free to go the other way round that fork. Offered anyway, it would
     * be a row for an arm it may never take.
     */
    public static CellSelection at(souther.compiler.reading.WayIn way,
                                   souther.compiler.coverage.ControlClaim arrivesAt,
                                   List<Axis> axes) {
        if (way.decisions().isEmpty()) {
            // Nothing has to hold to get here, so there is nothing to steer a row by. The body
            // itself is reached this way.
            return null;
        }
        Placed placed = placedBy(way.decisions(), axes);
        if (placed == null) {
            return null;
        }
        // What a run that arrived would be seen doing, beside what holds on the way. Asked for
        // rather than read off the way, because the two are one thing only where the last decision
        // on the way is the place itself: a `match` names the case a run matched at, which is the
        // arm, while a fork on a comparison names the comparison and leaves the arm unsaid. Held to
        // the way alone, a row seen making the comparison and never seen at the arm certified the
        // arm (issue #1009).
        List<souther.compiler.coverage.ControlClaim> claims = new ArrayList<>(placed.claims());
        if (!claims.contains(arrivesAt)) {
            claims.add(arrivesAt);
        }
        return new CellSelection(placed.cell(), claims);
    }

    /** The groups worth offering, over the ordered {@code axes}, and the ones held back. */
    public static Offered of(List<Interaction> groups, List<Axis> axes,
                             AdequacyPolicy.OfTheGeneration budget) {
        List<Group> out = new ArrayList<>();
        List<NotOffered> held = new ArrayList<>();
        for (Interaction group : groups) {
            Placed reach = placedBy(group.reach(), axes);
            if (reach == null) {
                continue;
            }
            List<List<Placed>> placed = factorsOf(group, axes);
            if (placed == null) {
                continue;
            }
            // Past the limit, and said so rather than dropped. What this costs is the combinations
            // of one group going untried; what saying nothing cost is an arm among them reading as
            // one the body never reaches.
            if (productOf(placed, budget.cellsPerGroup()) > budget.cellsPerGroup()) {
                held.add(new NotOffered(claimsOf(reach, placed)));
                continue;
            }
            Group built = new Group(reach, placed);
            if (built.left(0) > 0) {
                out.add(built);
            }
        }
        return new Offered(out, held);
    }

    /** Every control point any combination of this group could claim, which is the union over the
     *  way in and every outcome of every factor. One pass, and never the product. */
    private static List<souther.compiler.coverage.ControlClaim> claimsOf(
            Placed reach, List<List<Placed>> byFactor) {
        java.util.LinkedHashSet<souther.compiler.coverage.ControlClaim> out =
                new java.util.LinkedHashSet<>(reach.claims());
        for (List<Placed> factor : byFactor) {
            for (Placed outcome : factor) {
                out.addAll(outcome.claims());
            }
        }
        return List.copyOf(out);
    }

    /**
     * How far the product runs, stopping once it is past anything that would be offered.
     *
     * <p>Stopped rather than run to the end, because a body of enough factors multiplies past what a
     * {@code long} holds and the answer this is asked for is only whether the limit was passed. One
     * over the limit is as good an answer as the true product for that, and is the largest number
     * this ever returns.
     */
    private static long productOf(List<List<Placed>> placed, int mostCells) {
        long size = 1;
        for (List<Placed> factor : placed) {
            size *= factor.size();
            if (size > mostCells) {
                return mostCells + 1L;
            }
        }
        return size;
    }

    /** What each outcome of each factor leaves open, or null where the group is not one to offer. */
    private static List<List<Placed>> factorsOf(Interaction group, List<Axis> axes) {
        List<List<Placed>> out = new ArrayList<>();
        for (Factor factor : group.factors()) {
            List<Placed> outcomes = new ArrayList<>();
            for (Outcome outcome : factor.outcomes()) {
                Placed at = placedBy(outcome.holds(), axes);
                if (at == null || alreadyThere(outcomes, at)) {
                    return null;
                }
                outcomes.add(at);
            }
            out.add(outcomes);
        }
        return out;
    }

    /**
     * What {@code made} leaves open and what a run that made it would be seen to have done, or null
     * where any of it narrows nothing or narrows it away.
     *
     * <p>One walk over the decisions for both halves. The classes are this reader's own reading of
     * what the decisions take of the inputs; the claims are carried through untouched, being the
     * reading that owns them. Which decisions are here is decided once, so the claims cannot end up
     * being of a different set of them than the classes are.
     */
    private static Placed placedBy(List<souther.compiler.reading.Decision> made,
                                   List<Axis> axes) {
        Cell cell = narrowedBy(
                made.stream().map(souther.compiler.reading.Decision::constrains).toList(), axes);
        return cell == null ? null
                : new Placed(cell, made.stream()
                        .map(souther.compiler.reading.Decision::claims).toList());
    }

    /** What {@code holds} leaves open, or null where any of it narrows nothing or narrows it away. */
    private static Cell narrowedBy(List<souther.compiler.reading.Condition> holds, List<Axis> axes) {
        Cell cell = Cell.anything(axes);
        for (souther.compiler.reading.Condition each : holds) {
            Cell said = admittedBy(each, axes);
            if (said == null) {
                return null;
            }
            cell = cell.and(said);
            if (cell == null) {
                return null;
            }
        }
        return cell;
    }

    private static boolean alreadyThere(List<Placed> outcomes, Placed at) {
        for (Placed each : outcomes) {
            if (each.cell().sameAs(at.cell())) {
                return true;
            }
        }
        return false;
    }

    /** Which classes {@code condition} leaves its position, or null where it names none. */
    private static Cell admittedBy(souther.compiler.reading.Condition condition, List<Axis> axes) {
        switch (condition) {
            case souther.compiler.reading.Condition.Case one -> {
                int axis = axisAt(axes, one.at());
                if (axis < 0) {
                    return null;
                }
                List<PartitionClass> classes = axes.get(axis).classes();
                for (int c = 0; c < classes.size(); c++) {
                    if (classes.get(c).label().equals(one.name())) {
                        return only(axes, axis, c, c);
                    }
                }
                return null;
            }
            case souther.compiler.reading.Condition.Side one -> {
                int axis = axisOf(axes, one.at());
                if (axis < 0) {
                    return null;
                }
                Cut line = cutAt(axes.get(axis), one.comparison());
                if (line == null) {
                    return null;
                }
                LineOrigin.ComparisonOrigin guard = guardOf(line, one.comparison());
                int home = holding(axes.get(axis), line);
                if (home < 0) {
                    return null;
                }
                // Which side the comparison is true on, from the two facts the order carries: which
                // side of it the cut value itself sits on, and whether the comparison holds there.
                // The whole side and not its nearest class: the comparison admits every value out
                // that way, and a reading that answered with one of them would have said more than
                // the rule does — which is what makes two of them impossible to take together.
                //
                // Asked of a rule that ordered the values, and of no other. A rule that names one
                // has no side its values are true on — what it distinguishes is that value from
                // every other — so there is no cell either way of it to take.
                if (!(guard.facts().claim() instanceof ComparisonClaim.Cut order)) {
                    return null;
                }
                boolean wantedIsHomeSide = order.holdsAtTheValue() == one.held();
                // The side the cell wants: the one the rule is satisfied on where it wants the rule
                // met, and the other where it wants it broken.
                boolean wantedIsUp = (one.held() ? order.satisfyingSide()
                        : order.satisfyingSide().opposite()) == Towards.ABOVE;
                int last = axes.get(axis).classes().size() - 1;
                int edge = wantedIsHomeSide ? home : (wantedIsUp ? home + 1 : home - 1);
                if (edge < 0 || edge > last) {
                    return null;
                }
                return wantedIsUp ? only(axes, axis, edge, last) : only(axes, axis, 0, edge);
            }
            // A fork this reading could not name a position for narrows nothing.
            case souther.compiler.reading.Condition.Arm ignored -> {
                return null;
            }
        }
    }

    /** Everything open but {@code axis}, which is left the classes from {@code from} to {@code to}. */
    private static Cell only(List<Axis> axes, int axis, int from, int to) {
        Cell cell = Cell.anything(axes);
        Arrays.fill(cell.allowed()[axis], false);
        for (int c = from; c <= to; c++) {
            cell.allowed()[axis][c] = true;
        }
        return cell;
    }

    /**
     * The axis measuring {@code term}, or -1 where none does.
     *
     * <p>The number and never the path it is read from. A location may be measured at more than one
     * number — which hour of a time it is and which minute — and those are one path: asked by path,
     * a comparison about the second of them is answered by the first's axis, which carries no cut of
     * that comparison and so narrows nothing, with nothing saying the condition went unread.
     *
     * <p>Exact and not the nearest. The reading that named this condition and the reading that made
     * the axis ask {@link souther.compiler.inputs.InputNumber} for the number, so a name worn over
     * a type is already looked through on both sides and there is nothing left for a match to be
     * lenient about.
     */
    private static int axisOf(List<Axis> axes, souther.compiler.inputs.NumericTerm term) {
        for (int i = 0; i < axes.size(); i++) {
            if (axes.get(i).term().equals(term)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Which position the condition is about, allowing for the names a value is written under.
     *
     * <p>{@code total.value} and {@code total} are one position where {@code Total} is a name worn
     * over an {@code Int}: the body reads through the name and the partition divides the position
     * the name is at. The longest match wins, so a record whose own field is divided keeps its own
     * axis rather than being answered by the record's.
     *
     * <p>For a case of a union, which is about the location and not about a number of it. What a
     * comparison is about is {@link #axisOf}'s, exactly.
     */
    private static int axisAt(List<Axis> axes, TermPath path) {
        int found = -1;
        int deepest = -1;
        for (int i = 0; i < axes.size(); i++) {
            TermPath at = axes.get(i).path();
            if (!at.head().equals(path.head()) || at.steps().size() > path.steps().size()) {
                continue;
            }
            if (path.steps().subList(0, at.steps().size()).equals(at.steps())
                    && at.steps().size() > deepest) {
                deepest = at.steps().size();
                found = i;
            }
        }
        return found;
    }

    /** The cut this reading of the comparison drew, or null where it drew none. */
    private static Cut cutAt(Axis axis, ComparisonOccurrence comparison) {
        for (Cut each : axis.cuts()) {
            if (guardOf(each, comparison) != null) {
                return each;
            }
        }
        return null;
    }

    /** Which rule of the cut this reading of the comparison is. */
    private static LineOrigin.ComparisonOrigin guardOf(Cut cut, ComparisonOccurrence comparison) {
        for (LineOrigin origin : cut.origins()) {
            if (origin instanceof LineOrigin.ComparisonOrigin guard
                    && guard.read().comparison().equals(comparison)) {
                return guard;
            }
        }
        return null;
    }

    /**
     * Which class of the axis holds the number the line is drawn at, or -1 where none does.
     *
     * <p>Asked with the place the line is at, on the order of the number the axis measures. The
     * classes of that axis are classes of that number, so the two meet as they stand — asked with a
     * value of the position instead, a class about something taken of it is handed the taken number
     * where it expects what stands there, and every class says no.
     */
    private static int holding(Axis axis, Cut line) {
        List<PartitionClass> classes = axis.classes();
        for (int c = 0; c < classes.size(); c++) {
            if (classes.get(c).holdsTheNumberAt(line.at())) {
                return c;
            }
        }
        return -1;
    }
}
