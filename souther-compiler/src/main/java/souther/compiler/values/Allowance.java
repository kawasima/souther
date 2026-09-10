package souther.compiler.values;

import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each block of one answer is allowed to build, and everything that builds under it.
 *
 * <p>Held per block, because a block is what an answer is about: every rule reaching one pays
 * into the machine it finally admits, whether the rules are two halves of a clause, two clauses of
 * one declaration, or two declarations met by whoever needed both. One allowance for the whole
 * reading instead would let a complicated rule at one block spend what a plain one at another was
 * going to need, and which of them went unanswered would turn on the order they were written in.
 *
 * <p><b>A block and not a position, because a machine is made once for one answer.</b> Positions a
 * rule holds as one value have one set between them ({@link Sameness}), so they ask for one machine
 * and it is bought from one purse. Charged to a member instead, which of them paid would be a
 * choice this makes, and the same rules written with the equality the other way round would spend a
 * different number. A position no equality reached is a block of one, which is what every purse
 * was before any of this.
 *
 * <p><b>One answer, one allowance.</b> Turning a rule into the set it names and putting a position's
 * sets together are the same spending, and both are part of the one answer being built. An answer
 * given a fresh allowance for each of those may spend its whole budget several times over, and the
 * bound would be on a phase rather than on the answer.
 *
 * <p><b>And putting two answers together is a third answer.</b> A reading of one declaration is
 * bounded by the purse it was read under; two of them met over one vocabulary come to a set neither
 * of them holds, and that set belongs to whoever asked for it. So it is paid for out of that
 * caller's own allowance, and the readings' purses are where they were — a composition charged to
 * an operand's purse would spend what a declaration was allowed on an answer about somebody else's
 * question.
 *
 * <p><b>Which is why this is held by whoever is building an answer and never by a value that
 * answers.</b> A value carrying one is a value carrying a fact about no reading in it, and a
 * composition of two such values has two purses to pick from — so which of them pays, and with it
 * how exactly the composition comes out, would be settled by which side the call was written on.
 * So an operation that may build is told which allowance it spends, and one that builds nothing
 * takes none. Who holds one is written down and counted off the compiled classes
 * ({@code AnAllowanceIsHeldByWhoeverIsBuildingAnAnswerTest}), rather than listed here, where a
 * holder added later would be one this sentence had not heard of.
 *
 * <p><b>And nothing composes outside it.</b> Everything that may build is asked for through
 * {@link AdmittedPlan} and worked out by a {@link Realizer}, which is what makes the order the work
 * is done in a function of what is being asked rather than of who asked first. Two sets put together
 * by whoever held them would be a fold in arrival order again, one layer further out.
 *
 * @param <A> what a position is called
 */
public final class Allowance<A> {

    private final PatternPlan.Budget budget;
    private final Map<Sameness.Block<A>, Meter> meters = new LinkedHashMap<>();
    private final Map<Sameness.Block<A>, Realizer> realizers = new LinkedHashMap<>();
    /** The blocks whose exact answer this stopped building, in the order they were found. */
    private final Set<Sameness.Block<A>> spent = new LinkedHashSet<>();
    /** What another question of the same blocks built, which this one may use and does not pay
     *  for — see {@link #besides}. */
    private final Known<A> borrowed;
    private Realizer nowhere;
    /** What {@link #elsewhere} draws on, kept so that what this has spent in all can be read. */
    private Meter nowhereMeter;
    private boolean spentElsewhere;

    private Allowance(PatternPlan.Budget budget, Known<A> borrowed) {
        this.budget = budget;
        this.borrowed = borrowed;
    }

