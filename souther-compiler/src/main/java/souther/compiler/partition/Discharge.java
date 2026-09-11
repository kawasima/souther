package souther.compiler.partition;

import java.util.Map;

/**
 * What became of each thing a run was asked for.
 *
 * <p>Keyed by the obligation itself, so what a reader joins a finding by is the identity the plan
 * used to ask. The entries used to carry that identity inside them as well and be held in a list,
 * which let one run hold an entry filed under one class whose value named another — and a reader
 * looking a class up by identity found whichever of the two the search wrote first.
 *
 * <p>Whether it covers the plan is {@link FillResult}'s to hold, since that is where the plan is.
 * This is the answers alone.
 *
 * <p>Nothing reads these in order. What a row is offered for is taken in the plan's order, and
 * everything else asks by key — so the order the entries were written in is kept for the sake of a
 * message about one run reading the same way twice, and is not something to build an answer from.
 */
public record Discharge(Map<ClassOfAPosition, ClassDisposition> classes,
                        Map<Generator.ArmOwed, ArmDisposition> arms) {

    /** Nothing asked for and nothing answered, which is the only run this is right for. */
    public static final Discharge NOTHING = new Discharge(Map.of(), Map.of());

    public Discharge {
        // Neither half of an entry missing. A key with nothing under it is an obligation that was
        // asked about and not answered for, which is the absence every value here is arranged to
        // have none of — and it satisfied a check written over the keys alone.
        classes = Ordered.copyOf(classes);
        arms = Ordered.copyOf(arms);
    }

    /** What became of one class, or null where this run was not asked about it. */
    public ClassDisposition at(ClassOfAPosition owed) {
        return classes.get(owed);
    }

    /** What became of one arm, or null where this run was not asked about it. */
    public ArmDisposition at(Generator.ArmOwed owed) {
        return arms.get(owed);
    }

    /**
     * The same, asked at one of the places a run through the arm is recorded.
     *
     * <p>For a reader holding an occurrence rather than the arm — a finding names one site of the
     * arm it is about, and what the search was asked for is the arm and every splice of it. Asked
     * with the site's own probe as though it were the whole key, such a reader found nothing
     * whenever the arm stood in the body more than once.
     */
    public ArmDisposition at(souther.compiler.coverage.ArmProbe probe) {
        for (Map.Entry<Generator.ArmOwed, ArmDisposition> each : arms.entrySet()) {
            if (each.getKey().recordedAt(probe)) {
                return each.getValue();
            }
        }
        return null;
    }
}
