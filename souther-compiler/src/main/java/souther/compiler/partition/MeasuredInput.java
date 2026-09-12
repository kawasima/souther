package souther.compiler.partition;

import souther.compiler.check.DeclarationReadings;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.PositionId;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * One behavior's input as it was read, and where the model divides it.
 *
 * <p><b>One reading, and the geometry measured against it.</b> The walk a row's values are written
 * by and the orders its numbers are measured on both follow from one reading of one behavior's
 * declarations; where the values are divided does not — a body draws lines the declarations say
 * nothing about — so the classes come from a measurement made against that reading rather than from
 * the reading itself. Handed over as separate values, a caller could give a search the walk of one
 * reading and the orders of another, or classes measured against a third: two behaviors taking a
 * parameter spelled the same way is all it takes, and then a row is composed on one measurement's
 * orders and read back by another's walk. There is one value here, so there is nothing to put
 * together wrongly — and one place makes it, from one {@code (module, behavior)}.
 *
 * <p><b>What a reader gets is a projection of this and never its parts.</b> Placing a row's values
 * takes the walk and some of the classes, and looking for a row at a line takes the walk and that
 * line: both are the walk beside something measured, which is exactly the pairing above. So a
 * reader asks here for {@link MeasuredAxes} or a {@link BorderReading}, and neither can be made
 * anywhere else — what a projection holds is this measurement's own geometry rather than geometry
 * that agreed with it. Which is not the same thing: whether a value is this measurement's is
 * whether this measurement has it, and comparing its attributes instead is a derivation of that
 * with one more attribute to it every time somebody finds a way past.
 *
 * <p><b>The reading itself does not come through.</b> What is offered is the two questions a search
 * asks — where a value is written, and what a number there is measured on — and not a way of asking
 * the reading anything else. A construction plan's coordinate is spelled with the same
 * {@link TermPath} as a position of the input, so a search that could reach the reading could look
 * one up in it and be told that a place it is building at is not a position of anything.
 */
public final class MeasuredInput {

    private final String behavior;
    private final BehaviorInputs written;
    private final Quantities quantities;
    private final Partitions.Partitioning divided;
    /**
     * Where the declarations this input reaches get what the walk that read it already made of
     * them.
     *
     * <p>Kept because choosing a value reads those declarations again. A probe fixes a coordinate
     * and reads what is left beside it, which is a reading of its own every time; what the
     * declarations' string rules come to is not, and is what the walk has already worked out.
     *
     * <p>No part of what makes two of these one. It is a capability: two built from one store
     * answer alike and compare unlike, so a comparison that took it in would say a reading taken
     * again was another reading.
     */
    private final DeclarationReadings machines;

    private MeasuredInput(String behavior, BehaviorInputs written, Quantities quantities,
                          Partitions.Partitioning divided, DeclarationReadings machines) {
        this.machines = machines;
        this.behavior = behavior;
        this.written = written;
        this.quantities = quantities;
        this.divided = divided;
    }

    /**
     * The input {@code read} was made of, divided by {@code divided}.
     *
     * <p>The one way to make one. What a row is written by and what its numbers are measured on are
     * both taken from the reading rather than from the caller, and the measurement is held to it: a
     * measure of a number the reading cannot answer for is a measure of another behavior's input,
     * whatever it is named after.
     *
     * <p><b>The whole measurement and not a list of axes.</b> What a reader wants of it is several
     * projections — every measure, the ones that divide their number into classes, the ones a
     * search can derive a value at — and a caller handed one list works the others out beside this
     * one. The lines are here for the same reason: a border is measured against this reading like
     * an axis is, and taking one from a partitioning beside this would be the pairing this type
     * exists to remove.
     *
     * @param behavior what the rows are written for, which every axis agrees with
     */
    public static MeasuredInput of(String behavior, InputReading read,
                                   Partitions.Partitioning divided) {
        if (behavior == null || behavior.isEmpty()) {
            throw new IllegalArgumentException("a row is written for a behavior with a name");
        }
        for (Axis axis : divided.axes()) {
            // An axis of another behavior, which is a subject assembled from two measurements. The
            // name would then be one of two answers rather than the subject's, and whichever
            // sentence read it would be right about one of them.
            if (!axis.id().behavior().equals(behavior)) {
                throw new IllegalArgumentException(
                        "an axis of " + axis.id().behavior() + " in the subject of " + behavior
                                + ": " + axis.id());
            }
            // And a measure of a number this reading takes nothing of, which is what an axis
            // derived at another reading of a behavior spelled the same way comes to. The reading
            // refuses such a term, so asking it is the check.
            read.quantities().ordersOf(axis.term());
        }
        return new MeasuredInput(behavior, BehaviorInputs.of(read), read.quantities(), divided,
                read.domain().machines());
    }

