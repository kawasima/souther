package souther.compiler.values;

import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a position's plan comes to, built once and under an allowance.
 *
 * <p>The plan says which values the position admits and says it without an order
 * ({@link AdmittedPlan}); this turns it into the set. What that costs is counted where the states
 * are made ({@link Meter}), and where the allowance runs out what comes back is that it was not
 * built — never a smaller set, which would be this compiler answering a model with something it
 * made up because the truth was expensive.
 *
 * <p><b>The order the work is done in is this one's to choose, and it is chosen from the plan.</b>
 * A meet holding a set the rules wrote out is a meet whose answer is inside that set, so it is
 * settled by asking the written values and never by building anything — {@code [a-z]{300}} met with
 * {@code {"x"}} is a question about one string. Read in the order a clause happened to state it,
 * the same rules met the two patterns first and gave up on a product nobody needed. What is chosen
 * here is fixed by the plan and not by a heuristic that could change: a reader may work out what
 * this will do from what it is given.
 *
 * <p>Answers are kept, so a plan realized twice is realized once. Which is what lets a caller force
 * an answer early — to ask whether the position admits anything at all, say — without the position
 * paying twice for it, and what makes when it was asked not part of what it costs.
 */
final class Realizer {

    private final Meter meter;
    /**
     * What another question of this position built, or nothing.
     *
     * <p>Asked wherever a plan is, which is what makes it a second place a machine may already
     * exist rather than a shortcut at the top. A plan built out of others reaches its parts through
     * {@link #of}, so a part somebody else built is found there too — looked up only for what a
     * caller named, the parts underneath would be made again and this allowance would pay for
     * machines that exist.
     */
    private final Function<AdmittedPlan, ValueSet> borrowed;
    /** Told what was built here where the lender had nothing, so a lender that keeps machines
     *  has this one's. */
    private final BiConsumer<AdmittedPlan, Realization> noting;
    private final Map<AdmittedPlan, Realization> done = new LinkedHashMap<>();

    Realizer(Meter meter, Function<AdmittedPlan, ValueSet> borrowed,
             BiConsumer<AdmittedPlan, Realization> noting) {
        this.meter = meter;
        this.borrowed = borrowed;
        this.noting = noting;
    }

    /**
     * What {@code plan} admits if that has already been worked out, and null where it has not.
     *
     * <p>Nothing is built and nothing is spent. A caller here is not asking what a plan admits — it
     * is asking what this position's answer already established, which is a different question and
     * the only one an account may ask: what a rule of the model did is read off the answer, and an
     * account that built anything would be paying out of the budget the answer is bounded by.
     *
     * <p>So the absence of a row is an absence of evidence and never a fact about the plan. A
     * caller told nothing is told exactly that, and what it does about it is decline to claim.
     */
    Realization known(AdmittedPlan plan) {
        return done.get(plan);
    }

    /** What {@code plan} admits, worked out at most once however often it is asked for. */
    Realization of(AdmittedPlan plan) {
        Realization had = done.get(plan);
        if (had != null) {
            return had;
        }
        // What another question of this position made, before anything is made here. Kept like
        // everything else, so the lending question is asked once and the second asking is this
        // one's own answer.
        ValueSet lent = borrowed.apply(plan);
        Realization made;
        if (lent == null) {
            made = built(plan);
            noting.accept(plan, made);
        } else {
            made = new Realization.Exact(lent);
        }
        done.put(plan, made);
        return made;
    }

    private Realization built(AdmittedPlan plan) {
        return switch (plan) {
            case AdmittedPlan.Everything _ -> new Realization.Exact(ValueSet.ANY);
            case AdmittedPlan.Nothing _ -> new Realization.Exact(ValueSet.NONE);
            case AdmittedPlan.Of it -> new Realization.Exact(it.set());
            case AdmittedPlan.Pattern it -> pattern(it);
            case AdmittedPlan.Both it -> met(it.parts());
            case AdmittedPlan.Either it -> joined(it.parts());
        };
    }

