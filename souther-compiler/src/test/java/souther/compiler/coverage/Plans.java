package souther.compiler.coverage;

import souther.compiler.core.Core;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * What a test asks of a plan that the package keeps to itself.
 *
 * <p>Two kinds of thing, and both are here for the same reason: a plan is made where the bodies
 * are walked, and what it holds is not handed out. A test driving a reader into a state no source
 * reaches still has to hand it a plan, and a test counting what a plan numbered has to see the
 * nodes — neither of which a caller outside this package can do.
 *
 * <p>Assembled here and not asked of the plan. A derived plan is wanted by tests and by nothing
 * this compiler does, so the operation making one belongs where its callers are: written on the
 * plan it would stand in the list of what may put a plan together, under a reason that is about a
 * test — which is not what that list is for.
 */
public final class Plans {

    private Plans() {
    }

    /**
     * {@code plan}, answering that a run may come back to anywhere.
     *
     * <p>What the walk cannot be made to produce today, which is why it is stated rather than
     * arranged out of a source.
     */
    public static CoverageSites.Plan whereEverythingRepeats(CoverageSites.Plan plan) {
        AbstractSet<Core> everywhere = new AbstractSet<>() {

            @Override
            public boolean contains(Object node) {
                return true;
            }

            @Override
            public Iterator<Core> iterator() {
                return Collections.emptyIterator();
            }

            @Override
            public int size() {
                return 0;
            }
        };
        return new CoverageSites.Plan(plan.sites(), plan.guards(), plan.byNode(),
                plan.byComparison(), plan.armsByNode(), everywhere,
                plan.whereEachArmsForkIsWritten(), plan.comparisons(), plan.numbering());
    }

    /**
     * {@code plan} with {@code now} standing where {@code was} stood, and nothing else moved.
     *
     * <p>Matched by what an occurrence is and not by which object it is: a reading held against
     * this was made against a derivation of its own, and holds equal places rather than the same
     * ones.
     */
    public static CoverageSites.Plan withArmRenamed(CoverageSites.Plan plan,
                                                    ControlPointId.ArmPoint was,
                                                    ControlPointId.ArmPoint now) {
        IdentityHashMap<Core, ControlPointId.ArmPoint[]> arms = new IdentityHashMap<>();
        plan.armsByNode().forEach((node, held) -> {
            ControlPointId.ArmPoint[] out = held.clone();
            for (int at = 0; at < out.length; at++) {
                if (out[at].equals(was)) {
                    out[at] = now;
                }
            }
            arms.put(node, out);
        });
        return new CoverageSites.Plan(plan.sites(), plan.guards(), plan.byNode(),
                plan.byComparison(), arms, plan.mayRepeat(),
                plan.whereEachArmsForkIsWritten(), plan.comparisons(), plan.numbering());
    }

    /** The nodes this plan numbered arms for, which is what a test counting forks walks. */
    public static List<Core> nodesWithArms(CoverageSites.Plan plan) {
        return List.copyOf(plan.byNode().keySet());
    }
}
