package souther.compiler.partition;

import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Towards;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedSentence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * One line a rule drew, and what a row is owed at each of the points it has.
 *
 * <p>The unit the technique keys on. Domain testing asks for an {@code ON}, an {@code OFF}, an
 * {@code IN} and an {@code OUT} point per border, and the same value can be one role for one border
 * and another role for the next — so what owes them is a border, and nothing else can be asked. The
 * other measure this compiler makes counts how many of a position's classes some row is in, which is
 * a different unit and has no word at all for a row on the far side of a line.
 *
 * <p><b>One reading, whatever the rule cut.</b> Where a border's points are is a question about the
 * order the quantity's own values sit on, and about nothing else — so a bound on a position, a rule
 * relating two positions and a rule over an arithmetic form are read here by one procedure. Written
 * as a procedure per shape of line, the two that existed answered the same question by different
 * reasoning and a third would have been a third reasoning.
 *
 * <p><b>Two totalities and not one.</b> Which points a line has is its rule's ({@link #pointsOf}),
 * and this answers at every one of them: the answer where no row is asked for is a reason
 * ({@link Demand.NotOwed}) rather than an entry left out, so a point that goes missing stops the
 * build where the border is made. Which of the four each point is is the second question
 * ({@link #inEachRole}), and it is answered for all four words — a role can have two points and a
 * role can have none, so a reader walking the points alone is told nothing about the second kind.
 * Held as one map keyed on the role, the two were one answer and a line with two points in a role
 * had nowhere to put the second.
 *
 * <p>Which rule drew it and which line of that rule is {@link #origin}'s, which reading of that line
 * this is is the origin's too, and which of those readings are one line is {@link BoundaryLine}'s.
 * This is not another identity: a border is a line together with what it owes, and two readings of
 * one line owe the same things.
 *
 * @param cut     where the line is: what is cut, and where on it
 * @param origin  the rule that drew it, as this reading met it
 * @param answers one entry per point the line has, and no other
 */
public record Border(BoundaryTarget cut, LineOrigin origin, Map<DomainPoint, PointAnswer> answers) {

    public Border {
        if (answers == null || !answers.keySet().equals(pointsOf(origin))) {
            throw new IllegalArgumentException("a border that does not answer at every point its"
                    + " line has: " + answers + ", where its rule has " + pointsOf(origin));
        }
        // And an answer at every one of them. A key with nothing under it is the same silence the
        // point set was made total to stop, wearing the shape that was supposed to have refused it.
        if (answers.containsValue(null)) {
            throw new IllegalArgumentException(
                    "a border with a point it names and does not answer: " + answers);
        }
        // And an answer of the kind the place is. Whether a point names a value or a run of them is
        // the place's own answer, and a border that came back with a region where a value belongs
        // would have every reader of it deciding what it was holding from the shape it happened to
        // have.
        for (Map.Entry<DomainPoint, PointAnswer> each : answers.entrySet()) {
            boolean atTheLine = each.getValue() instanceof PointAnswer.AtLine;
            boolean inARegion = each.getValue() instanceof PointAnswer.InRegion;
            if ((atTheLine && !each.getKey().againstTheLine())
                    || (inARegion && each.getKey().againstTheLine())) {
                throw new IllegalArgumentException("the " + each.getKey() + " of a border,"
                        + " answered as " + each.getValue());
            }
        }
        // Walked the one way, whatever order the branch that built them filled them in. Everything
        // that shows a border's points in turn reads this, so the order they are shown in is the
        // line's and not a caller's.
        Map<DomainPoint, PointAnswer> ordered = new LinkedHashMap<>();
        for (DomainPoint point : pointsOf(origin)) {
            ordered.put(point, answers.get(point));
        }
        answers = Collections.unmodifiableMap(ordered);
    }

    /**
     * Every point this line has, which is what its rule says and not what a reading found.
     *
     * <p>One derivation, asked where a border is built and again where one is assessed, so that a
     * point cannot go missing between the two. A rule that orders the values has the value it wrote
     * and the value beside it on the side whose class differs; a rule that names a value has that
     * value and the value beside it on each side, both of which are in the class it keeps out. Both
     * have a run either way — which one of them is inside the partition the border bounds is the
     * rule's answer and no part of which point it is.
     */
    public static SequencedSet<DomainPoint> pointsOf(LineOrigin origin) {
        List<DomainPoint> points = new ArrayList<>();
        points.add(new DomainPoint.AtTheLine());
        switch (origin.lineFacts().claim()) {
            case ComparisonClaim.Cut order -> points.add(new DomainPoint.BesideTheLine(
                    order.valueBelongs().opposite()));
            case ComparisonClaim.Singled _ -> {
                points.add(new DomainPoint.BesideTheLine(Towards.BELOW));
                points.add(new DomainPoint.BesideTheLine(Towards.ABOVE));
            }
        }
        points.add(new DomainPoint.InTheRegion(Towards.BELOW));
        points.add(new DomainPoint.InTheRegion(Towards.ABOVE));
        // Walked in the order the technique names them, and the two sides of a role in the order
        // the values are in. Which point is which is the set; this is how a reader is walked
        // through it, and a report that showed a line's points in the order a switch happened to
        // fill them in would move them about as the shapes of line changed.
        points.sort(Comparator
                .comparing((DomainPoint point) ->
                        PointRole.of(point, origin.lineFacts().holdsAt(point)))
                .thenComparingInt(point -> point.side() == Towards.BELOW ? 0 : 1));
        return new LinkedHashSet<>(points);
    }

    /** What this border asks of the rows at one of its points. */
    public Demand demand(DomainPoint point) {
        return answers.get(point).demand();
    }

    /** What this border asks of the rows at one point, and what such a row would be owed for. */
    public PointAnswer answer(DomainPoint point) {
        return answers.get(point);
    }

    /** Which of the four a point of this border is, from where it is and whether the rule holds
     *  there. */
    public PointRole roleOf(DomainPoint point) {
        return PointRole.of(point, holdsAt(point, origin));
    }

    /**
     * What this border has in each of the four roles, every one of them answered.
     *
     * <p>The points classified, with the empty classes said rather than left out. A border's points
     * are places and the roles are what the rule makes of them, so a role can have two points and a
     * role can have none — and a reader walking the points alone is told nothing at all about the
     * second kind. This is the technique's four words answered for, which is a different question
     * from what a row is owed at a place ({@link Demand}) and is not folded into it.
     *
     * <p>Derived here from the points and never kept beside them. A second table would be the same
     * facts written twice, free to disagree the moment either moved.
     */
    public java.util.SequencedMap<PointRole, RoleAnswer> inEachRole() {
        java.util.SequencedMap<PointRole, RoleAnswer> byRole = new LinkedHashMap<>();
        for (PointRole role : PointRole.values()) {
            List<DomainPoint> playing = answers.keySet().stream()
                    .filter(point -> roleOf(point) == role).toList();
            byRole.put(role, playing.isEmpty() ? noPointIs(role) : new RoleAnswer.Played(playing));
        }
        return byRole;
    }

    /**
     * Why no point of this border is one of the four, where the rule accounts for it.
     *
     * <p>Proved from the rule and never read off the emptiness. A role goes unplayed exactly where
     * the class it would be in is the line's own value, which is what a rule that names one leaves:
     * an equality has no {@code IN} point and a disequality no {@code OUT} one, and an order has a
     * point in every role. So an empty role the rule does not account for is not an absence to
     * explain — it is a point that went missing between the rule and the border, wearing the shape
     * of one that never existed.
     *
     * @throws IllegalStateException where the rule accounts for no such absence
     */
    private RoleAnswer noPointIs(PointRole role) {
        boolean holdsHere = holdsAtTheValue(origin);
        if (origin.lineFacts().claim() instanceof ComparisonClaim.Singled
                && role == (holdsHere ? PointRole.IN : PointRole.OUT)) {
            return new RoleAnswer.NoPoint(RoleAnswer.Reason.THE_CLASS_AT_THE_LINE_HOLDS_ONE_VALUE);
        }
        throw new IllegalStateException("no point of " + label() + " is its " + role
                + " point, and its rule accounts for no such absence: " + answers.keySet());
    }

    /**
     * How a report names one of this border's points.
     *
     * <p>Which of the four it is, and which side of the line where this border has two of them in
     * that role. A rule that names a value owes a row outside it on each side, and the two are
     * different work — so a report that wrote the role alone would print one sentence twice and
     * leave a reader unable to say which value is being asked for.
     *
     * <p>The side and not the value. Where a point is takes a quantity to write, and a quantity
     * belongs to the reading that met the line; which side of the line it lies on is the line's own
     * and is the same at every reading of it.
     */
    public String named(DomainPoint point) {
        return roleOf(point) + whichSide(point);
    }

    /**
     * Which side of the line this point is on, where saying the role alone would not tell it from
     * another point of this border, and nothing where it would.
     *
     * <p>Written where a sentence needs the words apart from the role — a report says "the OFF
     * point below the line", with the role and the word for a point together. Read off the point
     * alone instead, every border would say a side and an order's one point outside it would be
     * given a side nobody was choosing between.
     */
    public String whichSide(DomainPoint point) {
        PointRole role = roleOf(point);
        boolean andAnother = answers.keySet().stream()
                .anyMatch(each -> !each.equals(point) && roleOf(each) == role);
        if (!andAnother) {
            return "";
        }
        return point.side() == Towards.BELOW ? " below the line" : " above the line";
    }

    /** What this border asks of the rows at the one point playing {@code role}. */
    public Demand demand(PointRole role) {
        return demand(theOne(role));
    }

    /** The same, with what such a row would be owed for. */
    public PointAnswer answer(PointRole role) {
        return answer(theOne(role));
    }

    /** What a row at the one point playing {@code role} would be owed for. */
    public List<OwedPoint> owes(PointRole role) {
        return owes(theOne(role));
    }

    /** How that point relates a row's value to what it is against, or null where none is owed. */
    public String operator(PointRole role) {
        return operator(theOne(role));
    }

    /** What it is against, or null where none is owed. */
    public String against(PointRole role) {
        return against(theOne(role));
    }

    /** How a row there describes itself, or null where none is owed. */
    public String label(PointRole role) {
        return label(theOne(role));
    }

    /**
     * The one point of this border playing {@code role}, for a caller holding a classification
     * rather than a place.
     *
     * <p>A question and never a key. Where a line has one point in a role the two say the same
     * thing, and a caller that has established which shape of line it is holding may ask this way;
     * where it has two — a rule that names a value has a point outside it on each side — the
     * question has two answers and this refuses rather than picking. Answered by picking, everything
     * downstream would go on reading one point per role and the second would be the one nobody
     * reported.
     *
     * @throws IllegalArgumentException where this border has no point in that role, or more than one
     */
    public DomainPoint theOne(PointRole role) {
        List<DomainPoint> playing = answers.keySet().stream()
                .filter(point -> roleOf(point) == role).toList();
        if (playing.size() != 1) {
            throw new IllegalArgumentException("the " + role + " point of " + label() + ", which"
                    + " has " + playing.size() + " of them: " + playing);
        }
        return playing.getFirst();
    }

    /** Whether the rule that drew a line is satisfied by a row at one of its points, which is the
     *  rule's own answer ({@link LineFacts#holdsAt}). */
    private static boolean holdsAt(DomainPoint point, LineOrigin origin) {
        return origin.lineFacts().holdsAt(point);
    }

    /**
     * What a row in one role of this reading would be owed for, which is none where none is asked
     * for and more than one where more than one thing stopped the region.
     *
     * <p>Made here, from the answer that settled both halves at once. Everything that accounts for
     * rows asks this rather than building a point out of the parts it happened to know — which is
     * how a debt came to carry the reading it was met at.
     *
     * <p>Whose account each point falls in is read here as well, and here only. Everything that
     * settled the point has arrived by this line: its own rule, and whatever stops the region
     * beside it. A caller handed the contributors would be deciding it again, and a caller that
     * forgot to decide would measure a body against a line no row written for it is owed.
     */
    public List<OwedPoint> owes(DomainPoint point) {
        BorderObligationId line = obligation();
        // The line's own rule is behind every point of it, whatever else settled the region beside
        // it: it is the line a row here stands at or beside, and an author moving it moves the
        // point.
        PointContributions own = PointContributions.by(origin.authoredLine());
        return switch (answer(point)) {
            case PointAnswer.NotOwed _ -> List.of();
            case PointAnswer.AtLine _ -> List.of(
                    new OwedPoint(new BorderObligationPoint.AtLine(line, point),
                            PointAttribution.of(own)));
            case PointAnswer.InRegion in -> in.claims().stream()
                    .map(claim -> new OwedPoint(
                            new BorderObligationPoint.InRegion(line, point, claim.basis()),
                            PointAttribution.of(own.and(claim.contributions()))))
                    .toList();
        };
    }

    /**
     * Whether this and {@code other} are one reading of one line.
     *
     * <p>What the records' own equality was being used for at the two places that ask it: finding
     * the reading a debt was made from in a later assessment, and keeping one reading's lines from
     * holding one line twice. Neither is asking whether everything about the two values matches —
     * they are asking whether it is the same border, met in the same place, owing the same things.
     *
     * <p><b>The demands and what they are owed for, and not what is written beside them.</b> Two
     * readings can ask a row for the same values and owe them to different things — the run below a
     * line the rules already stop the quantity at is owed to the line as well as to that end — so
     * the demands alone would call two borders one. What settles where a run stops, on the other
     * hand, is settled by the model, while who can move it is a fact about the reading's
     * surroundings and is no part of which border this is.
     *
     * <p>The quantity is compared as the value it is, and the place on it as a place. What a rule
     * wrote a form's coefficients as is the rule's own text and is the same at every reading of it,
     * so nothing here folds two spellings of one form.
     */
    public boolean sameReadingAs(Border other) {
        if (other == null || !origin.equals(other.origin)
                || !cut.of().equals(other.cut.of())
                // The place, and not how the rule happened to write the number: a level keeps the
                // spelling it was written in, so two readings of one line at one place can hold
                // `0` and `0.00` — which is the mistake this whole comparison is here to stop
                // being made about a border, and it would have been made about the border's own
                // place by asking the records.
                || !cut.cut().canonical().equals(other.cut.cut().canonical())) {
            return false;
        }
        if (!answers.keySet().equals(other.answers.keySet())) {
            return false;
        }
        for (DomainPoint point : answers.keySet()) {
            PointAnswer mine = answer(point);
            PointAnswer also = other.answer(point);
            // What a row here is owed for, as the set it is. Which of them a reading listed first is
            // the order its arrangement was walked in, and reading that as part of the answer would
            // put an accident of the derivation into the identity — which is the thing this method
            // exists to keep out of it. One entry per basis is the answer's own invariant, so the
            // set loses nothing.
            if (!mine.demand().sameAs(also.demand())
                    || !java.util.Set.copyOf(mine.bases()).equals(
                            java.util.Set.copyOf(also.bases()))) {
                return false;
            }
        }
        return true;
    }

    /** The same over every point this line has, in the order the points are in. */
    public List<OwedPoint> owes() {
        return answers.keySet().stream().flatMap(point -> owes(point).stream()).toList();
    }

    /**
     * What a row here is owed for, which several readings of this line share.
     *
     * <p>The line the author wrote, without the position it was read at. What folds readings
     * together keys on {@link BoundaryLine}, which is this line and where it was read; this is what
     * such a fold may not cross, and {@code Coverages} holds itself to that where it merges.
     * Everything that says where a row would go reads {@link #cut}.
     *
     * <p>Both are here rather than one being worked out from the other by whoever needs it: a caller
     * deriving a key from the parts it happened to know asks for one clause's row once per position
     * of every behavior carrying the type.
     */
    public BorderObligationId obligation() {
        return new BorderObligationId(origin.authoredLine(), cut.at());
    }

    /** Where the line is, as a report names it. Not what any one of its points asks for: that is
     *  {@link #label(PointRole)}, and the two differ at three of the four. */
    public String label() {
        return cut.label();
    }

    /**
     * The left of the line, qualified by the behavior it is an input of, as a report indexes by.
     *
     * <p>Asked of the shape of the line rather than of a field every shape was assumed to have,
     * because what a line is drawn on is not always a position: a line between two positions is on
     * neither of them and a line over a form is on none of them, and each shape says how it is
     * named.
     *
     * <p><b>Not what tells one line from another.</b> A quantity over more than one position is
     * named after the left of it, so every line from one position to a field of a sum has this same
     * word — a heading a reader groups under, and half of a line where there are two. What says
     * which line it is, is the whole of what it was drawn at ({@link #cut}); what a report writes to
     * be read is {@link #label}, and what a document publishes beside this is the other end.
     */
    public String axis() {
        return cut.named();
    }

    /** How one point relates a row's value to what it is against, or null where none is owed. */
    public String operator(DomainPoint point) {
        Criterion criterion = demand(point).criterion();
        return criterion == null ? null : criterion.operator();
    }

    /** What that point is against, or null where none is owed. */
    public String against(DomainPoint point) {
        Criterion criterion = demand(point).criterion();
        // Asked of the criterion, which is what knows whether it is written against a level or
        // against a run of them. Two of the four points name a level and two name a run, and a
        // reader that took a level from every shape wrote a value inside a run as though it were
        // the run.
        return criterion == null ? null : criterion.written(cut.of());
    }

    /** The rule that drew this line, as what a report writes about it, with {@code places} asked
     *  where the rule is one found by where it is written. */
    public PublishedSentence describe(PublishedRuleHandle.WhereARuleIs places) {
        return origin.describe(places);
    }

    /**
     * The class a row at one point of this line falls in, as one line of a class list is written.
     *
     * <p>One place, because more than one reader names it: the document a consumer joins a finding
     * to its item by, the class a generated row is offered against, and a block that shows the line
     * whosever the row at each point is. Written once per reader, the spellings agreed for a point
     * on the line and had nothing to say to each other away from it.
     *
     * <p>A point on the line is the value it is at. A point away from it carries the relation as
     * well, because what it is against alone names the border there rather than the side of it a row
     * is owed in.
     */
    public String said(DomainPoint point) {
        return point.againstTheLine() ? axis() + " = " + against(point)
                : axis() + " " + operator(point) + " " + against(point);
    }

    /**
     * How a row at one point of this border describes itself.
     *
     * <p>What the generator writes on the row it offers, so that a row and the note about the point
     * it stands for name it the same way. Null where nothing is owed in this role — there is no row
     * to label.
     */
    public String label(DomainPoint point) {
        Criterion criterion = demand(point).criterion();
        return criterion == null ? null : label(criterion);
    }

    /** The same of a criterion this border owes, for a caller that is holding one rather than a
     *  role. One spelling, so that what a search reports and what a report prints agree. */
    public String label(Criterion criterion) {
        // A run names the quantity itself, because that is how a range of it reads — `10 < n <= 20`
        // and not `n in 10 < n <= 20`. The two shapes that name a level do not, so the quantity is
        // written in front of them here.
        return criterion instanceof Criterion.Within
                ? criterion.written(cut.of())
                : cut.left() + " " + criterion.asked(cut.of());
    }

    /**
     * The border a rule drew.
     *
     * <p>Total, and never null. Whether the quantity reaches the line is settled before this and by
     * two different things: a comparison is asked through {@link #reaches} where its rule is read,
     * and comes here only where the answer was yes ({@code ComparisonAssessment.OutsideTheDomain});
     * a bound is not asked at all, because its line is an end of what it leaves and so is never
     * outside it. The check below holds the second of those, which is an invariant of this compiler
     * rather than anything a model states.
     *
     * <p>It used to be a third reading of the same question, answered with a null the two callers
     * dropped without a word: a rule of the model went unmeasured, nothing recorded that it had, and
     * the measure came back saying the behavior's rules draw no line anywhere (issue #1079).
     *
     * <p><b>Where the rule stops is not where a row is written.</b> The two are one value on a
     * carrier that steps, because a strict end is moved onto the value it leaves before it ever gets
     * here — {@code value > 5} on an {@code Int} arrives as an inclusive 6. A carrier with no step
     * has no such move, so {@code value > 5.0m} arrives as an exclusive 5, and the point against
     * that line is a value the order cannot name. Both are lines. Which value each of the four
     * points stands at is asked of the order below, and never read off the cut.
     *
     * @param within what the rules leave the quantity, on the quantity's own order. Null where they
     *               leave it everything
     */
    public static Border at(BoundaryTarget target, LineOrigin origin, NumericDomain.Bounds within) {
        return at(target, origin, within, List.of());
    }

    /**
     * The same, told where else the rules part this quantity's values.
     *
     * <p>Which is what the two points away from the line are read off. An {@code IN} point is inside
     * the partition this border bounds; read as everything past the line, it runs to the end of the
     * order, and a row past the next line along answers for it while the partition between them has
     * nothing in it (issue #880). The two are the same only where the quantity has one line through
     * it, which is why nothing noticed for as long as a border was built one rule at a time.
     *
     * @param parted every place the rules part this quantity's values, this border's own among them
     *               or not
     */
    public static Border at(BoundaryTarget target, LineOrigin origin, NumericDomain.Bounds within,
                            List<Parting> parted) {
        return at(target, origin, within, parted, NarrowedBounds.NOTHING);
    }

    /**
     * The same, told what the value the position sits in leaves it and which declarations hold each
     * end of that.
     *
     * <p>Which is what a run stopping at one of those ends is owed to besides the line it lies
     * against. Carried from the reading that placed the end, because nothing downstream can work it
     * out: an end is where every rule about the position leaves off, and the number it leaves off at
     * says nothing about who moved it.
     *
     * <p><b>The reading and not the names it came to.</b> What {@code within} leaves this quantity
     * is a second reading — the behavior's own rules are in it, and the record's reading knows
     * nothing of them — so the two can stop the position in different places, and a name worked out
     * against one of them says nothing about the other. Which is why what arrives here is something
     * that can be asked about an end, rather than an answer already given about somebody else's.
     */
    public static Border at(BoundaryTarget target, LineOrigin origin, NumericDomain.Bounds within,
                            List<Parting> parted, NarrowedBounds narrowed) {
        NumericDomain.Bounds reach = within == null ? new NumericDomain.Bounds(null, null) : within;
        LevelSpace space = target.levels();
        Level cut = target.at();
        if (!reaches(cut, seamOf(space, cut, origin), origin.lineFacts().claim(),
                drawnByAnInvariant(origin), within)) {
            // Asked and answered by whoever holds the rule. Reaching here is that reader and this
            // one disagreeing about one line, which is not a state a model can put them in.
            throw new IllegalStateException(
                    "a border built on a line the quantity does not reach: " + target.left()
                            + " at " + target.right());
        }
        List<Parting> mine = partedBy(target, origin);
        List<Parting> all = new ArrayList<>(parted);
        // Handed over as candidates rather than told apart here. Whether this line is one the
        // others already hold is a question about where the values part, and the arrangement is
        // what answers it — asked here as well, this reading kept whichever of two lines at one
        // place it met first and the other went unsaid.
        all.addAll(mine);
        QuantityArrangement arrangement = QuantityArrangement.of(space, all,
                DomainEnds.leaving(space, cut, reach, narrowed));
        Map<DomainPoint, PointAnswer> demands = new LinkedHashMap<>();
        if (drawnByAnInvariant(origin)) {
            aBound(demands, origin, target.of(), cut, space, reach, arrangement);
            return new Border(target, origin, demands);
        }
        againstTheLine(demands, origin, cut, space, reach);
        // Each side runs from the point against the line on that side, where there is one, and from
        // the line itself where there is not: the values one step away are not there to be left out,
        // and everything past the line is then as far from the border as anything gets.
        // The partition this border bounds and the one it keeps out, each without the value against
        // the line — which is that side's own point against the line and is not this one. A side the
        // rules leave nothing in is not a run of the arrangement at all, and that is the answer here
        // too. Each of the two is told which line it is named for: this border's own, which the run
        // it asks for lies one way of. Worked out from the run instead, the two points that share a
        // run — this border's run above and the next border's run below — both started at the same
        // end of it.
        for (Towards side : Towards.values()) {
            demands.put(new DomainPoint.InTheRegion(side),
                    runOf(space, runBeside(arrangement, mine, side),
                            levelAgainstTheLineOn(demands, origin, side), side));
        }
        return new Border(target, origin, demands);
    }

    /**
     * The point at the line, and the nearest value on each side of it whose class differs.
     *
     * <p>One law for both shapes of line a body writes. A row is owed at the value the rule wrote,
     * and at the nearest value on a side exactly where the values there fall in another class than
     * that value does — which is one side of a rule that orders the values and both sides of one
     * that names a value. Written as a procedure per shape of line, the second has no way to hold
     * two points of one role, and what it can say instead is that neither neighbour is the nearer.
     *
     * <p>Which of the four each of them is is nobody's choice here: it follows from where the point
     * is and whether the rule holds there ({@link PointRole#of}).
     */
    private static void againstTheLine(Map<DomainPoint, PointAnswer> demands, LineOrigin origin,
                                       Level cut, LevelSpace space, NumericDomain.Bounds reach) {
        switch (origin.lineFacts().claim()) {
            case ComparisonClaim.Cut order -> {
                // The same `at` is the point inside the line of `<= 3000` and the point outside the
                // line of `< 3000`, and neither is the threshold at all where the quantity does not
                // take it: which way to look for the value the rule wrote is the side that value
                // belongs to.
                Towards belongs = order.valueBelongs();
                demands.put(new DomainPoint.AtTheLine(),
                        pointAt(space, cut, belongs, true, reach));
                demands.put(new DomainPoint.BesideTheLine(belongs.opposite()),
                        pointAt(space, cut, belongs.opposite(), false, reach));
            }
            case ComparisonClaim.Singled _ -> {
                // The value itself, and never a value the order names near it: what such a rule
                // names is that value, and a row at the value beside it is a row the rule is not
                // about.
                demands.put(new DomainPoint.AtTheLine(),
                        new PointAnswer.AtLine(new Criterion.AtTheLevel(cut)));
                for (Towards side : Towards.values()) {
                    demands.put(new DomainPoint.BesideTheLine(side),
                            pointAt(space, cut, side, false, reach));
                }
            }
        }
    }

    /**
     * The run the arrangement leaves on one side of this line.
     *
     * <p>Asked of the places this rule parts the values, and of the side. A rule that orders them
     * parts them at one place and each side of that place is a run of the arrangement; a rule that
     * names a value parts them at two, and the run on a side is the one past the place on that side.
     * Told the line instead of the place, a rule with two of them would have both its runs looked up
     * from whichever place was recorded first.
     */
    private static QuantityArrangement.Run runBeside(QuantityArrangement arrangement,
                                                     List<Parting> mine, Towards side) {
        return arrangement.beside(mine, side);
    }

    /** The value against the line on one side of it, which the run there is asked for without. Null
     *  where the order names no value there, and then the whole run is what is asked for. */
    private static Level levelAgainstTheLineOn(Map<DomainPoint, PointAnswer> demands,
                                               LineOrigin origin, Towards side) {
        boolean atTheLine = origin.lineFacts().claim() instanceof ComparisonClaim.Cut order
                && order.valueBelongs() == side;
        PointAnswer point = demands.get(atTheLine ? new DomainPoint.AtTheLine()
                : new DomainPoint.BesideTheLine(side));
        return point == null ? null : against(point);
    }

    /**
     * Whether the quantity reaches the line at all, which is the one thing about a border that does
     * not depend on the lines beside it.
     *
     * <p>Asked before the lines of one quantity are collected, because a rule that draws nothing has
     * to be reported as drawing nothing rather than joining the arrangement: three times a length is
     * never negative, and a rule comparing one against a negative draws no line for anything to be
     * beside.
     *
     * <p><b>Asked of the side the rule keeps, and of where it parts the values there.</b> A rule
     * draws somewhere exactly when the values it keeps are values the rules leave — so what is asked
     * is one place and not the whole line, and it is the place the rule leaves rather than the
     * number it wrote. Asked at the number, a rule refusing its own threshold on a carrier that
     * counts parts the values one step inside the range and reads as one cutting outside it; asked
     * of both sides, a line beyond the range has the side it refuses pulled back inside and reads as
     * one cutting within.
     *
     * <p>Which keeps a carrier with no step drawing. Such a rule names no value beside its line —
     * the quantity comes arbitrarily close and never arrives — and what it keeps is the line itself,
     * so the question is asked there and answered. Asked as whether the rules leave the line's own
     * value, every strict bound on a {@code Decimal} was read as drawing no line, and a model whose
     * every rule was one came back adequate on the strength of no measure at all.
     *
     * <p><b>And a rule that names a value is asked about that value.</b> What such a rule parts is
     * the one value from every other one, so it divides what the rules leave exactly when they
     * leave the value it names — which is a question about the value and about neither side of it.
     * Put the ordering rule's question, such a rule is asked about the value one step along, and at
     * the top of a range there is none: {@code value /= 100} under {@code value <= 100} would draw
     * no line at all, and a rule of the model would go unmeasured.
     *
     * @param parts where the rule parts the values, of a rule that ordered them. Null where it
     *              names a value instead, which parts them at no one place
     * @param claim what the rule placed on the values, which is what says which question this is
     */
    public static boolean reaches(Level cut, Seam parts,
                                  ComparisonClaim claim,
                                  boolean drawnByAnInvariant, NumericDomain.Bounds within) {
        if (within == null) {
            return true;
        }
        return switch (claim) {
            case ComparisonClaim.Singled _ -> admits(within, cut);
            case ComparisonClaim.Cut order -> {
                Towards kept = order.satisfyingSide();
                yield standsAt(within, cut, parts, kept)
                        // A line with two sides owes a point against the line on each of them, so
                        // the line's own value is enough for it to be one somebody can write a row
                        // against: the side a rule is satisfied on may hold nothing while a row at
                        // the line settles which way the behavior went. Asked of the satisfying
                        // side alone, a guard the declarations can never satisfy took its whole
                        // line with it, and the row showing the behavior going the other way was
                        // never asked for.
                        //
                        // The line and not the whole of the other side. Every value a length takes
                        // is above a line drawn below zero, and the rule divides them into nothing
                        // — asked of that side, a rule that puts everything in one class came back
                        // as one with two.
                        //
                        // Nothing outside a bound can be constructed, so a bound has no second side
                        // for the line's own value to settle anything about.
                        || (!drawnByAnInvariant && admits(within, cut));
            }
        };
    }

    /**
     * Whether the rules leave a value where this line parts them on one side.
     *
     * <p>The line itself where the seam has nothing to name there in the quantity's own units, which
     * is a rule that wrote a multiple of the quantity and refuses its threshold. The line is where
     * the values part to within one step of the quantity either way, and a caller with no place to
     * ask about has nothing better to ask about than that.
     */
    private static boolean standsAt(NumericDomain.Bounds within, Level cut, Seam parts,
                                    Towards side) {
        Level leaves = parts.leaving(side);
        return admits(within, leaves == null ? cut : leaves);
    }

    /** Whether {@code within} holds the value at {@code level}. */
    private static boolean admits(NumericDomain.Bounds within, Level level) {
        Place at = placeOf(level);
        return (within.min() == null || at.compareTo(within.min().at()) >= 0)
                && (within.max() == null || at.compareTo(within.max().at()) <= 0);
    }

    /**
     * Every border the lines of one behavior draw, each told about the others on its own quantity.
     *
     * <p>One place, so that a quantity is arranged once however many rules cut it. Grouped by what
     * the rule cuts and not by how it was written: a form and any positive multiple of it order the
     * rows the same way, so they are one quantity and their lines are one arrangement.
     */
    public static List<Border> allOf(List<LineDrawn> drawn) {
        return allOf(drawn, java.util.Map.of(), new LinesRead());
    }

    /**
     * The same, told where else the rules of this behavior part the quantities these lines are on.
     *
     * <p>Because a quantity is arranged once and not once per producer. A line that divides a
     * position leaves its border on the position and its division on the axis; a line that divides
     * one and has no value there leaves its border here. Read apart, the second knew only its own
     * line — a rule cutting at a third beside a rule cutting at a fifth asked for a row anywhere at
     * all, twice — and the first knew both only because the axis happened to carry them.
     *
     * @param alsoParted where the rules part each quantity, in the quantity's own units, by
     *                   {@link QuantityKey#key}
     */
    public static List<Border> allOf(List<LineDrawn> drawn,
                                               java.util.Map<String,
                                                       List<Parting>> alsoParted,
                                               LinesRead read) {
        // Collected in the quantity's own units, because that is the only order the lines of one
        // quantity are all on. Two rules can write one quantity at two scales — `3a + 6b > 48` and
        // `a + 2b > 20` run the same way — and the numbers they carry are not comparable until both
        // are read as what they are a multiple of.
        java.util.Map<String, List<Parting>> byQuantity = new LinkedHashMap<>();
        alsoParted.forEach((key, parted) ->
                byQuantity.computeIfAbsent(key, _ -> new ArrayList<>()).addAll(parted));
        for (LineDrawn each : drawn) {
            if (!ordersAroundTheCut(each.by())) {
                continue;
            }
            // Every line as it was read. Two of them at one place are one place with two lines
            // against it, which the arrangement says and this does not: told apart here, whichever
            // was read first stood for the other and the second line was not written down anywhere.
            byQuantity.computeIfAbsent(each.cuts().quantity().key(), _ -> new ArrayList<>())
                    .add(Parting.by(each.cuts().seam(), each.by().authoredLine()));
        }
        List<Border> out = new ArrayList<>();
        for (LineDrawn each : drawn) {
            // And read back into the units this rule wrote, which is what its own quantity measures
            // a row in. A border reads rows through the form it was written as, so a run handed to
            // it in another scale would be held against numbers of a different size.
            java.math.BigDecimal per = each.cuts().per();
            List<Parting> beside =
                    byQuantity.getOrDefault(each.cuts().quantity().key(), List.of())
                            .stream().map(parting -> parting.scaledBy(per)).toList();
            // One line drawn, one border. Which lines there are was settled by whoever read the
            // rules — a comparison whose line the quantity does not reach is no line, and says so
            // there ({@code ComparisonAssessment.OutsideTheDomain}) — so nothing here decides it
            // again and nothing is dropped for having come back empty.
            //
            // Written down as it is met and again where it lands. One line met twice by this
            // reading is one line, which is why the second of two equal borders is not a border
            // this reading lost — the account is asked of the lines and not of how many times the
            // loop went round.
            read.found(each.cuts().target(), each.by());
            Border made = at(each.cuts().target(), each.by(), each.cuts().within(), beside);
            if (out.stream().noneMatch(had -> had.sameReadingAs(made))) {
                out.add(read.drew(made));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Where the rule this reading met parts the quantity's values, and the line it wrote there.
     *
     * <p>For a caller collecting every place one quantity is parted before any border is built. A
     * border's two points away from the line are runs of the arrangement those places make, so a
     * border built without them knows only its own line and reads each side to the end of the order.
     *
     * <p>The line travels with the place because what a run beside it is owed to is the line and not
     * the place: two rules can part the values at one number, and each of them is one an author
     * could move without touching the other.
     */
    public static List<Parting> partedBy(BoundaryTarget target, LineOrigin origin) {
        LevelSpace space = target.levels();
        Level cut = target.at();
        if (drawnByAnInvariant(origin)) {
            // A bound is where the quantity stops rather than a place its values part: nothing
            // outside one can be constructed, so there is no run on the far side.
            return List.of();
        }
        return switch (origin.lineFacts().claim()) {
            case ComparisonClaim.Cut order -> List.of(Parting.by(Seam.of(space, cut,
                    order.valueBelongs()),
                    origin.authoredLine()));
            // The place under the value and the place over it, with the value between them. Which
            // of the two bounds which side is the arrangement's answer and not this one's: a run
            // is on a side of the line by what stops it, and that is a fact about the runs.
            case ComparisonClaim.Singled _ -> List.of(
                    Parting.by(Seam.of(space, cut, Towards.ABOVE), origin.authoredLine()),
                    Parting.by(Seam.of(space, cut, Towards.BELOW), origin.authoredLine()));
        };
    }

    /**
     * A row anywhere in one run but at the value against the line, or the reason the rules leave no
     * run there.
     *
     * <p>Whether anything is left is the order's answer about the item's own values, and not a
     * reading of where the run starts and stops: the rules may leave a side one value wide and that
     * value be the border's own point, and they may leave a run between two lines that the quantity
     * takes no value in at all. Told apart by comparing the two ends instead, the second was owed a
     * row nobody can write and the report said the search stopped looking for it.
     */
    private static PointAnswer runOf(LevelSpace space, QuantityArrangement.Run run, Level except,
                                     Towards away) {
        if (run == null) {
            return new PointAnswer.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT);
        }
        Criterion.Within inside = new Criterion.Within(run.values(), except, away);
        // What the run is owed to, from the same reading that said what it asks: the line this point
        // is named for is the border's own, and the far side is where the run stops — which is the
        // end lying the way the run does.
        return inside.region().parts().stream().anyMatch(part -> space.inspect(part).any())
                ? new PointAnswer.InRegion(inside, run.endsAt(away))
                : new PointAnswer.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT);
    }

    /** The level a point against the line stands at, or null where no row is owed there. */
    private static Level against(PointAnswer point) {
        return point.criterion() instanceof Criterion.AtTheLevel at ? at.at() : null;
    }

    /**
     * One of the two points of a line the rule orders the values around, or why no row is owed there.
     *
     * <p>Three reasons and one row, and the three used to be one silence. Whether the threshold is a
     * level the quantity takes says whether either point is at the threshold itself; the order says
     * whether it names a value the way this point lies; and what the rules leave says whether that
     * level is one a row can be written at — {@code value >= 10} under {@code x < 10} would otherwise
     * be owed a 9 the record refuses.
     *
     * @param isTheThreshold whether this point is the threshold itself, where the quantity takes it
     */
    private static PointAnswer pointAt(LevelSpace space, Level cut, Towards towards,
                                       boolean isTheThreshold, NumericDomain.Bounds reach) {
        if (isTheThreshold && space.attainable(cut)) {
            // Admitted already: a threshold the rules refuse is a line this never made.
            return new PointAnswer.AtLine(new Criterion.AtTheLevel(cut));
        }
        Optional<Level> at = beyond(space, cut, towards);
        if (at.isEmpty()) {
            return new PointAnswer.NotOwed(NotOwedReason.THE_CARRIER_NAMES_NO_NEIGHBOUR);
        }
        return reach.admits(placeOf(at.get()))
                ? new PointAnswer.AtLine(new Criterion.AtTheLevel(at.get()))
                : new PointAnswer.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT);
    }

    /**
     * The nearest level the quantity takes on one side of the threshold.
     *
     * <p>The value beside the threshold where the quantity takes the threshold, and the first value
     * it does take otherwise. Two questions the order answers apart: {@code 2 * a <= 9} has no
     * neighbour of 9 to ask for, because 9 is not a level it stands at, and the level it stands at
     * below 9 is not one step from anything.
     */
    static Optional<Level> beyond(LevelSpace space, Level cut, Towards towards) {
        return space.attainable(cut) ? space.neighbour(cut, towards)
                : space.nearestAtOrBeyond(cut, towards);
    }

    /**
     * A line nothing can stand outside of, which is what a clause of a value's declarations draws.
     *
     * <p>The one shape whose points are not read off the order alone. Everything past such a line is
     * refused at construction, so the point beside it out there and the run out there are values no
     * row can hold — which is a fact about the construct the rule is written in, and is why this is
     * answered here rather than by asking the arrangement for a run it would rightly hand over.
     *
     * <p>Which side the values are kept on is what the reading that placed the end recorded, and the
     * point against the line is the value a row inside stands at: the line's own where the bound
     * admits it, and the nearest value it does leave where the bound refuses it.
     */
    private static void aBound(Map<DomainPoint, PointAnswer> demands, LineOrigin origin,
                               BorderQuantity of, Level cut, LevelSpace space,
                               NumericDomain.Bounds within, QuantityArrangement arrangement) {
        Towards kept = satisfyingSide(origin);
        boolean holdsHere = holdsAtTheValue(origin);
        // Asked of a bound on a position and of nothing else. A position's range ends where its own
        // bound is, so the two readings are answers to one question and are held against each other.
        // What a rule relating positions cuts runs between whatever their ranges leave it, and its
        // line falls inside that — so the range is no account of where this rule stops, and
        // requiring the two to agree would refuse every relation a model states.
        if (of.aBoundOnItEndsItsRange()) {
            requireItIsTheEndItKeeps(within, space, cut, kept, origin);
        }
        // The value the rule wrote is a value a row can hold exactly where the bound admits it. A
        // bound refusing its own threshold keeps the values from one step in, and that step is the
        // row a person writes.
        DomainPoint inside = holdsHere ? new DomainPoint.AtTheLine()
                : new DomainPoint.BesideTheLine(kept);
        DomainPoint outside = holdsHere ? new DomainPoint.BesideTheLine(kept.opposite())
                : new DomainPoint.AtTheLine();
        PointAnswer on = againstABound(space, cut, kept, holdsHere, within);
        demands.put(inside, on);
        demands.put(outside, new PointAnswer.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT));
        // The partition the bound bounds, without the value against the line.
        //
        // Found by the point and not by the cut, and short of a point by the cut. The two are one
        // level wherever the position holds the line's own value, and where it does not the run
        // starts past the cut: looked up by the cut, the run a bound leaves was no run of the
        // arrangement at all and the row away from its line came back refused.
        Level against = against(on) != null ? against(on) : cut;
        demands.put(new DomainPoint.InTheRegion(kept),
                runOf(space, arrangement.endmost(kept), against, kept));
        demands.put(new DomainPoint.InTheRegion(kept.opposite()),
                new PointAnswer.NotOwed(NotOwedReason.THE_RULES_REFUSE_IT));
    }

    /**
     * The point against a bound's line: the value a row inside it is written at, or why there is
     * none.
     *
     * <p>Asked of the order and never read off the cut. Where the position holds the line's own
     * value that value is the point, and where it does not the point is the nearest value the rules
     * leave — which on a carrier with no step is no value at all, and then the technique's point
     * cannot be written down.
     *
     * <p><b>And a bound that stops short of its own line where the order does name a value beside
     * it is refused, not repaired.</b> Such an end is not canonical: a strict bound is moved onto
     * the value it leaves where the carrier steps, by {@code InvariantBound} where a statement
     * places an end and by the solver for what a record leaves, so {@code value > 5} on an
     * {@code Int} reaches
     * here as an inclusive 6 and never as an exclusive 5. Answered by stepping to the 6 here, this
     * would be a third place that normalizes ends — and the day either of the two above stopped
     * doing it, the border would come out right and nothing would say the reading had been repaired
     * on its way through.
     *
     * <p>No carrier is asked. What tells the two apart is whether the order names a value on the
     * side the bound keeps, which is the question the point is about anyway.
     */
    private static PointAnswer againstABound(LevelSpace space, Level cut, Towards kept,
                                             boolean holdsHere, NumericDomain.Bounds within) {
        if (holdsHere) {
            return pointAt(space, cut, kept, true, within);
        }
        // And where it refuses its own threshold, the value beside it on the side it keeps — which
        // is what every other role of every other line is offered. A rule refusing its threshold
        // keeps the values from one count in, and that count is the row a person writes.
        return pointAt(space, cut, kept, false, within);
    }

    /**
     * That a bound's line stands where what the rules leave stops on the side the bound keeps.
     *
     * <p>Two readings of one rule held against each other. Which way the bound runs comes from the
     * reading that placed the end and the range comes from the reading of everything the position
     * is left with, and a bound's line is by construction the end of that range on the side it
     * keeps. Where the two disagree, one of them is about a rule the other did not read — which is
     * not a state a model can put them in, and a line inside the range is not one this rule drew.
     *
     * <p>The side and not either end. A rule leaving one value puts both ends at the line, and each
     * of its two bounds answers for the end it placed rather than for whichever end matches.
     */
    private static void requireItIsTheEndItKeeps(NumericDomain.Bounds within, LevelSpace space,
                                                 Level cut, Towards kept, LineOrigin origin) {
        Endpoint end = within == null ? null
                : kept == Towards.ABOVE ? within.min() : within.max();
        // Where the rule leaves off, which is not always the number it wrote. A carrier that counts
        // has a next value, so a rule refusing its own threshold keeps the values from one count in
        // — and the range this is held against stops there too. Held against the number instead,
        // the two agree wherever a rule admits its threshold and are one count apart wherever it
        // does not, which is half the operators an author can write.
        Level leaves = Seam.of(space, cut, valueBelongs(origin)).leaving(kept);
        if (end == null || !end.at().sameAs(placeOf(leaves))) {
            throw new IllegalStateException(
                    "a bound whose line is not where what it leaves stops: "
                            + origin.saidWithoutAPlace());
        }
    }

    /**
     * A level as a place on the order it is a level of.
     *
     * <p>The one narrowing, so that what the rules leave — which is written as places, because it is
     * read off the declarations — can be held against what the quantity takes. Both are on the
     * quantity's own order by construction: the bounds handed in are the bounds of the thing being
     * cut, and a caller that handed in a position's bounds for a border over something else would be
     * answering a different question here as well.
     */
    private static Place placeOf(Level level) {
        return switch (level) {
            case Level.OnACarrier on -> on.at();
            case Level.ACount count -> count.at();
        };
    }

    /**
     * Which way from the threshold the rule is satisfied.
     *
     * <p>Derived here from the two things a rule says about its own threshold, rather than asked of
     * each producer. Which side the threshold's own value belongs to and whether the rule holds there
     * are what every rule records ({@link LineOrigin}); which way the rule is satisfied follows from
     * the pair and is what a border is read off. Recorded a third time it would be free to disagree
     * with them, and a line whose sides were the wrong way round asks for two rows that prove
     * nothing.
     */
    private static Towards satisfyingSide(LineOrigin origin) {
        return ordering(origin).satisfyingSide();
    }

    /**
     * The order this rule placed on the values either side of its line.
     *
     * <p>Asked only of a rule that placed one. Which side of its value a rule that names one is
     * satisfied on is not a question that rule answers — what it parts is that value from every
     * other one, and both sides of it are the same class — so a caller here is holding a line whose
     * two sides it has already established.
     */
    private static ComparisonClaim.Cut ordering(LineOrigin origin) {
        if (origin.lineFacts().claim()
                instanceof ComparisonClaim.Cut order) {
            return order;
        }
        throw new IllegalStateException("which way a rule is satisfied from its line, asked of one"
                + " that names a value and orders nothing: " + origin.saidWithoutAPlace());
    }

    /**
     * Whether the rule orders the values around its threshold at all.
     *
     * <p>Asked of the origin here rather than answered by it. Which rule drew a line and whether that
     * line has two sides are two questions, and only the first is the rule's — the second is what
     * this measure does with the answer, and reading it off the origin put the vocabulary of borders
     * inside the identity every other measure of a rule shares.
     */
    private static boolean ordersAroundTheCut(LineOrigin origin) {
        return ordersAroundTheCut(drawnByAnInvariant(origin), origin.lineFacts().singles());
    }

    /**
     * Whether a clause of a value's own declarations drew this line.
     *
     * <p>Nothing outside such a rule can be constructed, which is a fact about the construct the
     * rule is written in and not about what its comparison placed. Read off the claim instead, a
     * bound and a {@code guard} written with the same operator would have to differ there — and
     * they do not: both order the values either side of the number, and only one of the two has a
     * far side anything can stand on.
     */
    private static boolean drawnByAnInvariant(LineOrigin origin) {
        return switch (origin) {
            case LineOrigin.InvariantOrigin _ -> true;
            case LineOrigin.NarrowedOrigin n -> drawnByAnInvariant(n.bound());
            case LineOrigin.ComparisonOrigin _, LineOrigin.EnsuresOrigin _ -> false;
        };
    }

    /**
     * Where this rule parts the quantity's values, or null where it parts them at no one place.
     *
     * <p>A seam is an order's: the last value on one side and the first on the other. A rule that
     * names a value has the values beside it on both sides of what it distinguishes, so there is no
     * one place to put a seam at — and whoever asks about such a line asks about the value itself.
     */
    private static Seam seamOf(LevelSpace space, Level cut, LineOrigin origin) {
        if (!(origin.lineFacts().claim()
                instanceof ComparisonClaim.Cut order)) {
            return null;
        }
        return Seam.of(space, cut, order.valueBelongs());
    }

    /**
     * The same, of a reading that has not named a rule for its line yet.
     *
     * <p>One derivation, asked by whoever holds the two facts. Whether a line has two sides is
     * settled before a border is built — a reading has to know which points its line will owe
     * before it can ask whether anything can stand at them — and it is settled again once the rule
     * is named. Worked out twice, a line would owe one side while it was a reading and two once it
     * had a rule.
     *
     * @param drawnByAnInvariant whether what drew it is a clause a value's declarations state.
     *                           Nothing outside such a rule can be constructed, so there is no far
     *                           side to order against. An {@code ensures} is written on a
     *                           declaration too and is not one of these: what it states is a
     *                           relation the behavior is held to, and both its sides hold values
     * @param singles            whether the rule names a value rather than a side, which orders
     *                           nothing around itself either
     */
    static boolean ordersAroundTheCut(boolean drawnByAnInvariant, boolean singles) {
        return !drawnByAnInvariant && !singles;
    }

    /** Whether the threshold's own value satisfies the rule that drew the line. */
    private static boolean holdsAtTheValue(LineOrigin origin) {
        return origin.lineFacts().holdsAtTheValue();
    }

    /** Which side of the line the threshold's own value belongs to, of a rule that ordered them. */
    private static Towards valueBelongs(LineOrigin origin) {
        return ordering(origin).valueBelongs();
    }
}