    /**
     * Two of these are one where they were read and divided alike.
     *
     * <p>Written out because this is held in an answer, and what an answer holds is compared by
     * whatever decides that a compile changed nothing. The reading itself compares as the capability
     * it is — one per behavior per compile — so what this says is what the record it replaced said.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MeasuredInput that
                && behavior.equals(that.behavior)
                && written.equals(that.written)
                && quantities.equals(that.quantities)
                && divided.equals(that.divided);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(behavior, written, quantities, divided);
    }

    @Override
    public String toString() {
        return "MeasuredInput[" + behavior + " over " + divided.axes().size() + " axes]";
    }

    /** What the rows are written for. */
    public String behavior() {
        return behavior;
    }

    /**
     * The walk into what a row writes: what the inputs are called, what they are, and what those
     * names denote.
     *
     * <p>For composing a value and for neither of the two questions below. Reading a value back is
     * done through a projection of this measurement ({@link MeasuredAxes}, {@link BorderReading}),
     * because a walk is only ever right beside geometry measured against the same reading. What a
     * number at a position is measured on is the reading's ({@link #quantities()}), and where the
     * model divides one is the measure's.
     */
    public BehaviorInputs inputs() {
        return written;
    }

    /** Where a reader of the declarations this input reaches borrows what the walk that read it
     *  already made of them. */
    public DeclarationReadings machines() {
        return machines;
    }

    /** What the rules reaching this input leave its numbers. */
    public Quantities quantities() {
        return quantities;
    }

    /**
     * Where the model divides its positions, whole.
     *
     * <p>For a reader whose question is about the measurement rather than about a row: which
     * positions came back with nothing to divide them by, which rules drew no line, how far the
     * enumeration got. Placing a row's values is not one of those questions and goes through the
     * projections below, which carry the walk the values are found by.
     */
    public Partitions.Partitioning partitioning() {
        return divided;
    }

    /**
     * What was measured at each of its positions, in the order the reading found them.
     *
     * <p>The shape the measurement has. A number is measured at a location and a location may be
     * measured at several, so which location a measure is of is where the measure sits — a reader
     * that needs both walks this rather than putting them back together from how a path is
     * spelled. The two projections below are this flattened.
     */
    public List<MeasuredPosition> measurements() {
        List<MeasuredPosition> out = new ArrayList<>(divided.measurements().size());
        for (PositionMeasurements at : divided.measurements()) {
            out.add(new MeasuredPosition(this, at));
        }
        return List.copyOf(out);
    }

    /**
     * What a search composing a value at one of its positions is given beside the region: the sets
     * the declarations leave those positions, and what looking in one may cost.
     *
     * <p>Off this measurement and not worked out again by whoever searches. The sets are the
     * reading's answer about the model, and the region a search runs inside is arithmetic — a caller
     * that had only the second would compose a value the first refuses, with nothing on the way back
     * able to tell it so.
     */
    public WitnessSearch witnessSearch() {
        return Partitions.witnessSearch(divided.measurements());
    }

    /** Every measure of its positions, in the order the rules name the numbers. */
    public MeasuredAxes axes() {
        return new MeasuredAxes(this, divided.axes());
    }

    /**
     * The measures that divide their number into classes, which is what a partition is counted
     * over.
     *
     * <p>A measure may be a boundary and no partition, and such a number has no class for a value
     * to fall in. Asked here rather than filtered by whoever wants them, so that what a partition
     * is over is one answer.
     */
    public MeasuredAxes partitionAxes() {
        return new MeasuredAxes(this, divided.partitionAxes());
    }

