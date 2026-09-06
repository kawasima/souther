package souther.compiler.values;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a reading of one declaration asks about its string machines, answered from what somebody
 * has already made where there is such a thing, and worked out where there is not.
 *
 * <p>Held by the reading and by nothing that outlives it. It is not a value: what it answers from
 * is a value ({@link StringFacts}), but it also builds, and one that has answered a reading is a
 * different object at the end from the one it was at the start. So it is made where a reading is
 * made, handed along with it, and never kept in an answer — what leaves is {@link #facts()}.
 *
 * <p>One of these keeps what it made, whether or not it borrowed. What a reading came to is what it
 * was handed and what it built on top, and the reading is the only place both are: asked of the
 * lender afterwards, what comes back is what there was to borrow before the reading built anything.
 * {@link #NONE} is the exception and is not a reading's — it is what a question asked outside one
 * has to answer from.
 *
 * <p>What is borrowed is not spent. A machine answered from the facts was made under an allowance
 * of the reading that made it, so a reading whose allowance would have refused it is handed the
 * machine all the same — which is what {@link Allowance#besides} already does between two questions
 * of one position, carried across readings.
 */
public final class StringMachineAnswers {

    /**
     * Answering from nothing and keeping nothing: every machine worked out on the spot.
     *
     * <p>For a question asked outside a reading — whether a state is bottom, whether a set meets a
     * range — where there is nothing to borrow from and nothing that will be read again. Shared,
     * which is why this one keeps nothing: a reading's answers are its own.
     */
    public static final StringMachineAnswers NONE = new StringMachineAnswers(StringFacts.NONE, false);

    private final StringFacts borrowed;
    /** Whether what is made here is kept, which is what a reading's own answers do and what the
     *  shared {@link #NONE} must not. */
    private final boolean keeps;
    private final Map<AdmittedPlan, ValueSet> realized = new LinkedHashMap<>();
    private final Map<ValueSet, TextExtent> extents = new LinkedHashMap<>();
    private final Map<StringFacts.Stretch, Emptiness> inside = new LinkedHashMap<>();

    private StringMachineAnswers(StringFacts borrowed, boolean keeps) {
        this.borrowed = borrowed;
        this.keeps = keeps;
    }

    /**
     * A reading's own answers, made from {@code facts} and keeping what it works out beside them.
     *
     * <p>{@link StringFacts#NONE} for a reading with nothing to borrow, which is a reading that
     * builds all of them and holds all of them — the canonical reading of a declaration, whose
     * machines become the facts the store keeps, is one of those.
     */
    public static StringMachineAnswers borrowing(StringFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("a reading borrows from some facts, or from none");
        }
        return new StringMachineAnswers(facts, true);
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
        if (!(made instanceof TextExtent.NotBuilt)) {
            MADE.incrementAndGet();
            if (keeps) {
                extents.put(set, made);
            }
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
        if (made != Emptiness.UNDECIDED) {
            MADE.incrementAndGet();
            if (keeps) {
                inside.put(stretch, made);
            }
        }
        return made;
    }

    /** The same, as the lender an allowance takes; the block is not part of the question, and
     *  what the allowance builds is kept here. */
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
                if (machine && made instanceof Realization.Exact it) {
                    MADE.incrementAndGet();
                    if (keeps) {
                        realized.put(plan, it.set());
                    }
                }
            }
        };
    }

    /**
     * How many machines have been made rather than answered from the facts, for a test holding a
     * reading to what it borrows.
     *
     * <p>All three of the questions this answers, counted where the answer was not there to be had
     * and what was built came out: a plan realized into a set, the extent of a set, and whether a
     * language has a string inside a stretch. Counted whatever this does with it afterwards: what
     * is at stake is whether the machine had to be built, and not whose maps it ends up in.
     *
     * <p>What a caller is held to is that a second reading of a declaration asks the same string
     * questions and is answered from what the first came to — a shape, and not a speed.
     */
    public static long machinesMade() {
        return MADE.get();
    }

    private static final AtomicLong MADE = new AtomicLong();

    /**
     * Everything this answered from and everything it made: what the reading it belongs to came to.
     *
     * <p>What a store keeps under the declaration, and what a second reading of the same
     * declaration is handed — including a counterfactual of the reading this answered, which meets
     * the same string rules wherever what it leaves out is about something else.
     */
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
