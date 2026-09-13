package souther.compiler.observe;

import java.util.List;
import java.util.Map;

/**
 * Which part of an answer stands for which part of what a text stated.
 *
 * <p>What a report needs to write the two side by side. A map pairs its entries by key and a set
 * pairs its elements without an order, and both of those are questions about what being the same
 * value means — asked here by the step the comparison asks them by. A report that found them from
 * what the two are written as would be a second answer to that: two entries the comparison matched
 * but a rendering could not tell apart would be written as though the answer held neither.
 *
 * <p><b>Worked out when a report wants it, rather than kept by every comparison that holds.</b> A
 * verdict is asked of every row and nearly all of them hold; this is asked of the ones that did
 * not. So it is a second walk over the two values, under the rule the first one used and not a
 * rule of its own — which is what there is to hold to, and is why the one step that finds which
 * value a statement is about is written once and called from both.
 *
 * <p><b>A correspondence and not a verdict.</b> Nothing here says the two are the same; it says
 * which stood against which while that was being settled. A part that stands against nothing is
 * said as {@link Nothing}, which is what a reader writing it out has to know: there is no statement
 * putting it anywhere.
 *
 * <p>Over what the answer holds, because the answer is what is written out. A map is the one shape
 * where the statement's order is carried instead — {@link Entries} says the answer's entries in the
 * order the text wrote the ones it wrote, and the rest after them, because a map holds no order of
 * its own and the text's is the only one either side has.
 */
public sealed interface Alignment {

    /** Nothing of the statement stands against this, so nothing about it is the text's. */
    record Nothing() implements Alignment {}

    /** A value with no parts, or one whose parts nothing here pairs. */
    record Leaf() implements Alignment {}

    /** A construction, by the name each field is held under. A field the statement does not write
     *  is absent from the map rather than held as {@link Nothing}, which reads the same and says it
     *  in the place a reader looks. */
    record Built(Map<String, Alignment> fields) implements Alignment {}

    /** A sequence, one entry per element the answer holds, in the answer's own order. Which element
     *  of the statement each stands for is the pairing — positional for a list, and for a set the
     *  one the comparison found. */
    record Elements(List<Alignment> byElement) implements Alignment {}

    /** A mapping: the answer's entries the text wrote, in the order it wrote them, and then the ones
     *  it did not. */
    record Entries(List<Placed> written, List<ObservedValue.Entry> rest) implements Alignment {}

    /** One entry of the answer, under the statement that put it where it is. */
    record Placed(ObservedValue.Entry entry, Alignment under) {}
}
