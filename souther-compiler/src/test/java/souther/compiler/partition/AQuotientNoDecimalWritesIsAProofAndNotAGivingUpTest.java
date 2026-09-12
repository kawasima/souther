package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.SearchRegion;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.check.DeclaredSig;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A last position whose value no decimal writes says the level is out of reach, not that the search
 * gave up.
 *
 * <p>The last position of a form is divided rather than stepped to. Over positions whose values fill
 * the division is exact or it does not end, and a quotient that does not end is not a value a model
 * writes — so what it settles is that this prefix has no last value. Read as a search running out,
 * the level came back owed and unsettled instead of taken away.
 *
 * <p>Asked of the search and not of a report. What a report says about the level is decided further
 * up by whether the level is one the order names at all, and that question is answered before this
 * one is reached; the contract being checked here is the one between {@link LevelRealizer} and its
 * caller, which is where the difference between a proof and a search that stopped lives.
 */
class AQuotientNoDecimalWritesIsAProofAndNotAGivingUpTest {

    private static final String ONE_DECIMAL_FIELD = """
            module example.third

            data P = { b: Decimal }

            data Yes = { v: Int }

            behavior take : (p: P) -> Yes
                constructs Yes
            let take (p) = Yes { v = 1 }
            """;

    /**
     * {@code 3 * p.b = 1} over decimals. A third is not a decimal a model writes, so no value of the
     * position puts the form at one — and nothing about this compiler's willingness to look enters
     * into it.
     */
    @Test
    void aLevelNoValueOfThePositionReachesIsOutOfReachAndNotUnsettled() {
        Standing.OfAForm aThird = new Standing.OfAForm(
                new LinearForm<>(BigDecimal.ZERO,
                        Map.of(value("b"), new BigDecimal("3"))),
                Map.of(value("b"), new Carrier.Dense()),
                LevelSpace.overFiniteDecimals(new BigDecimal("3")),
                new Criterion.AtTheLevel(new Level.ACount(Count.of(BigDecimal.ONE))));

        assertInstanceOf(Realization.Impossible.class, new LevelRealizer().realize(aThird, region(),
                        NothingTheDeclarationsRefuse.at()));
    }

    /** And one it does reach comes back as the row rather than as a proof, so the check above is not
     *  reading a search that refuses everything. */
    @Test
    void aLevelAValueDoesReachComesBackAsARow() {
        Standing.OfAForm aWhole = new Standing.OfAForm(
                new LinearForm<>(BigDecimal.ZERO,
                        Map.of(value("b"), new BigDecimal("3"))),
                Map.of(value("b"), new Carrier.Dense()),
                LevelSpace.overFiniteDecimals(new BigDecimal("3")),
                new Criterion.AtTheLevel(new Level.ACount(Count.of(new BigDecimal("6")))));

        assertInstanceOf(Realization.Found.class, new LevelRealizer().realize(aWhole, region(),
                        NothingTheDeclarationsRefuse.at()));
    }

    private static NumericTerm value(String field) {
        return new NumericTerm.ValueOf(TermPath.of("p").then(field));
    }

    private static SearchRegion region() {
        Compilation compilation = Compilation.ofSource(ONE_DECIMAL_FIELD, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get("take"), rules, ReadAs.THE_COMPILATION_DOES)
                .quantities(rules).region();
    }
}
