package souther.compiler.claims;

import souther.compiler.check.PathReachability;
import souther.compiler.reach.Reachability;

import java.util.ArrayList;
import java.util.List;

/**
 * A behavior's claims, held against the reading of its input.
 *
 * <p>The one place the two meet. Whether a case declared unreachable is refused, left owed with the
 * claim named as unproven, or reported as a contradiction is one decision, and a decision made per
 * measure is one each measure can make differently — which is how a case left the signature's
 * denominator while the rules that would have refuted it were never asked.
 *
 * <p>What comes out of here reaches a diagnostic and a report and nothing else. The denominators are
 * the reading's, before this runs and after it.
 */
public final class Claims {

    /** Nothing claimed, which is what a behavior with no body and most bodies come to. */
    public static final Claims NONE = new Claims(List.of());

    /** One claim and what the rules said about it. */
    public record Judged(Claim claim, ClaimVerdict verdict) {}

    private final List<Judged> judged;

    private Claims(List<Judged> judged) {
        this.judged = List.copyOf(judged);
    }

    /**
     * Two of these are one where they judged the same claims the same way.
     *
     * <p>The question it answers is whether a check that ran again came to what the last one came
     * to. This is reached from what a module's check answers with, and what stops the work an edit
     * costs is that answer comparing equal to the one it replaces — so a reading of the same claims
     * against the same arrivals that came back saying only which run made it would leave every
     * reader of the check running again.
     *
     * <p>Over the judged claims and nothing else, which is the whole of what one of these is.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Claims that && judged.equals(that.judged);
    }

    @Override
    public int hashCode() {
        return judged.hashCode();
    }

    /**
     * Every claim {@code claims} makes, judged against {@code read}.
     *
     * <p>A claim naming a position the reading has none of is unproven rather than anything else:
     * what would settle it was never read, and a target nothing resolves is a fact about how far
     * this compiler walks rather than about the model.
     */
    public static Claims of(UnreachableClaims claims, PathReachability.Answers arrives) {
        if (claims.isEmpty()) {
            return NONE;
        }
        // The claims name arms of one plan and the reading answers about the places of one plan, so
        // the two are held to being the same plan's here, where they are put together. Read against
        // another module's, every arm would be one the reading has nothing filed under — which is
        // the sentence below, and would be untrue of both bodies.
        arrives.requireNumbering(claims.numbering().orElseThrow());
        List<Judged> out = new ArrayList<>();
        for (Claim claim : claims.all()) {
            out.add(new Judged(claim, verdictOf(arrives.at(claim.where()))));
        }
        return new Claims(out);
    }

    /**
     * What one claim comes to, which is the one reading of what arrives read for this question.
     *
     * <p>Three answers and three, so there is nothing to decide here. The rules refusing the case
     * is the claim borne out; something arriving is the claim refuted, and what says something
     * arrives is a witness rather than the absence of a proof; anything else leaves the claim where
     * it was. Written as a test of one thing and an else, a claim would be refuted wherever the
     * reading happened to be silent.
     */
    private static ClaimVerdict verdictOf(Reachability arrives) {
        return switch (arrives) {
            case Reachability.Unreachable _ -> new ClaimVerdict.Confirmed();
            case Reachability.Reachable _ -> new ClaimVerdict.Contradicted();
            case Reachability.Unsettled unsettled -> new ClaimVerdict.Unproven(unsettled.why());
        };
    }

    public boolean isEmpty() {
        return judged.isEmpty();
    }

    /** Every claim with what was said about it, in the order the body makes them. */
    public List<Judged> all() {
        return judged;
    }

    /** The claims of one kind, which is how a diagnostic asks for the ones it refuses and a report
     *  for the ones it prints. */
    public List<Judged> thatAre(Class<? extends ClaimVerdict> verdict) {
        return judged.stream().filter(each -> verdict.isInstance(each.verdict())).toList();
    }
}