    /**
     * The machine for one pattern, made here rather than where the pattern was read.
     *
     * <p>Because it is spending, and this is where a position's spending is arranged. A pattern met
     * with three written strings is a question about three strings — built where it was read, it
     * was a machine nobody needed, and the position had that much less for the meet it did need.
     *
     * <p>Kept like everything else, so the same pattern written into three rules is one machine.
     * Which is also why no rule is named here and the pattern is: the machine is the pattern's, and
     * a rule that asked for one refused is a rule whose reading asked for that pattern — three of
     * them where three wrote it.
     */
    private Realization pattern(AdmittedPlan.Pattern plan) {
        Language made = plan.plan().compile(meter);
        if (made != null) {
            return new Realization.Exact(ValueSet.matching(made));
        }
        // The one place the machine limit is about a rule. What was being built is a pattern
        // somebody wrote, so a machine larger than a machine may be is that pattern's size — the
        // same one asked for first, out of a full allowance, would have been refused the same way.
        return meter.stoppedBy() == Meter.Stopped.ONE_MACHINE
                ? new Realization.OverTheMachineLimit(plan.plan())
                : new Realization.OverTheAnswerLimit();
    }

    /**
     * What all of them admit.
     *
     * <p>The written values first, wherever the plan holds any. A meet with them is a question
     * about the values they name, asked of each — free whatever the other side is, and exact.
     * Everything after it is a meet with what that left, which is those values again.
     *
     * <p>So a plan holding one written set builds no product of languages however many are beside
     * it: each of them is still made — a pattern is a machine and asking which of the written
     * values it holds means having it — and none of them is met with another. What that is worth is
     * the product: two patterns of three hundred states apiece are six hundred made and ninety
     * thousand not made.
     *
     * <p>That is the whole of the choosing, and it is read off the plan: the same plan comes to the
     * same work.
     */
    private Realization met(java.util.Set<AdmittedPlan> parts) {
        List<AdmittedPlan> rest = new ArrayList<>();
        ValueSet written = null;
        for (AdmittedPlan each : parts) {
            if (each instanceof AdmittedPlan.Of it && it.isFree()) {
                written = written == null ? it.set() : Sets.metPlainly(written, it.set());
            } else {
                rest.add(each);
            }
        }
        // The written values first, and that is the whole of the choosing. A meet with them is a
        // question about the values they name — asked of each, which builds no machine of its own
        // whatever the other side is — so what all of it comes to is settled without a product.
        // What is left
        // after them is folded in the order the plan holds it, which the plan settled from what its
        // parts are; either order taken from how the parts arrived would cost a product one way
        // round and nothing the other, for rules that are the same rules.
        ValueSet out = written;
        for (AdmittedPlan each : rest) {
            Realization one = of(each);
            if (!(one instanceof Realization.Exact it)) {
                return one;
            }
            if (out == null) {
                out = it.set();
                continue;
            }
            Realization made = both(out, it.set());
            if (!(made instanceof Realization.Exact met)) {
                return made;
            }
            out = met.set();
        }
        return new Realization.Exact(out);
    }

    private Realization joined(java.util.Set<AdmittedPlan> parts) {
        ValueSet out = null;
        for (AdmittedPlan each : parts) {
            Realization one = of(each);
            if (!(one instanceof Realization.Exact it)) {
                return one;
            }
            if (out == null) {
                out = it.set();
                continue;
            }
            Realization made = either(out, it.set());
            if (!(made instanceof Realization.Exact joinedOne)) {
                return made;
            }
            out = joinedOne.set();
        }
        return new Realization.Exact(out);
    }

    private Realization both(ValueSet one, ValueSet other) {
        return outcome(Sets.metUnder(one, other, meter));
    }

    private Realization either(ValueSet one, ValueSet other) {
        return outcome(Sets.joinedUnder(one, other, meter));
    }

    /**
     * What putting two of a position's sets together came to.
     *
     * <p>A refusal here is about the answer whichever limit said no. What was being built is what
     * two rules leave between them, and no author wrote it: two patterns each small on its own have
     * a meet the size of their product, so a machine larger than a machine may be is as much a fact
     * about the pair as an allowance run down is. Named as one of the rules, it would tell an
     * author to rewrite something that is not why.
     */
    private Realization outcome(ValueSet made) {
        return made == null ? new Realization.OverTheAnswerLimit() : new Realization.Exact(made);
    }
}
