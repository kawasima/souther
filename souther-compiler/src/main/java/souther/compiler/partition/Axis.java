package souther.compiler.partition;

import souther.compiler.check.NumberAt;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;

import java.util.List;

/**
 * One measure a model makes of a number of its input: the classes it divides that number into, and
 * the lines it draws on it.
 *
 * <p>The model's own distinctions, not invented ones. A type with two cases has two classes; a
 * {@code guard}'s comparison divides what a position holds into the two sides it treats differently.
 * A position the model says nothing about — a plain {@code String}, an {@code Int} with no invariant
 * — is measured at nothing and has no measure at all, which the position says
 * ({@link PositionMeasurements}) and a report names as not derivable, rather than being filled in
 * with values nobody asked for. The choice matters: a made-up partition measures a rule the model
 * does not have, and reports coverage of it.
 *
 * <p>A bound is not one of them. An invariant's bound gives a boundary and no partition: everything
 * outside it is refused at construction, so there is no class on the far side to cover (ADR-0090),
 * and what such a position gets is {@link #cuts} and no classes — which is what
 * {@link #asksForARow} is for. What a bound does contribute to a partition is the range the classes are clipped to: the
 * two either side of a {@code guard} at 50 run from the bound and not from the type's own ends.
 *
 * <p>{@link #classes} is the one denominator. What a report counts, what a pair space is the
 * product of, and what the generator offers rows for all come from there — three derivations of the
 * same universe are three chances to disagree about it, which is how a class nothing can reach came
 * to be asked for by three measures at once. Nothing a body writes narrows it: a case an arm
 * declares cannot arrive is a claim about the same position, and what became of the claim is said
 * beside the report rather than taken out of the count — a claim the rules bear out has already
 * left, because the reading these classes come from is what took it out.
 *
 * <p>Where the number is measured, and nothing about what stands there. What a value at the
 * position looks like and what order its number is counted on are two questions the reading of the
 * input answers, and every reader of a measure can reach that reading. Held here as well, the two
 * would be answers a reader could take from either place, and the day they part is the day a row is
 * composed at a place one of them says nothing is written.
 *
 * @param term    the number this axis is of: a location's own content, or something taken of it.
 *                Answered by one input position and held as such, because that is what an axis is:
 *                a run of classes over the values of a number a row can be asked for somewhere. A
 *                number read from a run of a sequence has no such place, so it draws a line without
 *                dividing anything and never arrives here
 * @param classes exclusive and exhaustive over the term's values, or empty where the model does
 *                not divide them
 * @param cuts    the values the classes meet at, each carrying every rule that drew it there
 * @param divides which rules the classes were composed out of, each with which reading of it this
 *                is. The other half of what {@code cuts} is for a line: what a class is made of is
 *                not read off the class, because a set is what it holds and holds nothing about
 *                what put a value in it — so the rules that composed the classes are recorded
 *                beside them, and a stage claiming to have measured one can be held to it.
 *                Whatever kind of rule it was: a value singled out of a string composes into these
 *                classes as readily as a predicate over them does, and an axis that recorded only
 *                one kind would say it carried nothing of the other
 * @param parted  where the rules part this position's values, which is not the same list. A cut is
 *                a value a row can be written against and a bound has one without parting
 *                anything; a rule that wrote a multiple of the position parts its values where the
 *                position may hold none, and has no cut. What every border on this position owes
 *                away from its line is a run of what these leave together, and the cuts alone are
 *                short of the lines that have no value
 */
