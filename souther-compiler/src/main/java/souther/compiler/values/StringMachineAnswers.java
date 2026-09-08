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
     * range — where there is nothing to borrow from and nothing that will be asked again. Shared,
     * which is why this one keeps nothing.
     *
     * <p><b>Never handed to a reading.</b> A reading's answers are its own and keep what they make,
     * because what a reading came to is what its counterfactual is handed; handed this one, a
     * reading would come to nothing and the counterfactual would build all of it again. A reading
     * with nothing to borrow is {@link #unborrowed}, which is a different thing from this and
     * reads like it.
     */
    public static final StringMachineAnswers NONE =
            new StringMachineAnswers(StringFacts.NONE, false, KnownExtents.NONE);

    private final StringFacts borrowed;
    /** Whether what is made here is kept, which is what a reading's own answers do and what the
     *  shared {@link #NONE} must not. */
    private final boolean keeps;
    /** Where the sets this meets stopped when anything else in the revision met them, and where
     *  what this works out is said for the rest of it. */
    private final KnownExtents known;
    private final Map<AdmittedPlan, ValueSet> realized = new LinkedHashMap<>();
    private final Map<ValueSet, TextExtent> extents = new LinkedHashMap<>();
    private final Map<StringFacts.Stretch, Emptiness> inside = new LinkedHashMap<>();

    private StringMachineAnswers(StringFacts borrowed, boolean keeps, KnownExtents known) {
        this.borrowed = borrowed;
        this.keeps = keeps;
        this.known = known;
    }

    /**
     * A reading's own answers, made from {@code facts} and from what {@code known} says the
     * revision has already worked out, and keeping what it works out beside them.
     *
     * <p>{@link #unborrowed} where there is nothing to borrow, which is the same thing over no
     * facts at all.
     */
    public static StringMachineAnswers borrowing(StringFacts facts, KnownExtents known) {
        if (facts == null) {
            throw new IllegalArgumentException("a reading borrows from some facts, or from none");
        }
        return new StringMachineAnswers(facts, true, known);
    }

    /**
     * A reading's own answers with nothing to borrow: it works out every machine it meets that
     * {@code known} has no answer for, and holds every one it worked out.
     *
     * <p>Which is what a reading with no lender is, and what a reading of a declaration the lender
     * has nothing for is. Not {@link #NONE}: having nothing to borrow and keeping nothing are two
     * things, and they parted when what a reading came to became what its counterfactual is handed.
     *
     * <p>What the revision knows is beside all of that. It is not this reading's and is not lent
     * to it — an extent is a fact about a set, so a reading that takes one from there came to what
     * it would have come to on its own.
     */
    public static StringMachineAnswers unborrowed(KnownExtents known) {
        return new StringMachineAnswers(StringFacts.NONE, true, known);
    }

    /** What {@code plan} admits where somebody has made it, or null; asking makes nothing and
     *  spends nothing of the asker's. */
    public ValueSet lent(AdmittedPlan plan) {
        return borrowed.realized().get(plan);
    }

    /**
     * Where the strings {@code set} holds stop on the order.
     *
     * <p>What the declaration's facts say, then what the revision has worked out, and only then the
     * walk. The second is not the first: what a declaration came to is its answer and is compared
     * as one, while what the revision knows is work anybody's reading did — so an extent taken from
     * there is put where an extent this reading worked out would go, and the declaration comes to
     * the same facts either way.
     */
    public TextExtent extentOf(ValueSet set) {
        TextExtent lent = borrowed.extents().get(set);
        if (lent != null) {
            return lent;
        }
        TextExtent had = known.of(set);
        if (had != null) {
            keep(set, had);
            return had;
        }
        WALKED.incrementAndGet();
        TextExtent made = TextExtents.of(set);
        // Whatever it came to, including a walk that ran past what it may spend: an extent is
        // settled by its set, and the allowance it ran past is the one every set is walked under.
        known.remember(set, made);
        if (!(made instanceof TextExtent.NotBuilt)) {
            MADE.incrementAndGet();
        }
        keep(set, made);
        return made;
    }

    /**
     * Keeps what a set came to, where this reading keeps anything.
     *
     * <p>Whether the walk was here or anywhere else in the revision, since what the declaration
     * came to is the same answer either way. A walk nobody could afford is not among them: it says
     * what this compiler could do rather than what the declaration's rules leave, and a reading
     * that kept one would be handing that on as something it came to.
     */
    private void keep(ValueSet set, TextExtent extent) {
        if (keeps && !(extent instanceof TextExtent.NotBuilt)) {
            extents.put(set, extent);
        }
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
        if (made.isDecided()) {
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
     * How many machines have been made rather than answered from the facts or from what the
     * revision knows, for a test holding a reading to what it borrows.
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
     * How many times a set's extent has been walked, for a test holding a revision to working one
     * out once.
     *
     * <p>Every walk and only a walk: a set answered from the facts or from what the revision knows
     * is not one, and a walk that ran past its allowance is, since that is the walk this is about.
     * Which is what makes it a different count from {@link #machinesMade}, where what is at stake
     * is what a reading came to hold rather than what any of it cost.
     */
    public static long extentsWalked() {
        return WALKED.get();
    }

    private static final AtomicLong WALKED = new AtomicLong();

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
