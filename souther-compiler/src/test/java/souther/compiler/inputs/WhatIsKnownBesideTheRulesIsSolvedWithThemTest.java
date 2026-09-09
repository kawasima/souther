package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A fact a caller holds beside the rules is solved with them, not met onto their answer.
 *
 * <p>Projecting is not distributive over meeting. What the rules leave a form, met afterwards
 * against what each of its positions is known to be, is wider than what the rules and those facts
 * leave it together — and the difference is a rule going missing rather than a bound being loose.
 *
 * <p>A rule holding two positions at one apiece says nothing about a form that also names a third
 * the rules leave unbounded: the third can be taken as far below nothing as anybody likes, so the
 * form has no floor. Met afterwards against a floor the caller did have for that third position,
 * the sum of the three still has no floor, because meeting a bound on the whole form against a
 * bound on the whole form is all that is left to do. Put in first, the rule and the floor hold each
 * other up and the form stops where it should.
 *
 * <p>Asked of the input and not of one value's reading. The rules of every parameter are said
 * together here and what a caller took in is said onto them, so this is where a fact held beside the
 * rules meets them — and where it would be met onto an answer if anybody assembled one.
 */
class WhatIsKnownBesideTheRulesIsSolvedWithThemTest {

    /** Two positions a rule relates, and a third nothing here bounds. */
    private static final String TWO_RELATED_AND_A_THIRD = """
            module example.third

            data P = { x: Int, y: Int, z: Int }
                invariant oneOfXY = x + y >= 1

            data Taken

            behavior take : (p: P) -> Taken
            """;

    private static final NumericTerm X = value("x");
    private static final NumericTerm Y = value("y");
    private static final NumericTerm Z = value("z");

    @Test
    void aFloorPutInHoldsTheRuleUpAndMetAfterwardsDoesNot() {
        SearchRegion rules = region();

        // Nothing bounds the third, so the rules alone leave the sum nowhere in particular.
        assertNull(rules.runsBetween(sum()).min(), "the third can be as far below nothing as it likes");

        // And on its own the third is exactly what the caller said and no more, so a reader meeting
        // that onto the answer above would still have no floor for the sum.
        SearchRegion withAFloorUnderTheThird =
                rules.assuming(LinearForm.atom(Z), Rel.GE);
        assertEquals(Endpoint.inclusive(Count.of(0)), withAFloorUnderTheThird.runsBetween(Z).min());

        assertEquals(Endpoint.inclusive(Count.of(1)), withAFloorUnderTheThird.runsBetween(sum()).min(),
                "two of them come to one and the third is never below nought");
    }

    /** And a fact about a position the rules do not name settles nothing about them, which is the
     *  safe way. */
    @Test
    void aFactAboutAPositionTheRulesDoNotNameChangesNothing() {
        SearchRegion rules = region();

        assertEquals(rules.runsBetween(LinearForm.atom(X)),
                rules.assuming(LinearForm.atom(Z), Rel.GE)
                        .runsBetween(LinearForm.atom(X)));
    }

    private static LinearForm<NumericTerm> sum() {
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(X, BigDecimal.ONE);
        coefs.put(Y, BigDecimal.ONE);
        coefs.put(Z, BigDecimal.ONE);
        return new LinearForm<>(BigDecimal.ZERO, coefs);
    }

    private static NumericTerm value(String field) {
        return new NumericTerm.ValueOf(TermPath.of("p").then(field));
    }

    private static SearchRegion region() {
        Compilation compilation = Compilation.ofSource(TWO_RELATED_AND_A_THIRD, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get("take"), rules, ReadAs.THE_COMPILATION_DOES)
                .quantities(rules).region();
    }
}
