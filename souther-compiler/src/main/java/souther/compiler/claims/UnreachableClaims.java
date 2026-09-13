package souther.compiler.claims;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.NormalReturn;
import souther.compiler.coverage.NumberingIdentity;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.TermPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a behavior's body declares cannot arrive.
 *
 * <p>Claims and nothing else. Reaching an {@code unreachable} is E1911, so a case behind one is a
 * case an author is being told not to write a row at — and what settles whether they should be told
 * that is the model's own rules, not the sentence saying so. Read here, judged in {@link Claims}.
 *
 * <p><b>Every claim the body makes about a position of its input, and the arm it makes it at.</b>
 * A {@code match} on an input position, wherever it is written, has an arm per case, and an arm
 * that answers nothing is that case being declared not to arrive. What is read off each is the
 * same; what differs is whether anything arrives there, and that is not decided here — the claim
 * carries the arm and the reading of what arrives answers about it.
 *
 * <p>Names are resolved through what the {@code let}s in scope bound, so a helper expanded into the
 * body is read against the position the call handed it. A function written where a call takes one is
 * entered like anything else: what it matches on is resolved the same way, and a name it binds
 * itself resolves to no position of this input, which is the answer that arm deserves.
 *
 * <p>Nothing is read off a condition: telling which values satisfy {@code somePredicate(f)} is what
 * a solver does. So claims are missed, and that direction is the safe one now that a claim moves no
 * denominator — a claim nothing read is a case the report asks for a row at, and an author reading a
 * gap they cannot fill is at least told something true about their model.
 */
public final class UnreachableClaims {

    /** Nothing claimed: a behavior with no body, or one whose body says nothing this can read. */
    public static final UnreachableClaims NONE = new UnreachableClaims(List.of(), Optional.empty());

    private final List<Claim> claims;

    private final Optional<NumberingIdentity> numbering;

    private UnreachableClaims(List<Claim> claims, Optional<NumberingIdentity> numbering) {
        this.claims = List.copyOf(claims);
        this.numbering = numbering;
        if (this.claims.isEmpty() != numbering.isEmpty()) {
            throw new IllegalArgumentException(
                    "a claim names an arm of some plan, and nothing claimed names no plan: "
                            + this.claims.size() + " claimed under " + numbering);
        }
    }

    /**
     * Which plan's arms these claims name, or empty where nothing is claimed.
     *
     * <p>What a reading of the body has to have been made under for its answers to be about these
     * arms. A claim is judged by looking the arm up in that reading, and a reading of some other
     * plan has nothing filed under any of them — which reads as every claim unproven and is not an
     * answer about either body.
     */
    public Optional<NumberingIdentity> numbering() {
        return numbering;
    }

    /**
     * What {@code body} declares cannot arrive.
     *
     * @param read the reading of the behavior's input, which is what tells a {@code match} on one
     *             of its positions from a {@code match} on anything else
     */
    public static UnreachableClaims of(Core body, InputDomain read, Symbols symbols,
                                       DeclarationNewtypes newtypes,
                                       souther.compiler.coverage.CoverageSites.Plan plan) {
        if (body == null) {
            return NONE;
        }
        List<Claim> found = new ArrayList<>();
        claimedUnder(body, InputReads.ofParameters(read.parameterReads(), ElementBindings.NONE),
                symbols, newtypes, plan, NormalReturn.ofBody(body), true, found);
        return found.isEmpty() ? NONE
                : new UnreachableClaims(found, Optional.of(plan.identity()));
    }

    /**
     * Every claim under {@code e}, each standing behind whatever arm it is written in.
     *
     * <p>One walk over the body. Which fork is the one nothing stands above is settled before it
     * starts and asked by identity here, because that is a fact about where the fork sits and not
     * about what it looks like.
     */
    private static void claimedUnder(Core e, InputReads reads, Symbols symbols,
                                     DeclarationNewtypes newtypes,
                                     souther.compiler.coverage.CoverageSites.Plan plan,
                                     NormalReturn answering, boolean reachable, List<Claim> found) {
        if (e == null) {
            return;
        }
        InputReads names = e instanceof Core.LetIn let ? reads.and(let.binder(), let.value())
                : reads;
        // Whether a run gets here, which is about the way in and not about this. A body every arm of
        // which aborts arrives nowhere and is still where its claims are written; what is inside such
        // a part is what nothing reaches, so a claim found further in would be one about a fork
        // behind an abort — a case nobody can be asked for a row at and a gap that would stay open
        // for ever. The same rule, and the same reading, the numbering stops on.
        if (reachable && e instanceof Core.Match match) {
            claimedIn(match, names, newtypes, plan, answering, found);
        }
        boolean inside = reachable && answering.at(e);
        // Each arm under what it says the value it matched turned out to be: the name it binds
        // stands for the scrutinee's position narrowed to that case, so a claim written about a
        // position inside the arm is about a position of the input. Every other child is walked as
        // it was.
        if (e instanceof Core.Match match) {
            claimedUnder(match.scrutinee(), names, symbols, newtypes, plan, answering, inside,
                    found);
            for (Core.Case arm : match.cases()) {
                claimedUnder(arm.body(), names.insideArm(match, arm, symbols, newtypes), symbols,
                        newtypes, plan, answering, inside, found);
            }
            return;
        }
        Core.forEachChild(e, child ->
                claimedUnder(child, names, symbols, newtypes, plan, answering, inside, found));
    }

    /**
     * What one {@code match} declares, and the scrutinee it declares it about.
     *
     * <p>Says nothing where the scrutinee names no position of this input: there is nothing to
     * claim about, and what is under its arms is walked by the caller either way.
     */
    private static void claimedIn(Core.Match match, InputReads reads,
                                  DeclarationNewtypes newtypes,
                                  souther.compiler.coverage.CoverageSites.Plan plan,
                                  NormalReturn answering, List<Claim> found) {
        // A claim is about a position, so a scrutinee that names none carries none.
        TermPath path = switch (reads.pathOf(match.scrutinee(), newtypes)) {
            case PathResolution.At(var at) -> at;
            case PathResolution.NotAPosition _ -> null;
            // A claim is about one position, and a scrutinee that only may stand at one is about
            // whichever of them the run is in. Claimed of each, a case unreachable in one sequence
            // would be claimed unreachable in the other.
            case PathResolution.MayStandAt _ -> null;
        };
        if (path == null) {
            return;
        }
        ControlPlace.Arm[] arms = plan.armsOf(match);
        for (int i = 0; i < match.cases().size(); i++) {
            Core.Case arm = match.cases().get(i);
            if (answering.at(arm.body())) {
                continue;
            }
            if (arms == null || i >= arms.length) {
                continue;   // a fork this plan holds no arms for is one nothing can be asked about
            }
            ControlPlace.Arm where = arms[i];
            List<UnreachableReasons.Said> said = UnreachableReasons.said(arm.body(), answering);
            List<String> why = said.stream().map(UnreachableReasons.Said::reason).distinct().toList();
            // Cases written together on one arm are one run of code, and it declares the same thing
            // about every one of them.
            arm.caseTypes().forEach(each -> found.add(new Claim(path, each, why,
                    said.get(0).at(), where)));
        }
    }

    public boolean isEmpty() {
        return claims.isEmpty();
    }

    /** Every claim, in the order the body makes them. */
    public List<Claim> all() {
        return claims;
    }


}
