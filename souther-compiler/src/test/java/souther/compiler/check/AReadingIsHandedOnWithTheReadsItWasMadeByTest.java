package souther.compiler.check;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.regex.PatternPlan;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading handed to a second reader is handed the reads it was made by.
 *
 * <p>A question of the store is kept by what it read: an edit reaches it because something it read
 * came out different. The reading of a declaration is made by one question and handed to the next,
 * and the next read nothing to have it — so unless what the making read is read for it too, it
 * holds an answer made from rules it does not depend on, and an edit to those rules reaches it by
 * whatever other roads happen to exist.
 *
 * <p>Asked of the lender rather than through an edit. Which roads exist besides this one is a fact
 * about the questions a model happens to raise: over the corpus the readings a question is handed
 * are of declarations it reads by another road as well, so an edit reaches it either way and a test
 * watching a compile would pass whether or not anything was handed on. What is held here is the
 * thing that must be true of every lending, however many roads a given model leaves.
 */
class AReadingIsHandedOnWithTheReadsItWasMadeByTest {

    /** A store that says what it was asked to do and what it handed back to be read again. */
    private static final class Watched implements StoreWork {

        private final List<String> handedOn = new ArrayList<>();
        private int watched;

        @Override
        public <T> Made<T> watching(Supplier<T> work) {
            watched++;
            return new Made<>(work.get(), () -> handedOn.add("read again"));
        }
    }

    private static final String MODULE = """
            module demo

            data Amount = Int
                invariant value >= 0
                invariant value <= 100
            """;

    @Test
    void theSecondReaderIsHandedTheReadingAndTheReadsItWasMadeBy() {
        Compilation compilation = Compilation.ofSources(List.of(MODULE), ModulePath.EMPTY);
        compilation.answerEverything();
        RuleReadingSource source = RuleReadings.of(compilation, "demo");
        TypeSymbol.AtModule amount = TypeSymbols.declared(new TypeKey("demo", "Amount"));
        Watched store = new Watched();
        LentReadings lender = new LentReadings(DeclarationReadings.NONE, () -> 1, store);

        InvariantChecker.Seeded made =
                InvariantChecker.seedFields(amount, source, AS_THE_COMPILE_READS, lender);
        assertEquals(1, store.watched, "the making is what the store watched");
        assertEquals(List.of(), store.handedOn, "and nothing was handed on to make it");

        InvariantChecker.Seeded lent =
                InvariantChecker.seedFields(amount, source, AS_THE_COMPILE_READS, lender);
        assertTrue(made == lent, "the second reader is handed the reading the first made");
        assertEquals(1, store.watched, "which is not made again");
        assertEquals(List.of("read again"), store.handedOn,
                "and what making it read is read for the second reader, which read nothing to have"
                        + " it and would otherwise be kept over an edit to the rules it is of");
    }

    /** The terms the compilation reads under, said again here: what a reading is made under is
     *  handed to it, and a reader that could pick one up is a reader two readings can differ by. */
    private static final ReadingPolicy AS_THE_COMPILE_READS = new ReadingPolicy(64, 1000,
            PatternPlan.Budget.OF_ADMITTED_VALUES, PatternPlan.Budget.OF_WHAT_A_RULE_LEAVES);
}
