package souther.compiler.coverage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The numbering of one module's bodies: what handed the numbers out, and what they mean.
 *
 * <p>What a numbering is, is {@link NumberingIdentity}, and it is a value: two builds come to the
 * same one over the same executable with the same numbers addressing the same places, which is what
 * lets a recording made under one be read under the other. So an address of a numbering is that
 * value and a number, and two derivations of one module hand out addresses that are each other.
 *
 * <p><b>Nothing here is a token minted per construction.</b> One would answer "is this address
 * mine" with a reference comparison, and it would answer wrongly: the store recomputes, and an
 * address held under an answer would stop equalling the one a recomputation makes — so every reader
 * of that answer would run again on every revision, and a claim answered against a recording made a
 * moment earlier would be refused for having been made a moment earlier.
 *
 * <p>What this class is, then, is the one place an address is made. A number crosses back into a
 * place here and nowhere else, and it is refused where this numbering never handed it out or handed
 * it out to the other family.
 */
public final class SiteNumbering {

    private final NumberingIdentity identity;

    private SiteNumbering(NumberingIdentity identity) {
        this.identity = identity;
    }

    /**
     * The numbering {@code identity} names, as something places can be read out of.
     *
     * <p>What a numbering is, is the identity; this adds the reading and nothing else. So it opens
     * no way to an address that was not issued: every place handed out here is one the identity
     * says a number of its own addresses, of the family it says, and a caller stating an identity
     * has stated the places along with it.
     *
     * <p>What it is for is a reader that has an identity and no walk — one that took a recording
     * off an artifact, or a fixture saying which places it means.
     */
    public static SiteNumbering of(NumberingIdentity identity) {
        return new SiteNumbering(identity);
    }

    /** What this numbering is, as two builds can be held against each other by. */
    public NumberingIdentity identity() {
        return identity;
    }

    /**
     * The place {@code raw} was issued for, as an arm.
     *
     * <p>Refused where this numbering handed out no such number, and refused where it handed it out
     * for a comparison. Both are the same mistake seen from two sides — a number read as addressing
     * a place it was not issued for — and neither can be told from a right answer afterwards.
     */
    public ArmProbe arm(int raw) {
        if (!(at(raw) instanceof SiteAddress.Arm)) {
            throw new IllegalArgumentException(
                    raw + " was issued for " + at(raw) + ", which is not an arm");
        }
        return new ArmProbe(identity, raw);
    }

    /** The same, for a comparison. */
    public ComparisonEmissionSite comparison(int raw) {
        if (!(at(raw) instanceof SiteAddress.Comparison)) {
            throw new IllegalArgumentException(
                    raw + " was issued for " + at(raw) + ", which is not a comparison");
        }
        return new ComparisonEmissionSite(identity, raw);
    }

    /**
     * {@code seen}, read as places of this numbering.
     *
     * <p>The one crossing from what a run left behind to what a reader asks about, and the only
     * place a number becomes a place. What it establishes is that the run was recorded under a
     * numbering equal to this one, and that every number it holds is one this numbering handed out.
     * Both are refused rather than answered: a recording of another numbering read under this one
     * says a row went through places it was never near, and that reads as an ordinary yes or no
     * everywhere downstream.
     */
    public AlignedObservation align(Observation seen) {
        if (!identity.equals(seen.numbering())) {
            throw new IllegalArgumentException("a run recorded under " + seen.numbering()
                    + " is being read under " + identity
                    + "; a number means a place under the numbering that handed it out");
        }
        // A family apiece, each read back as what the recording says it is. Nothing here picks the
        // arms out of a set holding both: which family a number was issued to is this numbering's
        // answer, and a number recorded as one family and issued to the other is refused by the
        // reading itself rather than passed over.
        Set<ArmProbe> arms = new LinkedHashSet<>();
        for (int raw : seen.arms()) {
            arms.add(arm(raw));
        }
        Set<SeenComparison> ways = new LinkedHashSet<>();
        for (ComparisonOutcome way : seen.comparisons()) {
            ways.add(new SeenComparison(comparison(way.at()), way.held()));
        }
        return new AlignedObservation(identity, arms, ways);
    }

    private SiteAddress at(int raw) {
        return identity.at(raw);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SiteNumbering that && identity.equals(that.identity);
    }

    @Override
    public int hashCode() {
        return identity.hashCode();
    }

    @Override
    public String toString() {
        return identity.toString();
    }

    /** A numbering being handed out, before what it numbers is known. */
    static Building begin() {
        return new Building();
    }