    /**
     * This measurement's reading of the line it read where {@code asked} is.
     *
     * <p>Where a border becomes something a row can be looked for at. Which lines there are was
     * settled when this input was measured, so what makes one this measurement's is that this
     * measurement drew it — not that its behavior, its numbers, the position it is on and the
     * orders it is measured on each agree with this one. Those are the attributes of a value that
     * already has an identity, and comparing them one at a time is a derivation with no end: every
     * one that goes unchecked is a line read against a measurement that never drew it.
     *
     * <p><b>The line this measurement holds comes back, and not the one handed in.</b> What a
     * reader of a line goes on to ask — what it demands of a row, where the run below it stops — is
     * answered off the value it holds, so a caller's copy would be read instead of the reading's
     * own. Told apart by {@link Border#sameReadingAs}: the same border met in the same place owing
     * the same things is this line, whatever was written beside it where the caller got it.
     *
     * <p><b>A quantity a transformation produced comes back through here.</b> Moving a quantity to
     * another number ({@link BorderQuantity#movedTo}) is done on the quantity alone and carries
     * nothing of where it was measured, so what comes out is geometry again rather than a reading
     * of it — and a line this measurement never drew is not one it can read a row at.
     */
    public BorderReading at(Border asked) {
        Border held = divided.held(asked);
        if (held == null) {
            throw new IllegalArgumentException("the measurement of " + behavior
                    + " read no line where this one is: " + asked.cut());
        }
        return new BorderReading(this, held);
    }

    /**
     * The same three facts a row is read by, which is the point of holding one value.
     *
     * <p>Written out here as well, a row would be generated from one reading of what the behavior
     * takes and read back by another — and how a position is written is exactly what the two came
     * to disagree about.
     */
    public List<String> parameters() {
        return written.parameters();
    }

    public List<Type> types() {
        return written.types();
    }

    public Symbols symbols() {
        return written.symbols();
    }

    /** The scope and the clause representation this was read with, for whatever reads a
     *  declaration's rules again under it. */
    public RuleReadingSource rules() {
        return written.rules();
    }

    /**
     * The world a reader of this input's declarations reads in, whole.
     *
     * <p>Where the rules come from, what a reading may spend, and what the walk that read this
     * input already made of the declarations it met. Handed over together because a reader given
     * two of the three and left to find the last reads the same declarations again, once per
     * position that arrives at one.
     */
    public RuleReadingContext ruleReading() {
        return RuleReadingContext.of(rules(), written.policy(), machines);
    }

    /** How many the rules leave the container at {@code at}, or every number where they leave it
     *  unsaid. */
    public int mostHeldAt(PositionId at) {
        return quantities.mostHeldAt(at);
    }

