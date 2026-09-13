package souther.compiler.types;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A union is shown in one order however the set holding it was built.
 *
 * <p>Two writings of one union are one value: {@code Adult | Minor} and {@code Minor | Adult} hold
 * the same members and a union is a set of them. So a reader shown one of the two is being shown
 * something the value does not hold, and what decided it was whichever container the producer
 * reached for — a fact about this compiler's insides arriving in a sentence about somebody's model.
 *
 * <p><b>Held over the containers rather than over one recorded string.</b> A snapshot of what is
 * rendered fails when the order moves and says the rendering changed; it does not say that the
 * rendering was ever the producer's to decide. What is wanted is that it is not, which is a claim
 * about every way of building the set at once.
 *
 * <p>Nothing is given up by settling it. The order the members were written in reaches the set and
 * stops there — every sequence of them further on is taken back off the set — so there is no reading
 * of what somebody wrote that this replaces.
 */
class AUnionIsShownInOneOrderHoweverItWasBuiltTest {

    private static final TypeSymbol MINOR = TypeSymbols.declared(new TypeKey("example", "Minor"));
    private static final TypeSymbol ADULT = TypeSymbols.declared(new TypeKey("example", "Adult"));
    private static final TypeSymbol PENSIONER =
            TypeSymbols.declared(new TypeKey("example", "Pensioner"));

    /** Every way of holding the three members that this compiler has anywhere reached for. */
    private static List<Set<TypeSymbol>> everyWayOfHoldingThem() {
        Set<TypeSymbol> written = new LinkedHashSet<>(List.of(ADULT, MINOR, PENSIONER));
        Set<TypeSymbol> theOtherWay = new LinkedHashSet<>(List.of(PENSIONER, MINOR, ADULT));
        Set<TypeSymbol> hashed = new java.util.HashSet<>(List.of(ADULT, MINOR, PENSIONER));
        Set<TypeSymbol> against = new TreeSet<>(Comparator.reverseOrder());
        against.addAll(List.of(ADULT, MINOR, PENSIONER));
        return List.of(written, theOtherWay, hashed, against);
    }

    /**
     * The control. A set that iterated the same way whatever it was would make the claim below hold
     * of a renderer that walks its members, which is the renderer this is about.
     */
    @Test
    void theWaysOfHoldingThemDoNotAllIterateAlike() {
        Set<List<TypeSymbol>> walked = new LinkedHashSet<>();
        for (Set<TypeSymbol> each : everyWayOfHoldingThem()) {
            walked.add(List.copyOf(each));
        }

        assertTrue(walked.size() > 1,
                () -> "every way of holding the members walks them alike, so a renderer taking the"
                        + " order from the container would pass this as it stands: " + walked);
    }

    /** However it was built, one value. */
    @Test
    void everyWayOfHoldingThemIsOneUnion() {
        for (Set<TypeSymbol> each : everyWayOfHoldingThem()) {
            assertEquals(Type.union(everyWayOfHoldingThem().getFirst()), Type.union(each),
                    "a union is its members, and these are the same members");
        }
    }

    /** And one sentence, which is the half the value could not settle on its own. */
    @Test
    void everyWayOfHoldingThemIsShownAlike() {
        String first = Type.show(Type.union(everyWayOfHoldingThem().getFirst()));

        for (Set<TypeSymbol> each : everyWayOfHoldingThem()) {
            assertEquals(first, Type.show(Type.union(each)),
                    "what a reader is shown followed the container the members were put in");
        }
    }

    /** The order shown, said out loud, so that changing it is a change somebody made. */
    @Test
    void theOrderShownIsTheOneNamesAreShownIn() {
        assertEquals("Adult | Minor | Pensioner",
                Type.show(Type.union(everyWayOfHoldingThem().getFirst())));
    }
}
