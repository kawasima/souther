package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.semantics.ConstantArguments;
import souther.compiler.semantics.ResultRange;
import souther.compiler.types.ApplicationDerivationCause;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BinOp;
import souther.compiler.types.ReferenceDerivationCause;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a condition states, read off the condition and nothing else.
 *
 * <p>The half of reading a guard that no path is involved in. Two questions were one here for as
 * long as one reader asked both: what a condition <em>says</em> — which comparison it is once the
 * six ways of writing one are read as one, what an emptiness check means, which relation an order
 * operation states — and what saying it makes <em>known</em> on the path it was written on. The
 * first is a function of the condition and the names around it; the second needs what the path had
 * already established. {@link Predicates} keeps the second and asks this for the first.
 *
 * <p>Which is what lets the answer be recorded. A relation a condition states is the same relation
 * wherever it is read, so it may stand beside a recipe ({@link Derivation.Chosen}) where a range
 * could not — and an arm read under what chose it is read against a domain the arm's own reader
 * holds, out of relations recorded here.
 *
 * <p>Functions and not an object. Nothing here has state or a lifetime, and {@link Terms} is passed
 * where a name has to be read rather than held: a reader this one could be given at construction is
 * a reader something has to remember to give it, and the naming of an expression is reached from
 * places that have no {@link Predicates} to hand ({@link ContractDischarge}).
 */
final class Conditions {

    private Conditions() {}

    /**
     * What choosing the arm {@code decidedBy} states of the values around it, as relations.
     *
     * <p>Only what holds by that arm being the answer, and only what holds on its own. A condition
     * asserted true states each of its conjuncts; asserted false it states the disjunction of their
     * denials, which is not a list of relations, so it states none of them and this answers with
     * nothing. Under-answering costs precision and over-answering is unsound, so where the shape
     * runs out this stops — which is the opposite way round from what a recipe declares it may read
     * ({@link Derivation.Chosen.Arm}), and worth keeping straight.
     *
     * <p>A case and an attempt state nothing here. What choosing them settles is what the value's
     * type guarantees of what the arm binds, which is a declaration's answer and not a relation a
     * condition wrote, so it is read through the one reading of a declaration there is
     * ({@link TypeGuarantees}) and composed with this beside the arm ({@link Terms#chosen}).
     *
     * <p>An operation the library defines by cases states the relations its case is reached under,
     * which arrive already lowered to the values the call was given ({@link Choice}). Nothing about
     * the operation is asked here, and nothing about it needs to be.
     *
     * <p>Written as a switch over {@link Choice.Decides} with an arm for every way of deciding. As an
     * {@code instanceof} it answered "nothing is settled" for every way it had not been told about,
     * which is the same silence a new producer would want to be stopped by: a value that is one of
     * several would get its arms and lose what chose between them, and be bounded by their span with
     * no arm ruled out.
     *
     * <p><b>What the values a condition names guarantee is not here either, and the walk has it.</b>
     * {@link Predicates} takes what a size and what an operation's result are guaranteed to be into
     * what is known before it reads the condition at all: a size
     * is at or above nought whether the condition holds or not, so those are guarantees about values
     * and not statements the condition makes. An arm read here is read without them, so
     * {@code List.size(xs)} under {@code not List.isEmpty(xs)} comes to "not nought" here where the
     * walk has "above nought". Not two readings of one question — it is one question this reader
     * does not ask. It was written down as the same family as what a case and an attempt rest on,
     * and it is not: a value built through a checked constructor carries what its declaration
     * states ({@link TypeGuarantees}), and a list carries a length that is never negative because of
     * what a length is, with no declaration saying so. A third source, and #988.
     */
    static List<NumericConstraint> settledBy(Terms terms, Choice.Decides decidedBy, Denotations at) {
        List<NumericConstraint> out = new ArrayList<>();
        switch (decidedBy) {
            case Choice.Decides.ACondition(Core cond, boolean holding) ->
                    stating(terms, cond, at, holding, out);
            case Choice.Decides.ByArgumentRelations(List<Choice.ArgumentRelation> relations) ->
                    standing(terms, relations, at, out);
            // A case and an attempt state nothing here. What choosing them settles is what the
            // value's type guarantees of what the arm binds, which is a guarantee about a place
            // rather than a relation, and it is read where places are seeded (#982). Written as arms
            // of this switch and not left to a default: what a way of deciding settles is a question
            // somebody has to answer, and "nothing" is an answer rather than the absence of one.
            case Choice.Decides.ACase ignored -> { }
            case Choice.Decides.ItWasBuilt ignored -> { }
            case Choice.Decides.ItDeparted ignored -> { }
        }
        return out;
    }

