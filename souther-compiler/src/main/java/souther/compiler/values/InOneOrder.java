package souther.compiler.values;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Writing out what a value holds no order of.
 *
 * <p>What these values hold unordered is kept in the order it arrived, so that what a compilation
 * writes out comes out the same on two runs of it. That order is a fact about how a value was built
 * and not about what it is — two of them that are equal were built two ways — so a value that wrote
 * it out would be showing what it just said it is not, and a reader holding two equal values would
 * see two different things.
 *
 * <p>So the order a value is written in is settled here, and nowhere a value is compared. Which is
 * the same boundary a block's positions are put in an order at: how a thing reads is decided where
 * it is read, and how it is held is decided by what holds it.
 *
 * <p>Read off what each part is written as and not off the parts themselves. A value whose parts
 * are written in one order is written in one order however they are held, so this composes down a
 * value made of values without any of them being asked to sort what is inside it twice.
 */
final class InOneOrder {

    private InOneOrder() {
    }

    /** These, written in one order whichever order they are held in. */
    static String of(Collection<?> these) {
        List<String> out = new ArrayList<>();
        these.forEach(each -> out.add(String.valueOf(each)));
        out.sort(null);
        return out.toString();
    }

    /** The same, of what a map holds. */
    static String of(Map<?, ?> these) {
        List<String> out = new ArrayList<>();
        these.forEach((key, value) -> out.add(key + "=" + value));
        out.sort(null);
        return out.toString();
    }
}
