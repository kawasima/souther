package souther.compiler.coverage;

import souther.compiler.core.Core;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Plans a test needs that a walk of any source does not produce.
 *
 * <p>Here because a plan is made where the bodies are walked and nowhere else. A test driving a
 * reader into a state the compiler does not reach today still has to hand it a plan, and building
 * one out of parts is the one thing the shape of {@link CoverageSites.Plan} is meant to keep a
 * caller from doing — so it is done in this package, once, under names saying what each derived
 * plan differs in, rather than by ten fields copied at each test that wants one.
 *
 * <p>What each of these makes is a plan of the same bodies as the one handed in: the trees are the
 * ones already walked, and it is those objects the derived plan goes on addressing.
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
                plan.byComparison(), plan.armsByNode(), plan.controlByComparison(),
                everywhere, plan.forkByNode(), plan.comparisons(), plan.numbering());
    }

    /**
     * {@code plan} with {@code now} standing where {@code was} stood, and nothing else moved.
     *
     * <p>Matched by what an occurrence is and not by which object it is: a reading held against
     * this was made against a derivation of its own, and holds equal places rather than the same
     * ones.
     */
    public static CoverageSites.Plan withArmRenamed(CoverageSites.Plan plan,
                                                    ControlPointId.ArmOccurrence was,
                                                    ControlPointId.ArmOccurrence now) {
        IdentityHashMap<Core, ControlPointId.ArmOccurrence[]> arms = new IdentityHashMap<>();
        plan.armsByNode().forEach((node, held) -> {
            ControlPointId.ArmOccurrence[] out = held.clone();
            for (int at = 0; at < out.length; at++) {
                if (out[at].equals(was)) {
                    out[at] = now;
                }
            }
            arms.put(node, out);
        });
        return new CoverageSites.Plan(plan.sites(), plan.guards(), plan.byNode(),
                plan.byComparison(), arms, plan.controlByComparison(), plan.mayRepeat(),
                plan.forkByNode(), plan.comparisons(), plan.numbering());
    }

    /** The nodes this plan numbered arms for, which is what a test counting forks walks. */
    public static List<Core> nodesWithArms(CoverageSites.Plan plan) {
        return List.copyOf(plan.byNode().keySet());
    }
}
