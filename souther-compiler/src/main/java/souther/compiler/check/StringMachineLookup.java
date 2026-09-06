package souther.compiler.check;

import souther.compiler.types.TypeKey;
import souther.compiler.values.StringMachineAnswers;

/**
 * Where a reading of a declaration gets the answers about its string machines.
 *
 * <p>One question and one input: which declaration. What comes back is not a value but the thing a
 * reading asks as it goes ({@link StringMachineAnswers}), so this is a capability and is handed to
 * a reading as one — made where a store is to hand, passed along with the reading, and never kept
 * in anything a store answers with. A reading that holds nothing to ask is given {@link #NONE} and
 * works every machine out itself.
 */
@FunctionalInterface
public interface StringMachineLookup {

    /** The answers for a reading of {@code declaration}. */
    StringMachineAnswers of(TypeKey declaration);

    /** Nothing made anywhere, for a reading with no store to ask. */
    StringMachineLookup NONE = _ -> StringMachineAnswers.NONE;
}