public record Axis(AxisId id, NumericTerm.FromOnePosition term,
                   List<PartitionClass> classes, List<RuleEvidenceOrigin> divides,
                   List<Cut> cuts, List<Parting> parted, NarrowedBounds narrowed) {

    public Axis {
        classes = List.copyOf(classes);
        divides = List.copyOf(divides);
        cuts = List.copyOf(cuts);
        parted = List.copyOf(parted);
        // A measure is what the rules divided a number into, cut on it, or parted it at, and one
        // with none of the three measured nothing. Such a one used to stand for a position still to
        // be answered for — which is a fact about the location and is held there
        // ({@link PositionMeasurements}), so a reader counting what a behavior is measured at was
        // counting locations among the measures.
        if (classes.isEmpty() && cuts.isEmpty() && parted.isEmpty()) {
            throw new IllegalArgumentException(
                    "`" + id + "` measures " + term + " at nothing: no class, no line and no"
                            + " parting, which is a position nothing measures and not a measure");
        }
        // The name says which number this is a measure of, so it is that number as a report writes
        // it and never a second answer to what is measured here. An identity handed in beside the
        // number is one a caller chooses, and what a reader keyed by it would then be holding is a
        // measure of one number filed under the name of another — which every map downstream is
        // keyed by ({@link EvidenceAccount}, the lines along a measure, what a row was placed at).
        if (!id.term().equals(term.toString())) {
            throw new IllegalArgumentException("`" + id + "` names " + id.term()
                    + " and this measures " + term + "; a measure is named after its own number");
        }
        for (PartitionClass one : classes) {
            subjectHeld(term, one);
            // A line is a place on the number's order and falls in whichever class holds that
            // place. A class that cannot be asked about a place holds none of them, and every line
            // would fall in no class — which reads as the rules dividing the position nowhere,
            // where what happened is that a class was built without the order it sits on.
            if (!cuts.isEmpty() && !one.recognises().answersAboutAPlace()) {
                throw new IllegalArgumentException("`" + one.id() + "` cannot be asked where on "
                        + term + " it lies, and this axis has lines on it");
            }
        }
    }

    /**
     * That {@code one} is a class of the number this axis measures, which is what an axis is a run
     * of classes over.
     *
     * <p>Held where an axis is built, so that a reader crossing the classes with anything else the
     * axis holds is crossing two answers about one number. A line is a place on the term's order and
     * a class of another number is answered by reading a value of that one, so a mixed axis leaves
     * the two with nothing to compare — and what comes of asking anyway is that the class is not
     * found, which reads as the rules dividing the position nowhere.
     *
     * <p>Which number a class is of is said where the class is built and is never worked out from
     * what it means. A {@code true} is a truth wherever it stands: whether it is one of the classes
     * this position's own value is divided into is what the reading that built it decided, and an
     * axis reading that off the class would be deciding it a second time — which is how a class of
     * one position's truth would pass as a class of another's.
     */
    private static void subjectHeld(NumericTerm.FromOnePosition term, PartitionClass one) {
        if (one.of() == null) {
            throw new IllegalArgumentException("`" + one.id() + "` is a class of no measure, and"
                    + " this axis measures " + term + "; a class is put on an axis by whatever"
                    + " built it for one");
        }
        if (!one.of().equals(term)) {
            throw new IllegalArgumentException("`" + one.id() + "` is a class of " + one.of()
                    + ", and this axis measures " + term);
        }
    }

    public Axis(AxisId id, NumericTerm.FromOnePosition term,
                List<PartitionClass> classes, List<Cut> cuts) {
        this(id, term, classes, List.of(), cuts, List.of(), NarrowedBounds.NOTHING);
    }

    /**
     * One measure of {@code term}, named after it.
     *
     * <p>What a caller has is a number and the behavior whose input it is read from, and the name
     * follows from the two. Handed the name as well, a caller has a second thing to get right and
     * the constructor a second answer to refuse — so this is the way in, and passing a name is for
     * a reader rebuilding a measure it already has.
     */
    public static Axis of(String behavior, NumericTerm.FromOnePosition term,
                          List<PartitionClass> classes, List<RuleEvidenceOrigin> divides,
                          List<Cut> cuts, List<Parting> parted, NarrowedBounds narrowed) {
        return new Axis(AxisId.of(behavior, term), term, classes, divides, cuts, parted, narrowed);
    }


    /** Where the value this axis is about sits, which is where a row is walked to before the term is
     * read off it. Not what the axis is: two terms can be taken of one location, and {@link #id()}
     * is the one that tells them apart. */
    public TermPath path() {
        return term.position();
    }

    /**
     * Which number this axis is a measure of, said the way a question names one.
     *
     * <p>The projection runs this way and only this way. A subject is a place and which of the
     * numbers there it is; a term is what this compiler managed to make of one, and it carries how
     * the number is measured, where it runs and how it is read off a row besides. So an answer can
     * say which question it is an answer to, and no question is built out of an answer — asked the
     * other way round, a question about a number no term could be made of would have had no axis to
     * compare against and no way to be recognised as this axis's.
     *
     * <p>Here rather than beside {@link NumberAt}, so that the question vocabulary names nothing a
     * reading produces. A converter written over there would be that dependency with the arrow
     * drawn the other way.
     */
    public NumberAt<TermPath> subject() {
        return switch (term) {
            case NumericTerm.ValueOf it -> NumberAt.valueOf(it.position());
            case NumericTerm.TakenOf it -> NumberAt.takenOf(it.position(), it.operation());
        };
    }

    /**
     * What a row has to be for this position to exist in it at all.
     *
     * <p>Read off the path and kept nowhere else. A position under a narrowing requires it by being
     * there — {@code query@GlobalQuery.tag} says that {@code query} is a {@code GlobalQuery} and
     * says it completely — so an account of it beside the path would be two readings of one fact.
     */
    public Requirements requirements() {
        return path().requirements();
    }

    /**
     * The same, for a row sitting in {@code cls} here.
     *
     * <p>Both halves and neither standing for the other. A class of a sum states a narrowing by
     * being the class it is, and the position it is a class of may itself be under one — so what a
     * row at this class has to be is what the position requires and what the class selects,
     * together.
     */
    public Requirements requiring(PartitionClass cls) {
        return requirements().and(path(), cls == null ? null : cls.selects());
    }

    /** Whether the model divides this position into classes to cover. */
    public boolean derivable() {
        return !classes.isEmpty();
    }

    /** Whether there is anything here a row can be written against — a class to sit in, or an edge
     * to stand at.
     *
     * <p>A numeric newtype bounded by an invariant has the second and not the first: everything
     * outside the bound is refused at construction, so there is no other class, only an edge worth
     * a row. False where the rules part the number and the position holds no value at the parting:
     * that is a measure of the number, and there is nothing at it to ask an author for. */
    public boolean asksForARow() {
        return !classes.isEmpty() || !cuts.isEmpty();
    }

    public PartitionClass classOf(String id) {
        return classes.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
}