    /**
     * The relations the arguments of a library definition's case stand in, as constraints.
     *
     * <p>Already relations when they arrive ({@link Choice.ArgumentRelation}), so what is left is
     * reading each side as a form. A side no form is read of — an argument that is the answer of
     * something nothing names — states nothing and is left out.
     *
     * <p>Which is one answer for both readers of these arms, and it has to be. Dropping a relation
     * leaves the arm reachable where it is not: a recipe then spans an answer it need not have
     * ({@link DerivedNumericFacts}), and a clause read case by case is then asked to hold in a case
     * the values cannot reach ({@link Predicates.Piecewise}). Both directions cost precision and
     * neither states anything the values fail, which is the rule the class doc gives — and it is why
     * this is decided here rather than by each reader, where the two could come to degrade
     * differently and nothing would say which of them was reading the same choice.
     */
    private static void standing(Terms terms, List<Choice.ArgumentRelation> relations,
                                 Denotations at, List<NumericConstraint> out) {
        for (Choice.ArgumentRelation one : relations) {
            LinearForm<FactSubject> left = terms.affineOf(one.left(), at);
            LinearForm<FactSubject> right = terms.affineOf(one.right(), at);
            if (left != null && right != null) {
                out.add(new NumericConstraint(left.minus(right), one.rel()));
            }
        }
    }

    /**
     * The relations {@code cond}, asserted with polarity {@code positive}, states on its own.
     *
     * <p>Read through the same normalisations a guard is: an emptiness check is the comparison it
     * means, an order operation compared against zero is the comparison of what it orders, and a
     * negation is the condition under it with the polarity turned over. Without them the six ways of
     * writing one comparison are six conditions, and what an arm states would depend on which of
     * them an author wrote.
     *
     * <p>A conjunction asserted true states both halves, and a disjunction asserted false states
     * both denied. Anything else with two halves states neither: one of them holds and this cannot
     * say which.
     */
    static void stating(Terms terms, Core rawCond, Denotations at, boolean positive,
                        List<NumericConstraint> out) {
        out.addAll(new Stating(terms).read(rawCond, positive, at, terms::inside));
    }

    /**
     * What a condition states, read over the shape it was written in.
     *
     * <p>Over the shape and not over the tree, so that what a connective composes is recognised in
     * one place ({@link ClauseExpr}) and this reading agrees with every other by having been given
     * the answer. And a binding is crossed on the way in, so a rule stated through a helper states
     * what the same rule written out states — read as a shape with no word for it, such a rule
     * stated nothing at all.
     */
    private record Stating(Terms terms)
            implements ClauseReading<List<NumericConstraint>, Denotations> {

        /**
         * Every reading of one part, because each of them holds of the values: an arm read without
         * one of them is an arm bounded by less than what choosing it settles.
         *
         * <p>Read through the same normalisation a guard is, which is where it belongs: an
         * emptiness check is the comparison it means, and what that comparison composes is nothing,
         * so nothing above this had a shape to recognise differently for it.
         */
        @Override
        public List<NumericConstraint> whole(ClauseExpr.Part part, Denotations at) {
            boolean positive = part.positive();
            List<NumericConstraint> out = new ArrayList<>();
            for (StatedComparison stated
                    : comparisonsStatedBy(terms, asSizeComparison(part.of()), at)
                            .inReadingOrder()) {
                LinearForm<FactSubject> left = terms.affineOf(stated.left(), at);
                LinearForm<FactSubject> right = terms.affineOf(stated.right(), at);
                if (left != null && right != null) {
                    out.add(new NumericConstraint(left.minus(right),
                            stated.relationUnder(positive)));
                }
            }
            return out;
        }

        /**
         * A conjunction states both of what it composes, and a choice states neither.
         *
         * <p>One of a choice's parts holds and this cannot say which, so what is left of it is that
         * the author named the two — which is nothing this reading has a constraint for. Descending
         * would state each part of a choice as though the values had to satisfy it.
         */
        @Override
        public Descent<List<NumericConstraint>> at(ClauseExpr.Joined join) {
            return switch (join.how()) {
                case BOTH -> new Descent.Into<>(Stating::and);
                case EITHER -> new Descent.Whole<>();
            };
        }

        private static List<NumericConstraint> and(List<NumericConstraint> one,
                                                   List<NumericConstraint> other) {
            List<NumericConstraint> both = new ArrayList<>(one);
            both.addAll(other);
            return both;
        }
    }

