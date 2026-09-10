package souther.compiler.coverage;

import souther.compiler.core.Core;
import souther.compiler.types.ModelOccurrence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a run through each arm the model states is recorded.
 *
 * <p>The crossing {@link ComparisonEmissionIndex} makes, for the other kind of place. A fork is
 * read where the language's operations stand and a run through its arms is recorded where they are
 * expanded, so what the two trees agree about is the construct of the model — and an arm of it is
 * that construct with which of its ways.
 *
 * <p><b>One fork of the model, several materialisations of it.</b> A helper carrying a fork is
 * expanded once per call site, so one authored fork stands in the tree that runs as many times as
 * it is called. Each is a place a run can be recorded at, and a row through any of them went
 * through the arm the model states — which is the same reading the arm obligations already take,
 * where a helper's arm is one thing to cover however often it is called.
 *
 * <p><b>Empty where the emitter numbered nothing.</b> An arm answering {@code unreachable} answers
 * no value and carries no probe, so a run through it is recorded nowhere. What comes back says that
 * and no more: it is not the arm being unreachable, and it is not the fork being absent.
 */
public final class ArmEmissionIndex {

    /** Which arm of which fork of the model, which is what a run through one is about. */
    public record ArmOfTheModel(ModelOccurrence fork, int part) {

        public ArmOfTheModel {
            if (fork == null) {
                throw new IllegalArgumentException("an arm is an arm of some fork of the model");
            }
            if (part < 0) {
                throw new IllegalArgumentException("an arm stands somewhere among its fork's: "
                        + part);
            }
        }
    }

    private final Map<ArmOfTheModel, List<ControlPlace.Arm>> emitted;

    private ArmEmissionIndex(Map<ArmOfTheModel, List<ControlPlace.Arm>> emitted) {
        this.emitted = emitted;
    }

    /** The index of one emitted body, against the plan that numbered it. */
    public static ArmEmissionIndex ofBody(Core body, CoverageSites.Plan plan) {
        Map<ArmOfTheModel, Map<ArmOccurrence, ControlPlace.Arm>> emitted = new LinkedHashMap<>();
        walk(body, plan, emitted);
        Map<ArmOfTheModel, List<ControlPlace.Arm>> out = new LinkedHashMap<>();
        emitted.forEach((arm, made) -> out.put(arm, List.copyOf(made.values())));
        return new ArmEmissionIndex(Map.copyOf(out));
    }

    private static void walk(Core e, CoverageSites.Plan plan,
                             Map<ArmOfTheModel, Map<ArmOccurrence, ControlPlace.Arm>> emitted) {
        ControlPlace.Arm[] arms = plan.armsOf(e);
        if (arms != null) {
            for (ControlPlace.Arm arm : arms) {
                if (arm == null) {
                    continue;
                }
                // Only where the model states the fork. A fork inside one of the language's own
                // operations is materialised once per call of it and the model states none of them,
                // so filing them all would be one key over as many places as the body calls the
                // operation.
                //
                // Filed under the occurrence the numbering gave it, so a node the walk reaches
                // twice contributes one materialisation rather than two.
                ModelOccurrence.statedAt(arm.arm().fork()).ifPresent(fork ->
                        emitted.computeIfAbsent(new ArmOfTheModel(fork, arm.part()),
                                        _ -> new LinkedHashMap<>())
                                .putIfAbsent(arm.arm(), arm));
            }
        }
        Core.forEachChild(e, child -> walk(child, plan, emitted));
    }

    /**
     * Every materialisation of {@code arm} in the tree that runs, in the order the walk met them.
     *
     * <p>Empty where the emitted tree holds none, which is the two readings disagreeing about the
     * body rather than an arm nothing was measured about — a caller raises rather than answering
     * around it.
     */
    public List<ControlPlace.Arm> madeFor(ArmOfTheModel arm) {
        return emitted.getOrDefault(arm, List.of());
    }
}
