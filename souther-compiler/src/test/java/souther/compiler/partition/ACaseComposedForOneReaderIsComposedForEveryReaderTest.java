package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum every case of which is a record has values, and every reader that asks for one is told so.
 *
 * <p>{@code Values} and {@code Compose} are two ways of arriving at a representative and not two
 * answers about whether there is one. A reader that took the first and dropped the second reported
 * a position as having no value in the same report where another reader wrote one — which is issue
 * #651, and which is one function's answer read in five places.
 */
class ACaseComposedForOneReaderIsComposedForEveryReaderTest {

    private static final String MODULE = """
            module demo

            data Yen = Int invariant value >= 0
            data Ok = { n: Int }

            data Boxed = { a: Int, b: Int }
                invariant rising = a < b
            data Labelled = { s: String }
            data EveryCaseARecord = Boxed | Labelled

            data Wrapped = EveryCaseARecord

            data Bag = { xs: List<Int> }
                invariant enough = List.length(xs) >= 2

            behavior recordCases : (bill: Yen, v: EveryCaseARecord) -> Ok
                constructs Ok
            let recordCases (bill, v) = Ok { n = bill.value }

            example recordCases | (Yen(5), Boxed { a = 1, b = 2 }) -> Ok { n = 5 }
            """;

    private final RuleReadingSource rules = RuleReadings.ofSource(MODULE);
    private final RuleReadingContext reading = RuleReadingContext.unshared(rules,
            souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    private Type named(String name) {
        return Type.ref(TypeSymbols.declared(new TypeKey(rules.symbols().module(), name)));
    }

    private Type sum() {
        return named("EveryCaseARecord");
    }

    // --- the position a row is not about ---------------------------------------------------------

    // --- the same question, asked by the other readers --------------------------------------------

    /**
     * What an optional holds is what stands for its element, composed or not.
     *
     * <p>Held to the value and not to whether one could be arrived at. A class carrying a recipe
     * answers yes to that either way, and what went wrong was the step after it: the recipe was
     * read for values, it had none to hand over, and the class was written down as one nothing can
     * produce a value for.
     */
    @Test
    void theSomeOfAnOptionalSumOfRecordsStandsForAComposedCase() {
        PartitionClass some = PartitionClasses.of(new Type.OptionOf(sum()), reading, Set.of()).stream()
                .filter(each -> each.id().equals("Some")).findFirst().orElseThrow();

        List<FixtureTemplate> stands =
                Partitions.standingFor(some.representatives(), reading, Set.of());

        assertTrue(stands.stream().anyMatch(each -> each.text().startsWith("Boxed {")),
                () -> "`Some` stands for a case of the element: " + stands);
    }

    /** A collection whose rules ask it to hold one is built from what stands for its element. */
    @Test
    void aCollectionRequiredToHoldOneOfASumOfRecordsIsBuilt() {
        List<FixtureTemplate> held =
                Witnesses.holding(TypeView.asWritten(new Type.ListOf(sum()), rules.symbols(),
                                rules.published()),
                        1, reading, Set.of());

        assertFalse(held.isEmpty(), "a list of one is built from a case of the sum");
    }

    /** Two of them, which is the reading that asks what the type divides into rather than what
     *  stands for it. */
    @Test
    void aSetOfTwoIsBuiltFromTwoCasesOfASumOfRecords() {
        List<FixtureTemplate> held =
                Witnesses.holding(TypeView.asWritten(new Type.SetOf(sum()), rules.symbols(),
                                rules.published()),
                        2, reading, Set.of());

        assertFalse(held.isEmpty(), "the two cases are two distinct values");
    }

    /** A name round the sum is a name round what stands for it. */
    @Test
    void aNewtypeOverASumOfRecordsHasARepresentative() {
        List<FixtureTemplate> stands = Partitions.representativesOf(named("Wrapped"), reading);

        assertTrue(stands.stream().anyMatch(each -> each.text().startsWith("Wrapped(")),
                () -> "written under the name the position wears: " + stands);
    }

    // --- what a class says it is, once ------------------------------------------------------------

    /** Every class of the sum offers a representative: none of them is a class nothing produces a
     *  value for. */
    @Test
    void everyCaseOfTheSumStandsForSomething() {
        for (PartitionClass each : PartitionClasses.of(sum(), reading, Set.of())) {
            assertFalse(Partitions.representativesOf(named(each.id()), reading).isEmpty(),
                    () -> "a record case stands for a value: " + each.id());
        }
    }

    /**
     * A case whose fields constrain each other is composed against that rule.
     *
     * <p>The walk that composes a case as an axis chooses one position at a time, each against what
     * the record's rules leave it once the ones before it are settled, which is the only way
     * {@code a < b} is met. Composed here with every field read from the rules as they stand before
     * anything is chosen, the value handed back is one the decoder refuses — and the position is
     * back to being one the two strategies disagree about.
     */
    @Test
    void aCaseWhoseFieldsConstrainEachOtherIsComposedAgainstThatRule() {
        List<FixtureTemplate> stands = Partitions.representativesOf(named("Boxed"), reading);

        assertTrue(stands.stream().anyMatch(each -> each.text().equals("Boxed { a = 0, b = 1 }")),
                () -> "each field against what the rules leave it: " + stands);
    }

    /**
     * A record composed here holds what its own rule says it holds.
     *
     * <p>The floor a record puts on a field is read wherever a value is chosen, and composing a
     * record is choosing one for every field of it. Read off the field's type alone, what comes
     * back is a value the record refuses at construction, and the row it was composed for is
     * reported as one every value tried was refused at.
     */
    @Test
    void aComposedRecordHoldsWhatItsOwnRuleAsksFor() {
        List<FixtureTemplate> stands = Partitions.representativesOf(named("Bag"), reading);

        assertTrue(stands.stream().anyMatch(each -> each.text().contains("xs = [")
                        && !each.text().contains("xs = []")),
                () -> "the floor its rule puts on the field: " + stands);
    }

    /**
     * A recipe of no values is not a recipe.
     *
     * <p>Held as one, a class offering nothing and saying nothing about why was a class the search
     * met and had no reading for, and what it wrote down was read as the position having no value.
     * A class that cannot produce one says so and says why, which is {@link
     * RepresentativeSource.Ungeneratable}; there is nothing left for an empty list to mean.
     */
    @Test
    void aRecipeOfNoValuesCannotBeWritten() {
        assertThrows(IllegalArgumentException.class, () -> RepresentativeSource.of(List.of()));
    }

}