    /**
     * Whether the model divides this position into a class spelled this way.
     *
     * <p>The one place the question is answered, and asked of the whole measurement rather than of
     * a projection of it. What a projection holds is what some reader is looking at; whether the
     * model divides a position is what the model says, and answered from a projection the two
     * would come back as one {@code false}.
     */
    public boolean divides(ClassOfAPosition owed) {
        for (Axis axis : divided.axes()) {
            if (!axis.id().equals(owed.at())) {
                continue;
            }
            for (PartitionClass cls : axis.classes()) {
                if (cls.id().equals(owed.classId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * One location of a measured input, and what the model divides it by.
     *
     * <p>What a reader answering about a location holds: the account of what its reading came to,
     * and the measures made there, each of them already beside the input they were measured at. A
     * reader walking these never has a measure in one hand and a location in the other.
     */
    public static final class MeasuredPosition {

        private final MeasuredInput subject;
        private final PositionMeasurements measured;

        private MeasuredPosition(MeasuredInput subject, PositionMeasurements measured) {
            this.subject = subject;
            this.measured = measured;
        }

        /** What this location's reading came to. */
        public PositionAccount position() {
            return measured.position();
        }

        /** Every measure made here. */
        public MeasuredAxes axes() {
            return new MeasuredAxes(subject, measured.axes());
        }

        /** The measures here that divide their number into classes. */
        public MeasuredAxes partitionAxes() {
            return new MeasuredAxes(subject, measured.partitionAxes());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MeasuredPosition that
                    && subject.equals(that.subject)
                    && measured.equals(that.measured);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(subject, measured);
        }

        @Override
        public String toString() {
            return "MeasuredPosition[" + measured.position().path() + "]";
        }
    }

    /**
     * Some of one measurement's axes, in an order, with the input they were measured at.
     *
     * <p>What a reader placing values actually holds. Every question about where a row sits takes
     * the walk and the classes together, and this is the two as one value — so a reader cannot be
     * handed classes from one measurement beside a walk from another.
     *
     * <p><b>Made only from a measurement, and narrowed only by what it already holds.</b> The ways
     * to make one are {@link MeasuredInput#axes()}, {@link MeasuredInput#partitionAxes()} and the
     * two below, all of which select from this measurement's own axes. There is no way to name an
     * axis from outside and have it admitted, so a foreign axis has no road in and nothing has to
     * check for one.
     *
     * <p><b>An order and not a set.</b> A search fixes its positions in an order and names a place
     * in the assignment by the index, so two of these over the same axes in different orders are
     * two different readings of the same row.
     */
    public static final class MeasuredAxes {

        private final MeasuredInput subject;
        private final List<Axis> axes;

        private MeasuredAxes(MeasuredInput subject, List<Axis> axes) {
            this.subject = subject;
            this.axes = List.copyOf(axes);
        }

        /** The measurement these were measured at, and the walk a row of it is written by. */
        public MeasuredInput subject() {
            return subject;
        }

        /** The axes themselves, for a reader whose question is about the geometry alone. */
        public List<Axis> axes() {
            return axes;
        }

        public int size() {
            return axes.size();
        }

        public Axis get(int at) {
            return axes.get(at);
        }

        public boolean isEmpty() {
            return axes.isEmpty();
        }

        /**
         * The ones {@code admits} keeps, which is a narrowing and never a widening.
         *
         * <p>Takes what to keep rather than which to take. A caller naming axes would be handing
         * identities in, and an identity handed in is one that came from somewhere — which is the
         * road a foreign axis would take. A predicate names none.
         */
        MeasuredAxes where(Predicate<Axis> admits) {
            List<Axis> kept = new ArrayList<>(axes.size());
            for (Axis axis : axes) {
                if (admits.test(axis)) {
                    kept.add(axis);
                }
            }
            return new MeasuredAxes(subject, kept);
        }

        /** The same axes in the order {@code by} puts them in. */
        MeasuredAxes sortedBy(Comparator<Axis> by) {
            List<Axis> ordered = new ArrayList<>(axes);
            ordered.sort(by);
            return new MeasuredAxes(subject, ordered);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MeasuredAxes that
                    && subject.equals(that.subject)
                    && axes.equals(that.axes);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(subject, axes);
        }

        @Override
        public String toString() {
            return "MeasuredAxes[" + subject.behavior() + " over " + axes.size() + " axes]";
        }
    }

    /**
     * One measurement's reading of one of its lines.
     *
     * <p>The border beside the walk a row of this input is read by, for the reason
     * {@link MeasuredAxes} is: whether a row stands at a line is asked of the row's values, and the
     * values are found by walking. Two behaviors taking a parameter spelled the same way have a
     * line apiece at the same spelling, and read through the wrong walk one of them answers about
     * the other's rows.
     *
     * <p>Made only by {@link MeasuredInput#at}, which answers the line asked for with the one this
     * measurement read where it is. So the border here is the reading's own, and not one that was
     * handed in and let through.
     */
    public static final class BorderReading {

        private final MeasuredInput subject;
        private final Border border;

        private BorderReading(MeasuredInput subject, Border border) {
            this.subject = subject;
            this.border = border;
        }

        /** The measurement this line was read at. */
        public MeasuredInput subject() {
            return subject;
        }

        /** The line itself. */
        public Border border() {
            return border;
        }

        /** What it is a border of. */
        public BorderQuantity quantity() {
            return border.cut().of();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BorderReading that
                    && subject.equals(that.subject)
                    && border.equals(that.border);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(subject, border);
        }

        @Override
        public String toString() {
            return "BorderReading[" + subject.behavior() + " at " + border.cut() + "]";
        }
    }
}