    /** A fresh allowance for every position of one answer. */
    public static <A> Allowance<A> of(PatternPlan.Budget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("an allowance allows something");
        }
        return new Allowance<>(budget, Known.nothing());
    }

    /**
     * The same, borrowing what {@code lent} has already made of a plan.
     *
     * <p>As {@link #besides}, with the lender named rather than being another allowance: what it
     * hands over was made somewhere else under somebody else's allowance, is read wherever a plan
     * of it comes up here, and costs this one nothing.
     */
    public static <A> Allowance<A> of(PatternPlan.Budget budget, Known<A> lent) {
        if (budget == null || lent == null) {
            throw new IllegalArgumentException("an allowance allows something, and names what it"
                    + " may borrow");
        }
        return new Allowance<>(budget, lent);
    }

    /**
     * What {@code plan} admits, worked out on its own under this allowance.
     *
     * <p>For an answer about the plan and not about any position: what is built is charged to the
     * part of this allowance that belongs to no block ({@link #elsewhere}), and what a part of the
     * plan comes to is borrowed where somebody has made it already.
     */
    public Realization realized(AdmittedPlan plan) {
        return elsewhere().of(plan);
    }

    /**
     * The same, beside another question of the same positions whose machines this one may use.
     *
     * <p>A machine exists or it does not, and one that does is not made twice: what {@code answers}
     * built is read wherever a plan of it comes up here, including inside a plan this is building
     * out of others. Charged again, a question that needs one part more than its neighbour would
     * pay for the neighbour as well, and an allowance meant for the difference would be spent on
     * what was already bought.
     *
     * <p>Reading it spends nothing of {@code answers} ({@link #known}), and it is a reading and not
     * a share: what this builds is this one's, and the other question's allowance is where it was.
     */
    public static <A> Allowance<A> besides(PatternPlan.Budget budget, Allowance<A> answers) {
        if (budget == null || answers == null) {
            throw new IllegalArgumentException("an allowance allows something, beside something");
        }
        return new Allowance<>(budget, answers::known);
    }

    /**
     * What some other question of this position has already built, for a caller that may use it
     * and must not pay for it twice.
     *
     * <p>Answering nothing is what an allowance that built nothing says, and it is not an answer
     * about the values: {@link #known} is the reading of it, and null there is "nothing established
     * this" rather than "this admits everything".
     *
     * @param <A> what a position is called
     */
    @FunctionalInterface
    public interface Known<A> {

        /** What {@code plan} was worked out to admit at {@code block}, or null where nothing
         *  was. */
        ValueSet of(Sameness.Block<A> block, AdmittedPlan plan);

        /** Told what the allowance built of {@code plan} at {@code block} where this had nothing
         *  to lend, for a lender that keeps what is made; one that does not keeps nothing. */
        default void made(Sameness.Block<A> block, AdmittedPlan plan, Realization made) {
        }

        /** Nothing has been built anywhere, which is what one allowance on its own knows. */
        static <A> Known<A> nothing() {
            return (_, _) -> null;
        }
    }

    /**
     * A set, and whether it is the exact one.
     *
     * <p>One value because the two are one fact. {@code gaveUp} does not describe the set — every
     * set here is an upper bound of what the rules leave, and this one is wider than it had to be.
     *
     * @param set what the two sides come to, or every value where the exact answer was not built
     * @param gaveUp whether building the exact answer was given up on for want of allowance
     */
    public record Composed(ValueSet set, boolean gaveUp) {

        public Composed {
            if (set == null) {
                throw new IllegalArgumentException("a composition comes to some set");
            }
        }
    }

    /**
     * One of the two ways two sets are put together, as something to hand to a caller that does not
     * care which.
     *
     * <p>The block is part of it, because the allowance is per block. A composer handed without
     * one would be a composition nobody could charge.
     */
    @FunctionalInterface
    public interface Composing<A> {

        /** What the two come to at {@code block}, and whether it is the exact answer. */
        Composed of(Sameness.Block<A> block, ValueSet one, ValueSet other);
    }

    /** The values both admit — what two rules stated together leave a block. */
    public Composed meet(Sameness.Block<A> block, ValueSet one, ValueSet other) {
        return put(block, AdmittedPlan.meeting(both(one, other)));
    }

    /**
     * The same, where what the second rule leaves is said as a plan rather than as a set.
     *
     * <p>For a caller that knows which values a rule leaves without having built them. A rule about
     * how many a value holds leaves a set of strings nobody has a machine for yet, and building one
     * to hand in here would be the composing this exists to allow, done where nothing is counting —
     * so what comes in is the plan, and the one number is spent on the whole of it.
     */
    public Composed meet(Sameness.Block<A> block, ValueSet one, AdmittedPlan other) {
        return put(block, AdmittedPlan.meeting(List.of(AdmittedPlan.of(one), other)));
    }

    /**
     * The values any of them admits, said as one plan over all of them.
     *
     * <p>For a caller holding several at once, which is what a block holds across the
     * alternatives of a reading. Handed over together they are one plan however they were held, so
     * what they come to is worked out in the order the plan settles and costs one number. Folded
     * two at a time by the caller, the order would be the order it happened to hold them in — and a
     * set is a set however it was filled, so the same alternatives would cost two different things.
     */
    public Composed joining(Sameness.Block<A> block, List<ValueSet> these) {
        return put(block, AdmittedPlan.joining(these.stream().map(AdmittedPlan::of).toList()));
    }

    /**
     * The values all of them admit, said as one plan over all of them.
     *
     * <p>{@link #joining} for a conjunction, and there for the same reason. A block holding
     * several positions as one value gathers what each of them was stated to admit, and how many
     * that is turns on how the equalities making the block were written — so they are handed over
     * together and cost one number, where a fold two at a time would cost what the writing happened
     * to be.
     */
    public Composed meeting(Sameness.Block<A> block, List<ValueSet> these) {
        return put(block, AdmittedPlan.meeting(these.stream().map(AdmittedPlan::of).toList()));
    }

    /**
     * The same two sets, where what comes out is a promise rather than a bound.
     *
     * <p>Giving up leaves nothing and not everything, which is the other direction. What
     * {@link #meet} answers is which values a position may hold, so an answer this did not build
     * widens to every value and stays true. A promise says which values it certainly
     * may hold, and every value is the strongest thing that can be said rather than the weakest —
     * so an unbuilt one promises nothing, and a reader is short of a guarantee instead of holding
     * one nobody proved.
     */
    public Composed meetPromised(Sameness.Block<A> block, ValueSet one, ValueSet other) {
        return promised(meet(block, one, other));
    }

    /**
     * The same, of several at once, which is what one reading promises a block a coarser relation
     * holds as one value.
     *
     * <p>{@link #meeting} for a promise, and n-ary for its reason. A block of a coarser relation
     * covers several of this reading's own, and what it promises there is what it promises at every
     * one of them — so they are handed over together and cost one number, where a caller folding
     * them two at a time would pay for the order it happened to walk that block's positions in.
     */
    public Composed meetingPromised(Sameness.Block<A> block, List<ValueSet> these) {
        return promised(meeting(block, these));
    }

    private static Composed promised(Composed made) {
        return made.gaveUp() ? new Composed(ValueSet.NONE, true) : made;
    }

    private static List<AdmittedPlan> both(ValueSet one, ValueSet other) {
        return List.of(AdmittedPlan.of(one), AdmittedPlan.of(other));
    }

    /**
     * What a plan comes to at {@code block}, out of what that block is allowed.
     *
     * <p>Said as a plan and then worked out, rather than built as the two sets are handed over.
     * Which is what puts the order the work is done in with the thing being asked for: two sets are
     * one plan however they arrived, so what they cost is one number, and a plan already worked out
     * is not worked out again.
     *
     * <p>A block given up on stays given up on. Once the exact answer is not being built,
     * everything after it is about a set this compiler made wider, and buying a machine to narrow
     * something already widened is spending on an answer nobody can use.
     */
    private Composed put(Sameness.Block<A> block, AdmittedPlan plan) {
        if (isSpent(block)) {
            return gaveUp(block);
        }
        Realization made = at(block).of(plan);
        return made.isExact() ? new Composed(made.upperBound(), false) : gaveUp(block);
    }

    /**
     * What works a position's plans out, and what it has left to do it with.
     *
     * <p>One per block and kept, so a plan worked out twice is worked out once — which is what
     * lets a caller ask early without the block paying twice, and what makes when it was asked
     * no part of what it cost.
     *
     * <p>Not handed out. What a block may build is asked for through the readings that answer
     * for it and through {@link #realizeAll}; a caller holding this holds the block's whole
     * allowance and can spend it on anything, one plan at a time, which is every arrangement above
     * undone from outside.
     */
    Realizer realizer(Sameness.Block<A> block) {
        return realizers.computeIfAbsent(block,
                _ -> new Realizer(meter(block), plan -> borrowed.of(block, plan),
                        (plan, made) -> borrowed.made(block, plan, made)));
    }

    /**
     * What every one of {@code plans} admits at {@code block}, built as one answer.
     *
     * <p>For a reading about to publish what a block's rules leave to readers that build
     * nothing. What crosses that boundary is a set and never the plan that names one — handed the
     * plan, a reader would be making the machine itself, under whatever allowance it happened to
     * have, and the position's answer and the reader's would be two answers to one question.
     *
     * <p><b>All of them or none of them ({@link Realizations}).</b> Asked one at a time, a caller
     * would publish the ones the allowance reached and stop, and which of a position's rules a
     * reader hears about would follow the order they were walked in. Which is why this takes the
     * whole list rather than being a loop somebody writes: there is no way to spell a partial
     * answer here.
     *
     * <p>A machine this allowance may borrow is not made again ({@link #besides}), wherever it
     * comes up: a plan of it inside a plan being built out of others is read the same way, so what
     * is charged here is the difference and never what was already bought.
     *
     * <p>A block given up on stays given up on, here as everywhere: a group refused leaves the
     * block spent, so a caller that asks again is told the same thing rather than being sold the
     * rest of the allowance one plan at a time.
     */
    public Realizations realizeAll(Sameness.Block<A> block, Collection<AdmittedPlan> plans) {
        if (isSpent(block)) {
            return new Realizations.NotBuilt();
        }
        // In the order the plans themselves settle and not the order a caller gathered them in
        // ({@link PlanOrder}). What is being built is the same whichever way round, and what it
        // costs is not: the small ones first is the difference between a meet answered out of a set
        // in hand and a product nobody needed. A caller that walked its parts another way round
        // would spend a different number on the same rules.
        List<AdmittedPlan> order = new ArrayList<>(plans);
        order.sort(Comparator.comparing(PlanOrder::of));
        Map<AdmittedPlan, ValueSet> out = new LinkedHashMap<>();
        for (AdmittedPlan each : order) {
            // The first refusal stops the rest. What is left to build is for an answer this is not
            // going to give, and it would come out of the same allowance the rest of this
            // block's group draws on.
            Realization made = at(block).of(each);
            if (!(made instanceof Realization.Exact it)) {
                gaveUp(block);
                return new Realizations.NotBuilt();
            }
            out.put(each, it.set());
        }
        return new Realizations.Exact(out);
    }

    /**
     * The same for a set belonging to no block.
     *
     * <p>A reading holds one of those: what it guarantees at every block it holds no guarantee
     * for. It is not any block's, so it cannot be charged to one — put on the first block
     * that happened to be asked, it would take the allowance of a block whose own rules had not
     * been read yet.
     */
    public Realizer elsewhere() {
        if (nowhere == null) {
            nowhereMeter = budget.meter();
            nowhere = new Realizer(nowhereMeter, plan -> borrowed.of(null, plan),
                    (plan, made) -> borrowed.made(null, plan, made));
        }
        return nowhere;
    }

    private Realizer at(Sameness.Block<A> block) {
        return block == null ? elsewhere() : realizer(block);
    }

    /**
     * What {@code plan} was already worked out to admit at {@code atom}, or null where nothing
     * worked it out.
     *
     * <p>The one way to ask this allowance a question without spending it, and it is for the
     * accounts. What a rule of the model did to a position is read off the answer that was built
     * for the position; asked by building, an account would be paying out of the budget the answer
     * is bounded by, and a limit reached while attributing a reason would show up as the compiler
     * being less able to answer about the model.
     *
     * <p>Null is "nothing established this" and never "this admits everything". The two read alike
     * to a careless caller and mean opposite things: one is a fact about the values, and the other
     * is an absence of evidence that a caller answers by declining to claim.
     */
    public ValueSet known(Sameness.Block<A> block, AdmittedPlan plan) {
        return at(block).known(plan) instanceof Realization.Exact it ? it.set() : null;
    }

    /**
     * The blocks whose exact answer was given up on, in the order they were found.
     *
     * <p>Not how a reader learns of it. What each of them left is in the reading already — a
     * {@link Composed} carried the widening and the shortfall to whoever asked, together, so
     * neither can arrive without the other — and this is the same fact gathered, for holding the
     * allowance to what it is supposed to do.
     */
    public Set<Sameness.Block<A>> spent() {
        return Set.copyOf(spent);
    }

    /**
     * How much {@code block} has left, for a caller holding this allowance to what it spent.
     *
     * <p>Not how a reader learns anything about a model. What each block came to and whether it
     * is exact is in the reading; this is the number itself, for measuring that the same rules cost
     * the same.
     */
    public int left(Sameness.Block<A> block) {
        return meter(block).left();
    }

    /**
     * The blocks this has opened a purse for, for a caller holding it to what it is a purse per.
     *
     * <p>Not how a reader learns anything about a model. Which coordinates an answer was built in
     * is a fact about this compiler, and a reading with no equality in it opens one purse per
     * position — which is what every reading did before a rule could hold two positions as one
     * value.
     */
    public Set<Sameness.Block<A>> purses() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(meters.keySet()));
    }

    /**
     * How much this has spent in all, for a caller holding it to what it spent between two moments.
     *
     * <p>One number over every purse, and not a purse for every position a caller can name. Which
     * blocks exist is not settled before the rules are read — one is made where an equality holds
     * two positions as one value — so a caller listing the purses it expects would be listing the
     * ones it already knew about, and a purse opened in between would go unwatched. Opening one
     * spends nothing, which is why what is compared is the spending rather than the list.
     */
    public int spentSoFar() {
        int spent = 0;
        for (Meter each : meters.values()) {
            spent += budget.mostBuilt() - each.left();
        }
        if (nowhereMeter != null) {
            spent += budget.mostBuilt() - nowhereMeter.left();
        }
        return spent;
    }

    /** Every value there is, and the fact that this is not what the rules leave. */
    private Composed gaveUp(Sameness.Block<A> block) {
        if (block == null) {
            spentElsewhere = true;
        } else {
            spent.add(block);
        }
        return new Composed(ValueSet.ANY, true);
    }

    /** Whether the exact answer here was already given up on. */
    private boolean isSpent(Sameness.Block<A> block) {
        return block == null ? spentElsewhere : spent.contains(block);
    }

    private Meter meter(Sameness.Block<A> block) {
        return meters.computeIfAbsent(block, _ -> budget.meter());
    }
}
