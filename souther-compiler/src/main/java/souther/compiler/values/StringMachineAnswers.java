package souther.compiler.values;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a reading of one declaration asks about its string machines, answered from what somebody
 * has already made where there is such a thing, and worked out where there is not.
 *
 * <p>Held by the reading and by nothing that outlives it. It is not a value: what it answers from
 * is a value ({@link StringFacts}), but it also builds, and a reading that records what it built
 * ({@link #recording}) is a different object at the end from the one it was at the start. So it is
 * made where a reading is made, handed along with it, and never kept in an answer.
 *
 * <p>What is borrowed is not spent. A machine answered from the facts was made under an allowance
 * of the reading that made it, so a reading whose allowance would have refused it is handed the
 * machine all the same — which is what {@link Allowance#besides} already does between two questions
 * of one position, carried across readings.
 */
public final class StringMachineAnswers {

    /** Answering from nothing and keeping nothing: every machine worked out on the spot. */
    public static final StringMachineAnswers NONE = new StringMachineAnswers(StringFacts.NONE, false);

    private final StringFacts borrowed;
    private final boolean recording;
    private final Map<AdmittedPlan, ValueSet> realized = new LinkedHashMap<>();
    private final Map<ValueSet, TextExtent> extents = new LinkedHashMap<>();
    private final Map<StringFacts.Stretch, Emptiness> inside = new LinkedHashMap<>();

    private StringMachineAnswers(StringFacts borrowed, boolean recording) {
        this.borrowed = borrowed;
        this.recording = recording;
    }

    /** Answering from {@code facts}, and working out the rest without keeping it. */
    public static StringMachineAnswers borrowing(StringFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("a reading borrows from some facts, or from none");
        }
        return new StringMachineAnswers(facts, false);
    }

    /** Answering from nothing and keeping everything it works out, for the reading whose machines
     *  become the facts the store keeps ({@link #facts}). */
    public static StringMachineAnswers recording() {
        return new StringMachineAnswers(StringFacts.NONE, true);
    }

    /** What {@code plan} admits where somebody has made it, or null; asking makes nothing and
     *  spends nothing of the asker's. */
    public ValueSet lent(AdmittedPlan plan) {
        return borrowed.realized().get(plan);
    }

    /** Where the strings {@code set} holds stop on the order. */
    public TextExtent extentOf(ValueSet set) {
        TextExtent known = borrowed.extents().get(set);
        if (known != null) {
            return known;
        }
        TextExtent made = TextExtents.of(set);
        if (recording && !(made instanceof TextExtent.NotBuilt)) {
            extents.put(set, made);
        }
        return made;
    }

    /**
     * Whether any string {@code language} admits lies inside {@code held}, whose ends are strings.
     *
     * <p>{@code meter} is what the asker may build where nothing was made before; an answer from
     * the facts spends nothing of it.
     */
    public Emptiness inside(Language language, OrderedInterval held, Meter meter) {
        StringFacts.Stretch stretch = new StringFacts.Stretch(language, held);
        Emptiness known = borrowed.inside().get(stretch);
        if (known != null) {
            return known;
        }
        Emptiness made = TextExtents.inside(language, held, meter);
        if (recording && made != Emptiness.UNDECIDED) {
            inside.put(stretch, made);
        }
        return made;
    }

    /** The same, as the lender an allowance takes; the block is not part of the question, and
     *  what the allowance builds is kept here where this is recording. */
    public <A> Allowance.Known<A> lending() {
        return new Allowance.Known<>() {
            @Override
            public ValueSet of(Sameness.Block<A> block, AdmittedPlan plan) {
                return lent(plan);
            }

            @Override
            public void made(Sameness.Block<A> block, AdmittedPlan plan, Realization made) {
                // Only what took a machine to make. Everything, nothing and a set the rules wrote
                // out are answered by the plan itself, and keeping those would file a row for
                // every literal a rule names.
                boolean machine = switch (plan) {
                    case AdmittedPlan.Everything _, AdmittedPlan.Nothing _, AdmittedPlan.Of _ ->
                            false;
                    case AdmittedPlan.Pattern _, AdmittedPlan.Both _, AdmittedPlan.Either _ ->
                            true;
                };
                if (recording && machine && made instanceof Realization.Exact it) {
                    realized.put(plan, it.set());
                }
            }
        };
    }

    /** Everything this answered from and everything it made, as the value a store keeps. */
    public StringFacts facts() {
        Map<AdmittedPlan, ValueSet> plans = new LinkedHashMap<>(borrowed.realized());
        plans.putAll(realized);
        Map<ValueSet, TextExtent> stops = new LinkedHashMap<>(borrowed.extents());
        stops.putAll(extents);
        Map<StringFacts.Stretch, Emptiness> stretches = new LinkedHashMap<>(borrowed.inside());
        stretches.putAll(inside);
        return new StringFacts(plans, stops, stretches);
    }
}
