package souther.compiler.partition;

import souther.compiler.inputs.TermPath;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.ValueSet;

import java.util.Map;

/**
 * Positions that hold every value there is, for tests about what a search does on its own.
 *
 * <p>The companion of {@link NothingTheRulesSay}, and here for the same reason: a test handing over
 * sets that say something is testing the sets as well as the reader, and a reader that came back
 * empty-handed would leave which of the two answered unsaid.
 *
 * <p>Said per position rather than answered for any position asked about. A fixture that answered for
 * every path would let a test pass while the thing under test looks up a position nobody wired, which
 * is the state {@link AdmittedValues} exists to refuse.
 */
final class NothingTheDeclarationsRefuse {

    private NothingTheDeclarationsRefuse() {}

    /** A search over these positions, each of them holding every value of its order. */
    static WitnessSearch at(TermPath... positions) {
        Map<TermPath, ValueSet> sets = new java.util.LinkedHashMap<>();
        for (TermPath each : positions) {
            sets.put(each, ValueSet.ANY);
        }
        return new WitnessSearch(AdmittedValues.of(sets), PatternPlan.Budget.OF_A_WITNESS::meter);
    }
}
