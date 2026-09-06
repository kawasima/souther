package souther.compiler.values;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;

/**
 * Where a reading gets a machine somebody has already made: what a plan admits, where a set of
 * strings stops on the order, and whether a language has a string inside a stretch of it.
 *
 * <p>All three are facts about values and nothing else. A plan says which strings a position
 * admits without saying who asked, and the set it comes to is the same set whichever reading asks
 * for it; where that set stops is a fact about the set; whether a language and a stretch of the
 * order share a string is a fact about the two. So none of them is worked out again for a second
 * reading of the same declaration, or for a second declaration whose rules come to the same
 * strings — and what makes that so is that the answer is filed under the plan, the set or the
 * pair, never under the declaration or the question that reached it.
 *
 * <p>What a reading spends is its own, and what it borrows is not spent. A machine lent here was
 * made under an allowance of the plan's own, so a reading whose allowance would have refused it is
 * handed the machine all the same — which is what {@link Allowance#besides} already does between two
 * questions of one position, carried across readings.
 *
 * <p>The one that lends nothing is what a reading holds where there is no store to ask, and it
 * answers the other two questions by working them out under the meter it is handed; a reading with
 * that one pays for every machine itself.
 */
public interface StringMachines {

    /** What {@code plan} admits, or null where nothing has been made of it; asking makes nothing
     *  and spends nothing of the asker's. */
    ValueSet lent(AdmittedPlan plan);

    /** Where the strings {@code set} holds stop on the order. */
    TextExtent extentOf(ValueSet set);

    /**
     * Whether any string {@code language} admits lies inside {@code held}, whose ends are strings.
     *
     * <p>{@code meter} is what the asker may build where nothing is lent; a lender that answers
     * from what it has made spends nothing of it.
     */
    Emptiness inside(Language language, OrderedInterval held, Meter meter);

    /** The same, as the lender an allowance takes; the block is not part of the question. */
    default <A> Allowance.Known<A> lending() {
        return (_, plan) -> lent(plan);
    }

    /** Nothing lent anywhere; every extent and every emptiness worked out on the spot. */
    StringMachines NONE = new StringMachines() {
        @Override
        public ValueSet lent(AdmittedPlan plan) {
            return null;
        }

        @Override
        public TextExtent extentOf(ValueSet set) {
            return TextExtents.of(set);
        }

        @Override
        public Emptiness inside(Language language, OrderedInterval held, Meter meter) {
            return TextExtents.inside(language, held, meter);
        }
    };
}