    /**
     * The comparisons {@code cond} states, in their reading order.
     *
     * <p>The one entry point a reader of a flat condition takes. A condition that is not a
     * comparison states none, and a comparison states itself and, where it compares what an
     * operation answering an order answered, the comparison of the two values that order is of
     * ({@link #orderStatedBy}). Composition repeats while it says something, so a comparison of a
     * sign of a sign comes to the values underneath it; the deepest reading is first and the
     * comparison as written is last. It stops on its own: each composition takes the arguments of a
     * call one of the sides was, so the sides get smaller every time round.
     *
     * <p>Handed over as statements and not as expressions. What a reading arrived at is a claim and
     * two sides ({@link StatedComparison}), which is what every reader below does something with —
     * written back into the tree as an operator, it would be a comparison the source never wrote
     * that each of them had to recognise again before it could read what this already knew.
     */
    static ComparisonReadings comparisonsStatedBy(Terms terms, Core cond, Denotations at) {
        if (!(cond instanceof Core.Binary b)) {
            return ComparisonReadings.none();
        }
        ComparisonClaim placed = Comparison.of(b).map(Comparison::claim).orElse(null);
        if (placed == null) {
            return ComparisonReadings.none();
        }
        List<StatedComparison> readings = new ArrayList<>();
        readings.add(new StatedComparison(placed, b.left(), b.right()));
        for (StatedComparison composed = orderStatedBy(terms, readings.getLast(), at);
                composed != null;
                composed = orderStatedBy(terms, readings.getLast(), at)) {
            readings.add(composed);
        }
        Collections.reverse(readings);
        return new ComparisonReadings(readings);
    }

    /**
     * The comparison of the two values an order is of, where {@code stated} compares what an
     * operation answering that order answered — or null where it states nothing about them.
     *
     * <p>What such an operation answers is a sign ({@link DischargeRules#orderStatedBy}), so a
     * condition that settles which side of nought the answer falls on is a condition about the two
     * arguments. One account: the relation is the one the sign is left standing to nought, taken
     * between the argument a positive answer says is the greater and the other one, so the six
     * relations and the two sides of the comparison are all read from the row the library's
     * operation has rather than from a case for each.
     *
     * <p><b>Which side of nought is worked out and not matched.</b> This read a written zero, on the
     * reasoning that a sign compared with anything else bounds how far the answer is from nought
     * rather than saying which way it falls. That stopped being true the day the comparisons were
     * declared to answer one of three numbers: {@code Int.compare(a, b) >= 1} leaves the answer at
     * one, and {@code > -1} leaves it at nought or one, and both settle a side. Read by the shape of
     * what was written, the two canonical facts about one operation — the order it states and where
     * its answer runs — could not be put together, which is the division this whole change is about
     * (#1016). So the condition and the declared bounds are composed, and what the composition
     * leaves is asked which way it stands.
     *
     * <p>And only where both values are ones this check can name. The sign is a number the domain
     * carries whatever it is the order of, so a comparison of two values it cannot name is less than
     * what it already had: a clause reading {@code daysBetween(acquiredOn, lostOn) >= 0} is a bound
     * on that count, and rewriting it into a comparison of two dates the check cannot relate would
     * leave the clause unreadable — a construction dropped from the check where it had been reported.
     * Reading a predicate never takes a reading away.
     */
    private static StatedComparison orderStatedBy(Terms terms, StatedComparison stated,
                                                  Denotations at) {
        boolean callFirst = stated.left() instanceof Core.PreservedCall;
        Core side = callFirst ? stated.left() : stated.right();
        Core against = callFirst ? stated.right() : stated.left();
        if (!(side instanceof Core.PreservedCall call) || call.args().size() != 2) {
            return null;
        }
        BoundOperationFact.StatesTheOrderOfItsArguments positive =
                DischargeRules.orderStatedBy(call.operation());
        if (positive == null
                || terms.bodyKey(call.args().get(0), at) == null
                || terms.bodyKey(call.args().get(1), at) == null) {
            return null;
        }
        // The relation the source wrote, read from the sign's side of the comparison: `call rel x`
        // however the two were written round.
        Rel written = (callFirst ? stated.claim() : stated.claim().turned()).statedRelation();
        Rel stands = standsToNought(terms, call, written, against, at);
        return stands == null ? null
                : new StatedComparison(ComparisonClaim.stating(stands),
                        CallArguments.of(positive.greater(), call),
                        CallArguments.of(positive.lesser(), call));
    }

