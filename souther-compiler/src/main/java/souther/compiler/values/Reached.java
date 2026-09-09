package souther.compiler.values;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One reading read against a relation finer than its own, so that a block of that relation reaches
 * what the reading describes.
 *
 * <p>What a choice between two readings is worked out in is finer than either of them — each side's
 * equalities hold only where both state them — so a block of it is inside one block of every
 * alternative, and what an alternative describes there is described of it. The step to each
 * alternative is worked out once and kept, because it is taken again for every block the choice
 * names.
 *
 * @param <A> what a position is called
 */
record Reached<A>(PlannedValues.Settled<A> of,
                  Map<PlannedHeld.Alternative<A>, Refinement<A>> alternatives) {

    /** {@code of} read against {@code finer}, whose blocks the asks below are in. */
    static <A> Reached<A> of(PlannedValues.Settled<A> of, Sameness<A> finer) {
        if (!(of.held() instanceof PlannedHeld.Alternatives<A> boxes)) {
            return new Reached<>(of, Map.of());
        }
        Map<PlannedHeld.Alternative<A>, Refinement<A>> out = new LinkedHashMap<>();
        boxes.boxes().forEach(box -> out.put(box, Refinement.of(finer, box.sameness())));
        return new Reached<>(of, out);
    }

    /** What this reading describes at {@code block}, every one of its alternatives being asked at
     *  the block of its own that holds those positions. */
    AdmittedPlan at(Sameness.Block<A> block) {
        return switch (of.held()) {
            // A reading with no alternatives holds its positions apart, so a block of anything
            // finer than it is one position — and the meet is over that one, said this way rather
            // than by taking it out.
            case PlannedHeld.Nothing<A> _ -> AdmittedPlan.meeting(block.members().stream()
                    .map(each -> of.perPosition().getOrDefault(each, AdmittedPlan.ANY)).toList());
            case PlannedHeld.Alternatives<A> boxes -> AdmittedPlan.joining(boxes.boxes().stream()
                    .map(box -> box.get(alternatives.get(box).coarseBlockOf(block))).toList());
        };
    }
}