    /**
     * The numbering while it is being handed out.
     *
     * <p>A number and what it addresses are one act: the walk asks for a place to be numbered and
     * the place is written down in the same call, so there is no moment at which a number exists
     * and what it is an address of has still to be worked out. Which is the shape the whole of this
     * rests on — a number paired with a place anywhere else would be that correspondence rebuilt
     * from whatever the rebuilder had to hand.
     *
     * <p>What it hands back is the number and not an address, because there is no numbering yet: a
     * numbering is every place and what the bodies do, and both are still being found out. The walk
     * carries numbers until {@link #finish}, and then reads them back as places of what it made.
     */
    static final class Building {

        private final List<SiteAddress> byNumber = new ArrayList<>();

        private final Set<SiteAddress> issued = new LinkedHashSet<>();

        /**
         * A number for {@code where}.
         *
         * <p>A number and not an address, because there is no numbering yet for an address to be
         * of: what a number means is fixed by every place this walk has still to reach, and by what
         * the bodies do. So the walk carries numbers and the addresses are made at
         * {@link #finish}, which is the moment a number first means anything.
         */
        int number(SiteAddress where) {
            // One number per place. A node several ways lead to is arrived at once per way, and a
            // place numbered twice is a second site the emitter lights on no run — the shape of a
            // real omission, and read as one by every count.
            if (!issued.add(where)) {
                throw new IllegalStateException(where + " is numbered twice; a place a run is"
                        + " recorded at is recorded at one number");
            }
            byNumber.add(where);
            return byNumber.size() - 1;
        }

        /** The numbering, now that what its bodies do is known too. The one place a numbering is
         *  decided from bodies, and what every later walk of them is held to. */
        SiteNumbering finish(String module, Map<String, ExecutableIdentity> executable) {
            return new SiteNumbering(new NumberingIdentity(module, executable, byNumber));
        }

        /**
         * {@code identity}, as the numbering of what this walk found — the walk having been shown
         * to have found it.
         *
         * <p>What a second walk of one module's bodies is for. The numbering is decided once, by
         * whoever holds the bodies, and a later walk wants the places rather than a second opinion
         * about what the numbers mean; so it takes the numbering that was issued, and every address
         * it hands out is an address of that one.
         *
         * <p><b>Shown and not assumed.</b> Taking the numbering on the strength of having walked
         * the same bodies is what this must not do. A walk that came to number a place differently
         * would hand out an address carrying a numbering that says the number means something else,
         * and the two would be the same address to everything downstream — so the disagreement
         * that a numbering decided twice would have shown would be hidden by the numbering being
         * decided once. What is compared is what a numbering is and no more: whose module, what the
         * bodies do, and what each number addresses. How the plan around it is laid out is not part
         * of that and is not looked at.
         *
         * <p>Refused a component at a time, because what a reader of the refusal needs is which
         * half of the walk stopped agreeing.
         */
        SiteNumbering realize(NumberingIdentity identity, String module,
                             Map<String, ExecutableIdentity> executable) {
            if (!identity.module().equals(module)) {
                throw new IllegalStateException("the numbering issued for " + identity.module()
                        + " is being realized by a walk of " + module
                        + "; a number means a place in the module it was handed out for");
            }
            requireTheSameBodies(identity, executable);
            requireTheSamePlaces(identity);
            return new SiteNumbering(identity);
        }

        /** That the bodies this walk went through are the ones the numbering was issued over. */
        private void requireTheSameBodies(NumberingIdentity identity,
                                          Map<String, ExecutableIdentity> executable) {
            Set<String> issuedOver = identity.executable().keySet();
            if (!issuedOver.equals(executable.keySet())) {
                throw new IllegalStateException("the numbering of " + identity.module()
                        + " was issued over the behaviors " + issuedOver
                        + " and this walk went through " + executable.keySet());
            }
            for (Map.Entry<String, ExecutableIdentity> each : executable.entrySet()) {
                if (!identity.executable().get(each.getKey()).equals(each.getValue())) {
                    throw new IllegalStateException("the numbering of " + identity.module()
                            + " was issued over another body of `" + each.getKey()
                            + "`; two bodies that do different things are not two views of one"
                            + " measurement, however their places line up");
                }
            }
        }

        /** That the places this walk numbered are the places the numbering hands those numbers out
         *  for. */
        private void requireTheSamePlaces(NumberingIdentity identity) {
            List<SiteAddress> issuedFor = identity.byNumber();
            if (issuedFor.size() != byNumber.size()) {
                throw new IllegalStateException("the numbering of " + identity.module()
                        + " handed out " + issuedFor.size() + " numbers and this walk numbered "
                        + byNumber.size() + " places");
            }
            for (int n = 0; n < byNumber.size(); n++) {
                if (!issuedFor.get(n).equals(byNumber.get(n))) {
                    throw new IllegalStateException("the numbering of " + identity.module()
                            + " handed " + n + " out for " + issuedFor.get(n)
                            + " and this walk numbered " + byNumber.get(n) + " with it");
                }
            }
        }
    }
}