    /**
     * Which way {@code call}'s answer stands to nought, given that a condition puts it {@code rel}
     * what {@code against} reads as — or null where that leaves both sides open.
     *
     * <p>Composed in the numeric domain rather than decided here, so that what a step is worth is
     * the step the domain knows about: over whole numbers {@code > -1} is {@code >= 0}, and this
     * would either have to say so a second time or answer as though a sign could fall between the
     * two. The rules taken in are the two there are — what the operation declares of its answer
     * ({@code semantics}) and what the condition says — and the tightest relation to nought the two
     * prove together is the answer.
     *
     * <p>The number the condition stands against is read and not matched, for the reason every
     * argument a fact names is: a name given a constant is that constant, so {@code compare(a, b) >
     * zero} under {@code let zero = 0} is the condition it looks like.
     *
     * <p>Nothing where the two leave both sides open, and nothing where they leave nothing at all: a
     * condition no answer satisfies states no order, and that the arm is never entered is said by
     * what reads reachability rather than by an order nobody can stand on.
     */
    private static Rel standsToNought(Terms terms, Core.PreservedCall call, Rel rel, Core against,
                                      Denotations at) {
        LinearForm<FactSubject> read = terms.affineOf(against, at);
        if (rel == null || read == null || !read.coefs().isEmpty()) {
            return null;
        }
        // One atom, standing for the number this call answered. Nothing else is in this domain: what
        // is asked is what the operation and the condition prove between them, and a rule about
        // anything else would be a rule about a value that is not the sign.
        Object sign = new Object();
        java.util.Map<Object, Granularity> spacing =
                java.util.Map.of(sign, terms.granularityOf(call.type()));
        LinearForm<Object> answered = LinearForm.atom(sign);
        NumericDomain<Object> known = NumericDomain.<Object>top()
                .assuming(sign, ResultRange.of(DefaultBoundOperationFacts.get()
                        .boundsOnTheResult(call.operation()), ConstantArguments.none()), spacing)
                .assume(answered.minus(LinearForm.constant(read.constant())), rel, spacing);
        if (known.isBottom()) {
            return null;
        }
        // Tightest first: a sign held at nought is an equality and not two half-statements, and one
        // held above it says more than one held at or above it.
        for (Rel each : List.of(Rel.EQ, Rel.GT, Rel.LT, Rel.GE, Rel.LE, Rel.NE)) {
            if (known.entails(answered, each)) {
                return each;
            }
        }
        return null;
    }

