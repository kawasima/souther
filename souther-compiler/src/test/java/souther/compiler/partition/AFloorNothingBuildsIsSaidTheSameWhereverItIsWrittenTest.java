package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Why nothing was written, where a floor asks for more than a row is built to carry.
 *
 * <p>Two sentences are being kept apart. "Every value tried was refused" is about the model and sends
 * a reader looking for the rule that refuses them; "nothing here composes one" is about this compiler,
 * and a list of a hundred thousand is a value somebody could write and this will not. Which of the two
 * a position gets was read off its type alone, so a floor written on the record that has the position
 * got the wrong one — the same asymmetry as #650, in the half that explains rather than builds.
 */
class AFloorNothingBuildsIsSaidTheSameWhereverItIsWrittenTest {

    /**
     * The second parameter builds nothing, so the reason the row gives is that parameter's.
     *
     * <p>Only that one. A check refusing everything has the first parameter fail first, and the row
     * then reports the first parameter's reason — which is a fact about a `Bool` and would be the
     * same sentence whatever the second parameter's rules said.
     */
    private static final Generator.CandidateCheck REFUSED = Generator.CandidateCheck.refusing(
            (parameter, _) -> parameter == 1 ? Optional.of("refused") : Optional.empty());

    private static Generator.UnresolvedCombination.Reason reasonFor(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        assertNotNull(sigs, "the model did not compile");
        InputDomain domain = InputDomain.of(sigs.get(behavior), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.of(behavior, domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        FillResult filled = Generator.fill(
                MeasuredInput.of(behavior, domain.reading(rules), partitioning),
                List.of(), REFUSED, Budgets.generation());
        assertFalse(filled.unresolved().isEmpty(), "nothing was written and nothing said why");
        return filled.unresolved().get(0).reason();
    }

    private static final String NEWTYPE = """
            module example.huge

            data Huge = List<Int>
                invariant vast = List.length(value) >= 100000

            data Ok = { n: Int }

            behavior countThem : (flag: Bool, h: Huge) -> Ok
                constructs Ok

            let countThem (flag, h) = Ok { n = List.length(h.value) }
            """;

    private static final String RECORD = """
            module example.huge

            data HugeBag =
                { xs: List<Int>
                }
                invariant vast = List.length(xs) >= 100000

            data Ok = { n: Int }

            behavior countThem : (flag: Bool, h: HugeBag) -> Ok
                constructs Ok

            let countThem (flag, h) = Ok { n = List.length(h.xs) }
            """;

    /** The reading that already worked, held so the one below is read as the record's floor arriving
     * rather than as the sentence changing. */
    @Test
    void aTypesOwnFloorNothingBuildsSaysNothingComposesOne() {
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                reasonFor(NEWTYPE, "countThem"));
    }

    @Test
    void aRecordsFloorNothingBuildsSaysTheSame() {
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                reasonFor(RECORD, "countThem"));
    }

    /** And a position with no floor at all keeps the sentence about the model: its values were built
     * and something refused them. */
    @Test
    void aPositionWithNoFloorStillSaysItsValuesWereRefused() {
        assertEquals(Generator.UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED,
                reasonFor("""
                        module example.plain

                        data Bag = { xs: List<Int> }

                        data Ok = { n: Int }

                        behavior countThem : (flag: Bool, bag: Bag) -> Ok
                            constructs Ok

                        let countThem (flag, bag) = Ok { n = List.length(bag.xs) }
                        """, "countThem"));
    }
}
