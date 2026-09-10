package souther.compiler.query;

import souther.compiler.partition.Border;
import souther.compiler.partition.Demand;
import souther.compiler.partition.BoundaryTarget;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.LineOrigin;
import souther.compiler.partition.PointRole;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedSentence;

import java.util.Map;

/**
 * Everything known about one reading of one border: the line as this position met it, and what
 * became of each of its coverage items at this reading.
 *
 * <p>One of these per border, made in one place. It is the occurrence: where a row can be written
 * and what the search there came to, which is what a block that shows a border whole prints and
 * what a generation composes from. What is owed is not here — a line is owed once however many
 * positions read it — and what a finding is about, what a verdict counts and what a report marks is
 * the point across its readings ({@link BorderObligationPointAssessment}), which is gathered from
 * these and never read off one of them.
 *
 * <p><b>Total over the points its border has, the way that border is.</b> A border answers at every
 * point its rule gives it and so does this, so a reader asking what one of them came to is never
 * answered by an entry that is not there. Which of the four each point is is the line's answer
 * ({@link Border#roleOf}) and is asked of it rather than kept here: a role is what a point is and
 * two points of one border can be the same one, so a measure keyed on the role would hold one entry
 * where there are two.
 */
public record BorderAssessment(Border border, Map<DomainPoint, ItemAssessment> items) {

