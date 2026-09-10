package souther.compiler.partition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * What one run of the generator came to, against the plan it was asked with.
 *
 * <p>A fill is total over its plan. Every class and every arm the plan names has an entry saying
 * what became of it, and the constructor is where that is settled — so a way out of the search that
 * writes no entry is a value nothing can build, rather than a silence a reader downstream has to
 * make something of. What such a reader used to make of it was a sentence saying the generator had
 * failed to say, which was true and was the only thing left to say.
 *
 * <p>Apart from {@link Generator.GenerationResult}, which is what a generation with no plan comes
 * to. The rows offered at a behavior's boundaries are composed for points nobody was asked about
 * and nothing is owed at, and holding both in one shape meant a result that had dropped its
 * obligations and one that never had any were the same value.
 *
 * <p><b>What a row was composed for is not held here.</b> It is read back off the discharge, which
 * is the one place that says which obligation a row answered. Written down beside the row as well,
 * the two came apart: a row composed for a class and later found to take an arm was merged into one
 * line, and the class's own entry went on holding the line from before the merge.
 *
 * @param plan       what this run was asked for
 * @param composed   the rows, in the order they were composed, which is the order a reader is
 *                   offered them in
 * @param unresolved what each place a row was looked for came to, said once apiece
 * @param reasons    what happened to this run as a whole, which is never an answer about one thing
 *                   the plan named
 * @param discharge  what became of each of them
 */
public record FillResult(GenerationPlan plan, SequencedMap<RowId, ComposedRow> composed,
                         List<Generator.UnresolvedCombination> unresolved,
                         List<GenerationReason> reasons, Discharge discharge) {

    public FillResult {
        composed = Ordered.copyOf(composed);
        unresolved = List.copyOf(unresolved);
        reasons = List.copyOf(reasons);
        if (!discharge.classes().keySet().equals(new LinkedHashSet<>(plan.classesOwed()))) {
            throw new IllegalStateException(
                    "the classes this run was asked for and the ones it answered for are not the"
                            + " same: asked " + plan.classesOwed()
                            + ", answered " + discharge.classes().keySet());
        }
        if (!discharge.arms().keySet().equals(new LinkedHashSet<>(plan.armsOwed()))) {
            throw new IllegalStateException(
                    "the arms this run was asked for and the ones it answered for are not the same:"
                            + " asked " + plan.armsOwed()
                            + ", answered " + discharge.arms().keySet());
        }
        // And the rows against what the answers point at, in both directions. A row nothing points
        // at is one nobody was offered — it would come out of the projection below with nothing to
        // say it is for, which is not a row — and an answer pointing at a row that is not here is
        // an obligation reported as met by a line the offer does not hold.
        Set<RowId> answered = new LinkedHashSet<>();
        for (ClassDisposition each : discharge.classes().values()) {
            if (each instanceof ClassDisposition.Built built) {
                answered.add(built.rowId());
            }
        }
        for (ArmDisposition each : discharge.arms().values()) {
            if (each instanceof ArmDisposition.Built built) {
                answered.add(built.rowId());
            }
        }
        if (!answered.equals(composed.keySet())) {
            throw new IllegalStateException(
                    "the rows this run composed and the rows its answers point at are not the same:"
                            + " composed " + composed.keySet() + ", pointed at " + answered);
        }
    }

    /** Nothing asked for, nothing composed, nothing to answer for. */
    public static FillResult nothingAskedOf(GenerationPlan plan) {
        return new FillResult(plan, new LinkedHashMap<>(), List.of(), List.of(),
                Discharge.NOTHING);
    }

    /**
     * A run that ended before it looked for anything, with every obligation told the same thing.
     *
     * <p>For the ways a generation stops without searching: the rows could not be read, the classes
     * would not link. Each of those is one fact about the run, said in {@code reasons} — and each of
     * them used to be the whole of what was recorded, so a reader asking after one class or one arm
     * found nothing at all and said the generator had failed to say. The fact is the same for every
     * obligation here, which is why one word serves them all; what it is not is a reason for a
     * reader to work out from an absence.
     *
     * <p>No {@code unresolved} beside them. That list is the places a row was looked for, and this
     * is a run in which none was.
     */
    public static FillResult nothingWasLookedFor(GenerationPlan plan,
                                                 Generator.UnresolvedCombination.Reason why,
                                                 List<GenerationReason> reasons) {
        Map<ClassOfAPosition, ClassDisposition> classes = new LinkedHashMap<>();
        for (ClassOfAPosition owed : plan.classesOwed()) {
            classes.put(owed, new ClassDisposition.Unresolved(new Generator.UnresolvedCombination(
                    List.of(Generator.labelOf(plan.subject(), owed)), why)));
        }
        Map<Generator.ArmOwed, ArmDisposition> arms = new LinkedHashMap<>();
        for (Generator.ArmOwed owed : plan.armsOwed()) {
            arms.put(owed, new ArmDisposition.Unresolved(
                    List.of(new Generator.UnresolvedCombination(List.of(), why))));
        }
        return new FillResult(plan, new LinkedHashMap<>(), List.of(), reasons,
                new Discharge(classes, arms));
    }

    /**
     * The rows a reader is offered, each carrying what it was composed for.
     *
     * <p>Read off the discharge rather than kept beside the row, so a row that answers two
     * obligations says so because both of them point at it.
     *
     * <p>In the plan's order, classes before arms. Which is one rule, said here: the obligations
     * are held in the order they were gathered exactly so that this order is the same twice, and
     * taking it off the discharge's own iteration would leave the purposes of one model coming out
     * however a map happened to be walked.
     */
    public List<Generator.GeneratedRow> rows() {
        List<Generator.GeneratedRow> out = new ArrayList<>();
        for (RowId id : composed.keySet()) {
            out.add(rowFor(id));
        }
        return List.copyOf(out);
    }

    /** One of them, for a reader holding an answer that points at it. */
    public Generator.GeneratedRow rowFor(RowId id) {
        ComposedRow row = composed.get(id);
        if (row == null) {
            throw new IllegalArgumentException("no row of this run is " + id);
        }
        List<Generator.Purpose> purposes = new ArrayList<>();
        for (ClassOfAPosition owed : plan.classesOwed()) {
            if (discharge.at(owed) instanceof ClassDisposition.Built built
                    && built.rowId().equals(id)) {
                purposes.add(new Generator.Purpose.ForAClass(owed.at(), owed.classId(),
                        Generator.labelOf(plan.subject(), owed)));
            }
        }
        for (Generator.ArmOwed owed : plan.armsOwed()) {
            if (discharge.at(owed) instanceof ArmDisposition.Built built
                    && built.rowId().equals(id)) {
                purposes.add(new Generator.Purpose.ForAnArm(owed.probe()));
            }
        }
        return new Generator.GeneratedRow(purposes, row.inputs());
    }
}
