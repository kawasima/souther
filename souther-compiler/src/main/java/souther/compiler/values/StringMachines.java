package souther.compiler.values;

/**
 * Where a reading gets a machine somebody has already made: what a plan admits, and where a set of
 * strings stops on the order.
 *
 * <p>Both are facts about a value and nothing else. A plan says which strings a position admits
 * without saying who asked, and the set it comes to is the same set whichever reading asks for it;
 * where that set stops is a fact about the set. So neither is worked out again for a second reading
 * of the same declaration, or for a second declaration whose rules come to the same strings — and
 * what makes that so is that the answer is filed under the plan or the set, never under the
 * declaration or the question that reached it.
 *
 * <p>What a reading spends is its own, and what it borrows is not spent. A machine lent here was
 * made under an allowance of the plan's own, so a reading whose allowance would have refused it is
 * handed the machine all the same — which is what {@link Allowance#besides} already does between two
 * questions of one position, carried across readings.
 *
 * <p>The one that lends nothing is what a reading holds where there is no store to ask, and it
 * answers where a set stops by working it out; a reading with that one pays for every machine
 * itself.
 */
public interface StringMachines {

    /** What {@code plan} admits, or null where nothing has been made of it; asking makes nothing
     *  and spends nothing of the asker's. */
    ValueSet lent(AdmittedPlan plan);

    /** Where the strings {@code set} holds stop on the order. */
    TextExtent extentOf(ValueSet set);

    /** The same, as the lender an allowance takes; the block is not part of the question. */
    default <A> Allowance.Known<A> lending() {
        return (_, plan) -> lent(plan);
    }

    /** Nothing lent anywhere; every extent worked out on the spot. */
    StringMachines NONE = new StringMachines() {
        @Override
        public ValueSet lent(AdmittedPlan plan) {
            return null;
        }

        @Override
        public TextExtent extentOf(ValueSet set) {
            return TextExtents.of(set);
        }
    };
}