    public BorderAssessment {
        if (items == null || !items.keySet().equals(border.answers().keySet())) {
            throw new IllegalArgumentException(
                    "a border assessed at some of its points and not others: " + items);
        }
        // And each of them assessed as what the border says it owes. The two records answer the same
        // question about the same point — what is owed there — and holding them apart without
        // holding them together leaves a point the rules refuse carrying a row that is at it. What a
        // report prints and what a build refuses over read one of the two, so they may not disagree.
        for (DomainPoint point : items.keySet()) {
            agrees(border, point, items.get(point));
        }
        items = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(items));
    }

    /**
     * That an item answers for the demand its border makes in that role.
     *
     * <p>Checked rather than derived, because the two are made by different things: the border says
     * what is owed and the measure says what became of it, and a measure that answered about a role
     * it had not been told about is the two coming apart. Deriving the item from the demand instead
     * would put the measure's answer where its question is.
     */
    private static void agrees(Border border, DomainPoint point, ItemAssessment item) {
        Demand demand = border.demand(point);
        switch (item) {
            case null -> throw new IllegalArgumentException(
                    "a border with a point it names and does not assess: " + point);
            case ItemAssessment.NotOwed not -> {
                if (!(demand instanceof Demand.NotOwed owed) || owed.reason() != not.reason()) {
                    throw new IllegalArgumentException("the " + point + " of " + border.label()
                            + " is assessed as not owed for " + not.reason()
                            + ", and its border says " + demand);
                }
            }
            case ItemAssessment.Owed owed -> {
                if (!(demand instanceof Demand.Owed asked)
                        || !asked.criterion().sameAs(owed.criterion())) {
                    throw new IllegalArgumentException("the " + point + " of " + border.label()
                            + " is assessed against " + owed.criterion()
                            + ", and its border asks " + demand);
                }
            }
        }
    }

    /** What became of one point. Never null. */
    public ItemAssessment at(DomainPoint point) {
        return items.get(point);
    }

    /**
     * The same of the one point playing {@code role}.
     *
     * <p>For a reader holding a classification, which is what a person asking about a border does.
     * The line refuses where two of its points play the role ({@link Border#theOne}), so this is a
     * question about a line whose shape the caller has established and never a way to key on the
     * role.
     */
    public ItemAssessment at(PointRole role) {
        return at(border.theOne(role));
    }

    /** The measured half of the one point playing {@code role}, or null where no row is owed
     *  there. */
    public ItemAssessment.Owed owedAt(PointRole role) {
        return owedAt(border.theOne(role));
    }

    /**
     * The measured half of one role, or null where no row is owed there.
     *
     * <p>For a reader that has already established which role it is asking about. Null here is a
     * reading and not a state anything can be built in: what is owed is settled where the border is
     * made, and nothing can produce an item that is both refused by the rules and sat on by a row.
     */
    public ItemAssessment.Owed owedAt(DomainPoint point) {
        return at(point) instanceof ItemAssessment.Owed owed ? owed : null;
    }

    /** The position this border is on, as a report names it. The line's own answer, so that a point
     *  taken out of this and the block printed round it name it alike. */
    public String axis() {
        return border.axis();
    }

    /** Which shape this line has, which is what says how to read what each item asks for. */
    public BoundaryTarget.Shape shape() {
        return border.cut().shape();
    }

    /** The rule that drew the line, as what a report writes about it, with {@code places} asked
     *  where the rule is one found by where it is written. */
    public PublishedSentence describe(PublishedRuleHandle.WhereARuleIs places) {
        return border.describe(places);
    }

    /**
     * The rule as this reading met it, for a reader that renders it rather than printing what
     * {@link #describe} would.
     *
     * <p>An {@link LineOrigin} and not a {@link souther.compiler.check.RuleRef}, which is why it is
     * not called the rule. Which rule of the model this came from is
     * {@link souther.compiler.partition.BorderObligationId#provenance()}, the same value however
     * many lines the rule drew; what a row is owed for is
     * {@link souther.compiler.partition.Border#obligation()}. Named the rule, the first two were one
     * word, and a caller wanting either reached for whichever this happened to be.
     */
    public LineOrigin origin() {
        return border.origin();
    }

    /** Where the line is, as a report names it. */
    public String label() {
        return border.label();
    }

    /** What a row at one point of it would be written as, or null where none is owed there. */
    public String label(DomainPoint point) {
        return border.label(point);
    }

    /** How one point relates a row's value to what it is against, or null where none is owed. */
    public String operator(DomainPoint point) {
        return border.operator(point);
    }

    /** What that point is against, or null where none is owed. */
    public String against(DomainPoint point) {
        return border.against(point);
    }

    /** The same three of the one point playing {@code role}. */
    public String label(PointRole role) {
        return label(border.theOne(role));
    }

    public String operator(PointRole role) {
        return operator(border.theOne(role));
    }

    public String against(PointRole role) {
        return against(border.theOne(role));
    }

    /** The left of the {@code left = right} a report names this line by. */
    public String left() {
        return border.cut().left();
    }

    /** The right of it, which for a line between two positions is the other position. */
    public String value() {
        return border.cut().right();
    }

    /**
     * One coverage item of one border, for a reader that walks the items rather than the borders.
     *
     * <p>One place turns a border into its items, so that a reader walking them is never short of
     * one: all four are here, and a role the rule owes nothing in says so rather than being left
     * out. What is <em>not</em> read off this list is what anybody is owed — a count, a finding and
     * a verdict work in obligations, which are what the readings of a line come to together
     * ({@link BorderObligationPointAssessment}), and a line is owed once however many positions
     * read it. This list has one entry per reading of each point, so anything counting it counts
     * the walk.
     */
    public record Point(BorderAssessment border, DomainPoint at, ItemAssessment item) {

        /** Which of the four this is, which the line it is a point of answers. */
        public PointRole role() {
            return border.border().roleOf(at);
        }

        /** What a row here would be written as, or null where none is owed. */
        public String label() {
            return border.label(at);
        }

        /** What this asks of a row, as a report writes it, or null where none is owed. */
        public String asked() {
            return border.operator(at) == null ? null
                    : border.operator(at) + " " + border.against(at);
        }

        /** What the point is against, or null where none is owed. */
        public String against() {
            return border.against(at);
        }

        /** The class a row here falls in, as one line of a class list is written. The line's own
         *  answer, so that a point taken out of this and one of a behavior's account say it
         *  alike. */
        public String said() {
            return border.border().said(at);
        }

        /** The measured half, or null where no row is owed here. */
        public ItemAssessment.Owed owed() {
            return item instanceof ItemAssessment.Owed owed ? owed : null;
        }
    }

    /** Every one of this border's items, in the order its points are in. */
    public java.util.List<Point> points() {
        return items.keySet().stream().map(point -> new Point(this, point, at(point))).toList();
    }

    /** The same over a list of borders. */
    public static java.util.List<Point> pointsOf(java.util.List<BorderAssessment> borders) {
        return borders.stream().flatMap(each -> each.points().stream()).toList();
    }

    /**
     * The same point of the same line, in another reading of one behavior's lines.
     *
     * <p>For a reader holding a later assessment than the one it was handed. The lines of a behavior
     * are measured once and searched afterwards, so a finding made from the measurement names a
     * point that the search has more to say about, and this is how the second is asked for the first.
     *
     * <p>Found by the border itself, which is a value, and by the role. The four fields a finding
     * used to carry instead — the axis, the value, the rule and the role — did not identify a point:
     * several rules can draw a line at one value, so a reader matching those answered with whichever
     * assessment came first. Two lines here that are equal are the same line, and this refuses to
     * choose between two of them rather than taking one.
     *
     * @throws IllegalStateException where no line here is that one, or where more than one is
     */
    public static ItemAssessment owedAt(java.util.List<BorderAssessment> lines, Border line,
                                        DomainPoint point) {
        ItemAssessment found = null;
        for (BorderAssessment each : lines) {
            if (!each.border().sameReadingAs(line)) {
                continue;
            }
            if (found != null) {
                throw new IllegalStateException(
                        "one behavior's lines hold " + line.label() + " twice, so the "
                                + point + " of it is two points");
            }
            found = each.at(point);
        }
        if (found == null) {
            throw new IllegalStateException("no line here is " + line.label()
                    + ", so its " + point + " is not one of these");
        }
        return found;
    }

    /** The points a row is owed at, which is what a coverage count is over. */
    public java.util.List<DomainPoint> owed() {
        return items.keySet().stream().filter(point -> at(point).owed()).toList();
    }

    /** The points the model's own rules discharged, which a report counts as excluded rather than as
     *  items nobody has got to. */
    public java.util.List<DomainPoint> excluded() {
        return items.keySet().stream()
                .filter(point -> border.demand(point).excluded()).toList();
    }
}
