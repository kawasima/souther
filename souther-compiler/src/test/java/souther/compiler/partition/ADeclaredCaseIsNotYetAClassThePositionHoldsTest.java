package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a type declares and what a position can hold are two facts, crossed once.
 *
 * <p>{@code data StageI = Stage invariant value >= Qualified} declares three cases and holds two of
 * them: {@code StageI(Prospecting)} is refused at construction by the rule that says so. A class for
 * it would be a row nobody can write, asked for by the same report that has the rule in front of it.
 *
 * <p>Both halves are asserted because the crossing is only worth anything if the two are apart. The
 * reading of the declaration does not consult a rule, and the rule does not decide what the
 * declaration says — so neither is the other's input, and no order of working them out changes the
 * answer. Written as one reading handing its output to the other, the case comes back the day
 * somebody tidies the order.
 */
class ADeclaredCaseIsNotYetAClassThePositionHoldsTest {

    private static final String MODEL = """
            module demo

            data Ok
            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won
            data StageI = Stage invariant value >= Qualified

            behavior boundStage : (x: StageI) -> Ok
            let boundStage (x) = Ok

            behavior guardStage : (x: Stage) -> Ok
            let guardStage (x) = { guard x < Qualified else Ok
                Ok }
            """;

    private final RuleReadingSource rules = RuleReadings.ofSource(MODEL);

    /** The declaration's own answer, which the rule on the newtype does not enter into. */
    @Test
    void theTypeDeclaresEveryCaseWhateverItsRulesSay() {
        assertEquals(List.of("Prospecting", "Qualified", "Won"),
                PartitionClasses.of(Type.ref(TypeSymbols.declared(new TypeKey(rules.symbols().module(), "StageI"))), RuleReadingContext.unshared(rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES), java.util.Set.of()).stream()
                        .map(PartitionClass::id).toList());
    }

    /** And the position holds the ones its rules leave. */
    @Test
    void thePositionHoldsTheCasesItsRulesAdmit() {
        assertEquals(List.of("Qualified", "Won"), measured("boundStage"));
    }

    /** A {@code guard} takes no case away: it cuts the same order and everything either side of the
     *  cut is still a value the position holds. */
    @Test
    void aLineDrawnByABodyTakesNoCaseAway() {
        assertEquals(List.of("Prospecting", "Qualified", "Won"), measured("guardStage"));
    }

    private static List<String> measured(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence evidence = compilation.db()
                .ask(new Adequacy.Coverage("demo")).value().get(behavior);
        assertNotNull(evidence, behavior + " was measured");
        return evidence.axes().get(0).classes();
    }
}