    /**
     * An emptiness check as the comparison it means, or {@code e} unchanged.
     *
     * <p>The arguments move across as they stand. Nothing is checked of them here: what the call
     * takes is what its declaration takes, and that the size takes the same is what the two
     * declarations were held to each other for ({@link DischargeRules#sizeMeantBy}). A reader that
     * counted them would be asking a third time.
     */
    static Core asSizeComparison(Core e) {
        if (e instanceof Core.PreservedCall call
                && DischargeRules.sizeMeantBy(call.operation())
                        instanceof BoundOperationFact.MeansTheSameAsASizeOfNought means) {
            // No source wrote this call. It is the size the written one means, composed so that the
            // rule can be read as the comparison it states — and giving it the written call's own
            // identity would put two applications under one. So it is a name and an application of
            // this pass's, each derived from the one the comparison it is read off reached.
            ApplicationOrigin application = ApplicationOrigin.composedOutOf(call.application(), 0,
                    ApplicationDerivationCause.SizeMeaningOfApplication::new);
            Core size = new Core.PreservedCall(means.size(), call.args(),
                    ReferenceOrigin.composedOutOf(call.reference(), 0,
                            ReferenceDerivationCause.SizeMeaningOfReference::new),
                    application, Type.INT, call.pos());
            return new Core.Binary(BinOp.EQ, size, new Core.Int(0, Type.INT, call.pos()),
                    ConstructOccurrence.unwritten(), Type.BOOL, call.pos());
        }
        return e;
    }

    record Polar(Core expr, boolean positive) {

        /** A condition that states no comparison, which is stated as itself. There is nothing to
         *  bring to a canonical form: what a guard settles such a condition by is the condition, and
         *  the six ways of writing one thing that {@link #polar} exists for are ways of writing a
         *  comparison. */
        static Polar itself(Core e, boolean positive) {
            return new Polar(e, positive);
        }
    }

    /**
     * {@code stated}, asserted with polarity {@code positive}, as the comparison of {@code ==} or
     * {@code <} that says the same thing: {@code a /= b} is {@code a == b} denied, {@code a >= b} is
     * {@code a < b} denied, and {@code a > b} is {@code b < a}. A fact is settled by key equality, so
     * without this the six ways to compare two terms are six facts, and a guard written one way would
     * leave a clause written the other unsettled.
     */
    static Polar polar(StatedComparison stated, boolean positive) {
        Polar written =
                stated.claim().canonical(stated.left(), stated.right()).expressedAs(AS_POLAR);
        return positive ? written : AS_POLAR.denied(written);
    }

    /** One of them, because it holds nothing: what a canonical comparison is written as is the same
     *  answer wherever it is asked. */
    private static final AsPolar AS_POLAR = new AsPolar();

    /**
     * A canonical comparison, written as a node with what is asserted of it held beside it.
     *
     * <p>The node is this reader's own spelling and stands nowhere. What a comparison is filed under
     * is what it places and the terms of its two sides ({@link Terms}), so where it came from, what
     * it answers and where it stands decide nothing here — which is why they are written inert
     * rather than taken from a comparison the source wrote. A statement is a claim and two sides,
     * and there is no occurrence in it to inherit.
     */
    private record AsPolar() implements CanonicalComparison.Expression<Core, Polar> {

        @Override
        public Polar theSameValue(Core left, Core right) {
            return new Polar(canonical(BinOp.EQ, left, right), true);
        }

        @Override
        public Polar below(Core left, Core right) {
            return new Polar(canonical(BinOp.LT, left, right), true);
        }

        /** Written over nothing the source holds. The three the node needs besides its operator
         *  and sides decide nothing any reader of this asks, so they are filled with what says so:
         *  unwritten, so no coverage site is named by it, the type a comparison answers, and a
         *  position taken from a side because the constructor takes one. */
        private static Core.Binary canonical(BinOp op, Core left, Core right) {
            return new Core.Binary(op, left, right, ConstructOccurrence.unwritten(), Type.BOOL,
                    left.pos());
        }

        /** Carried beside the node, which is what a polarity is for: a denial written as a node
         *  would be a second shape for the readings below to take apart before they could read the
         *  comparison under it. */
        @Override
        public Polar denied(Polar statement) {
            return new Polar(statement.expr(), !statement.positive());
        }
    }

}
