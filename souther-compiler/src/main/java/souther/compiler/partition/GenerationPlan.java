package souther.compiler.partition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one run of the generator is asked for, and the only place it is said.
 *
 * <p>A class of a position no row sits in and an arm of the body no row goes through are the two
 * things a row can be owed for. Which of them a run is asked about is established by whoever read
 * the rows, and this carries that reading whole — so a search does not work the list out a second
 * time, and nothing downstream decides from what a search happened to touch.
 *
 * <p><b>Settled before anything that can stop the run.</b> The classes would not link, the rows
 * could not be read: each of those ends a generation, and each used to end it somewhere that had
 * never asked what was owed. What a reader of such a result got was a reason about the run and no
 * word about the thing they were asking after. Made here, every way out holds the same list.
 *
 * <p><b>Lists rather than sets, and the order is the contract.</b> What a row is composed for is
 * written out beside it, and the order those are written in is this order — so a plan handed over
 * as a set would leave the rows of one model coming out in whatever order a hash gave them, which
 * is not the same order twice. Each obligation appears once, which is the whole of what being a set
 * gave.
 *
 * @param subject     the behavior a row would be written for
 * @param classesOwed one class of one position apiece, in the order they were gathered
 * @param armsOwed    one arm apiece, in the order the plan numbered them
 */
public record GenerationPlan(MeasuredInput subject, List<ClassOfAPosition> classesOwed,
                             List<Generator.ArmOwed> armsOwed) {

    public GenerationPlan {
        classesOwed = List.copyOf(classesOwed);
        armsOwed = List.copyOf(armsOwed);
        if (subject == null) {
            throw new IllegalArgumentException("a generation is asked for on behalf of a subject");
        }
        onlyOnce("class", classesOwed);
        onlyOnce("arm", armsOwed);
        // A class of another behavior, which is the same disagreement a measured input refuses among its
        // axes. Held here, one run would be answering for two behaviors and every sentence about
        // what it was asked for would be right about one of them.
        for (ClassOfAPosition each : classesOwed) {
            if (!each.at().behavior().equals(subject.behavior())) {
                throw new IllegalArgumentException(
                        "a class of " + each.at().behavior() + " in the plan for "
                                + subject.behavior() + ": " + each);
            }
        }
    }

    /** Whether anything at all is owed, which is what a run with nothing to do looks like. */
    public boolean isEmpty() {
        return classesOwed.isEmpty() && armsOwed.isEmpty();
    }

    private static void onlyOnce(String kind, List<?> owed) {
        Set<Object> seen = new LinkedHashSet<>(owed);
        if (seen.size() != owed.size()) {
            throw new IllegalArgumentException(
                    "the same " + kind + " is owed twice in one plan: " + owed);
        }
    }
}
