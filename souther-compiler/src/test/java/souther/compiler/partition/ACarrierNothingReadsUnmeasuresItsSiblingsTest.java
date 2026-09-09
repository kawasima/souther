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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A position whose rules one reader cannot read does not take its siblings' measurements with it.
 *
 * <p>Whether a row can be written at an edge is asked of the whole value a position sits in — a rule
 * about some other field can refuse to be part of any value with this edge in it — so one unreadable
 * rule anywhere under a parameter puts every boundary under it beyond what anything promises. That is
 * the right shape, and it is why the reading has to be complete for the rules it can actually read.
 *
 * <p>It was not. Whether an ordered rule became a bound was answered by the reader that maps rules to
 * the checks a decoder emits, which knows about patterns and sizes and has no word for a comparison
 * of dates — while the interval algebra read the same clause as a bound without difficulty. So a
 * {@code Date} field with an invariant made its record's reading incomplete, and the {@code Int}
 * beside it lost its two boundaries from the denominator: an axis nobody had touched stopped being
 * measured because of what its neighbour was declared as.
 *
 * <p>What fixes it is one interpreter for ordered rules, taking the carrier from the ordered type.
 * What this holds is the consequence: adding a bounded {@code Date} field to a record leaves every
 * other position's obligations exactly as they were.
 */
class ACarrierNothingReadsUnmeasuresItsSiblingsTest {

    /** A record with one bounded {@code Int} field. */
    private static final String ALONE = """
            module example.beside

            data Slot = Int
                invariant value >= 0 && value <= 10

            data Span = { a: Slot }

            data Wide
            data Shape = Wide

            behavior classify : (s: Span) -> Shape
            let classify (s) = Wide
            """;

    /** The same record with a bounded {@code Date} field beside it. Nothing about `a` changed. */
    private static final String BESIDE = """
            module example.beside

            data Slot = Int
                invariant value >= 0 && value <= 10

            data Day = Date
                invariant value >= Date("2020-01-01") && value <= Date("2020-01-02")

            data Span = { a: Slot, d: Day }

            data Wide
            data Shape = Wide

            behavior classify : (s: Span) -> Shape
            let classify (s) = Wide
            """;

    /**
     * The whole of what {@code a} is measured at, before and after the date arrives beside it.
     *
     * <p>Both halves in one assertion, because only the second of them ever moved: the obligations
     * were there either way and it was their standing that went. Held apart, the first would read as
     * the regression and pass on the defect.
     */
    @Test
    void aBoundedDateBesideAnIntLeavesTheIntMeasuredExactlyAsItWas() {
        assertEquals(measured(ALONE, "s.a"), measured(BESIDE, "s.a"),
                "`a` is declared identically in both");
    }

    /** And the date's own edges are obligations too, written as dates, so the agreement above is not
     * two empty lists. */
    @Test
    void theDatesOwnEdgesAreObligationsWrittenAsDates() {
        assertEquals(List.of("ON 2020-01-01 writable", "ON 2020-01-02 writable"),
                measured(BESIDE, "s.d"));
        assertEquals(List.of("ON 0 writable", "ON 10 writable"), measured(BESIDE, "s.a"));
    }

    /** Every obligation of one position: where it is, and whether anything promises a row can be
     * written there. */
    private static List<String> measured(String source, String path) {
        Read read = read(source, "classify");
        Axis axis = read.partitioning().axes().stream()
                .filter(a -> a.path().toString().equals(path)).findFirst().orElseThrow();
        String standing = read.partitioning().edgeIsKnownWritable(axis.term())
                ? " writable" : " not known to be writable";
        return Partitions.bordersOf(axis, read.reading(),
                        read.reading().runsBetween(axis.term()), new LinesRead()).stream()
                .flatMap(border -> java.util.stream.Stream.of(PointRole.ON, PointRole.OFF)
                        .filter(role -> border.demand(role).criterion() != null)
                        .map(role -> role + " "
                                + border.demand(role).criterion().asked(border.cut().of()).substring(2) + standing))
                .sorted().toList();
    }

    private record Read(Partitions.Partitioning partitioning,
                        souther.compiler.inputs.Quantities reading, RuleReadingSource rules) {}

    private static Read read(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs.get(behavior), "the model under test compiles");
        InputDomain domain = InputDomain.of(sigs.get(behavior), rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        return new Read(Partitions.of(behavior, domain, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                domain.quantities(rules), rules);
    }
}
