package souther.compiler.partition;

import souther.compiler.inputs.Refinement;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.List;

/**
 * One equivalence class of an input position: a set of values expected to behave the same way.
 *
 * <p>Three things are kept apart that a single "class" would run together — what the class means,
 * how a value is told to be in it, and whether one can be produced. A class whose representative
 * cannot be built is still a class: rows that reach it still count, and only the generator has to say
 * it cannot fill the gap.
 *
 * @param id              a name stable within its axis, used to compare one run against another
 * @param label           what to call it in a report
 * @param recognises      what the class means, which is what says whether a value the rows already
 *                        carry is in it. A value rather than a way of asking, so that a class can be
 *                        compared with another and kept in an answer
 * @param representatives how a value standing for it is arrived at
 * @param denotes         the values this class holds, where they can be written out, and null
 *                        where they cannot. A fourth thing, and separate from {@link #recognises}
 *                        for the reason the others are separate: a classifier answers about one
 *                        value, and a reader asking whether a rule refuses the whole class is
 *                        asking about all of them. A case holding a record has no end of values
 *                        and says nothing here; a case of an enumeration is one value and says so.
 *
 *                        <p>Null is "not written out here" and never "holds nothing" — a class
 *                        that holds nothing is not a class of the position, and what decides that
 *                        is the reading this is put to
 * @param selects         the narrowing a row sits in this class thereby meets, and null where
 *                        sitting in it narrows nothing. What tells a case of a sum from a value of
 *                        a range: a row in the {@code GlobalQuery} class of a position <em>is</em> a
 *                        {@code GlobalQuery} there, which is what the positions under that case
 *                        require of it.
 *
 *                        <p>Kept beside {@link #id} rather than recovered from it. The id is a name
 *                        for a report and is stable within its axis; which narrowing the class is
 *                        is what decides whether two classes can be in one row, and reading that
 *                        off a name is the same position answering differently depending on how it
 *                        was spelled
 */
public record PartitionClass(String id, String label, Recognition recognises,
                             RepresentativeSource representatives, ValueSet denotes,
                             Refinement selects,
                             souther.compiler.inputs.NumericTerm.FromOnePosition of) {

    public PartitionClass {
        // The meaning and the number it is said to be of are in one vocabulary. A meaning about a
        // count is a class of that count and of no other; a meaning about the value at the position
        // is a class of the position's own value and of nothing taken of it. Let them differ and the
        // class answers membership on one order while being owed on another — the two readers an
        // axis exists to keep together.
        if (of != null && !recognises.canBeAClassOf(of)) {
            throw new IllegalArgumentException("`" + id + "` means " + recognises
                    + " and was said to be a class of " + of);
        }
    }

    public static PartitionClass of(String id, String label, Recognition recognises,
                                    RepresentativeSource representatives) {
        return new PartitionClass(id, label, recognises, representatives, null, null, null);
    }

    /** A class nothing can produce a value for, and why. */
    public static PartitionClass ungeneratable(String id, String label,
                                               Recognition recognises, String why) {
        return new PartitionClass(id, label, recognises,
                new RepresentativeSource.Ungeneratable(why), null, null, null);
    }

    /**
     * The same class, said to be a class of {@code number}.
     *
     * <p>Which number's values a class divides is not the class's meaning and is not read off it: a
     * {@code true} means what it means wherever it stands, and whether it is one of the classes a
     * position's own value is divided into is the producer's to say. Written by whoever builds the
     * classes of a measure, and required by the measure ({@link Axis}) — so a class of one number
     * cannot be put among another's, and a class built for no measure at all cannot be put on one.
     *
     * <p>Said once. A class already of one number is not made a class of another by saying so —
     * that would be a label whoever wrote last owns, and an axis could trust it no further than the
     * last writer. Saying the same number again is nothing said.
     */
    public PartitionClass ofTheNumber(souther.compiler.inputs.NumericTerm.FromOnePosition number) {
        if (number == null) {
            throw new IllegalArgumentException("`" + id + "` said to be a class of no number");
        }
        if (of != null && !of.equals(number)) {
            throw new IllegalArgumentException("`" + id + "` is a class of " + of
                    + " and cannot be made a class of " + number);
        }
        return new PartitionClass(id, label, recognises, representatives, denotes, selects, number);
    }

    /**
     * The same class, asked of the value inside the names the position writes it under.
     *
     * <p>What the class means is about the values the position holds, and a row writes one of them
     * under whatever names the position wears — so the two are one class and the names are how a
     * row spells it. Said by whoever assembles the measure, which is where the position's type is
     * read; a class made from the rules alone has no way of knowing what wears it.
     *
     * <p>Nothing said where the position wears nothing ({@link Recognition.Under#of}), so a class
     * of a bare position stays the class it was rather than becoming a second spelling of it.
     */
    public PartitionClass under(List<TypeSymbol> worn) {
        return new PartitionClass(id, label, Recognition.Under.of(worn, recognises),
                representatives, denotes, selects, of);
    }

    /**
     * Whether this class holds the number at {@code place}, which is on the order of the number the
     * axis this is a class of measures.
     *
     * <p>Beside {@link #classifier()} and not through it. That one is handed a row's value and reads
     * the class's number out of it; this one is handed the number, which is what a line is. Answered
     * by turning the place back into a value, a class about a minute of a time would be handed the
     * minute where it expects a time, and no class would hold the line at all.
     */
    public boolean holdsTheNumberAt(souther.compiler.numeric.Place place) {
        return Recognitions.holdsTheNumberAt(recognises, place);
    }

    /**
     * This class as something to ask of a row.
     *
     * <p>Derived and not held. What the class means is {@link #recognises}, and a way of asking it
     * is made from that wherever one is wanted — kept beside the meaning, a class would compare by
     * which reader had built its reading, and could not be part of an answer at all.
     */
    public Classifier classifier() {
        return Recognitions.reading(recognises);
    }

    /**
     * The same class, saying which values it holds.
     *
     * <p>Written by the producer that knows, since knowing is what it takes to say it: a case of an
     * enumeration is the one value the case is, and a class over a range is not something this can
     * write out. A producer that says nothing leaves a class no rule can be proved to refuse
     * whole, which is the safe direction — the class stays.
     */
    public PartitionClass holding(ValueSet values) {
        return new PartitionClass(id, label, recognises, representatives, values, selects, of);
    }

    /**
     * The same class, saying which narrowing a row in it meets.
     *
     * <p>Written by the producer that knows, as {@link #holding} is. A class that narrows nothing
     * says nothing here, and a reader asking what a row in it has to be is answered with the
     * requirements of its position and no more.
     */
    public PartitionClass selecting(Refinement refinement) {
        return new PartitionClass(id, label, recognises, representatives, denotes, refinement, of);
    }

    /**
     * Whether {@code admitted} leaves this class a value, where that can be settled.
     *
     * <p>One question and two proofs, which is why it is asked here rather than at each shape a set
     * comes in. {@code admitted} is an upper bound on what the position holds, so a class holding
     * none of a finite set holds nothing at all; and a set written as a denial proves a class empty
     * only by excluding every value the class has, which takes the class knowing what those are.
     * Where neither proof is available the class stays: nothing has shown the position cannot reach
     * it, and a class taken away on less than that is a distinction the model states going missing.
     */
    public boolean leftAnythingBy(ValueSet admitted, java.util.function.Predicate<Value> holds) {
        if (denotes != null) {
            return denotes.sharesAnythingWith(admitted);
        }
        return !(admitted instanceof ValueSet.Finite finite)
                || finite.values().stream().anyMatch(holds);
    }

    public boolean generatable() {
        return representatives.buildable();
    }
}
