package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.EndSide;
import souther.compiler.query.Compilation;
import souther.compiler.query.Machines;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.KnownExtents;
import souther.compiler.values.StringFacts;
import souther.compiler.values.StringMachineAnswers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading taken again with rules left out is answered out of the string machines the reading it
 * was taken from came to.
 *
 * <p>A machine is a fact about a plan, a set, or a language beside a stretch, and which reading
 * built it does not enter into what it says. Leaving a declaration's clauses out changes which
 * plans a reading meets; it does not change what any one of them admits. So a counterfactual over
 * clauses about numbers meets the same string rules as the reading it is taken from, and asks its
 * string questions of what that reading already holds.
 *
 * <p>Two readings answered from the same facts is also what makes attributing an end a comparison.
 * A borrowed machine is not paid for out of the borrower's allowance, so a counterfactual left to
 * build its own would be the one reading of the two that could run out and answer a wider end —
 * and the end it was compared against would read as one its own clauses had moved.
 */
class ACounterfactualBorrowsTheMachinesTheReadingMadeTest {

    /**
     * A record whose fields are a string with a pattern and two numbers, and two declarations
     * putting a floor under one of the numbers.
     *
     * <p>The floor is what is attributed, and it is attributed by leaving clauses about numbers
     * out. The pattern is reached either way, so the counterfactual asks the same string questions
     * as the reading it comes from.
     */
    private static final String SOURCE = """
            module demo exposing ( Held, keep )

            data Code = String
                invariant String.matches("[A-Z]{2}-[0-9]{4}", value)

            data Common =
                { code: Code
                , lo: Int
                , hi: Int
                }
                invariant based = lo >= 100
                invariant far = hi >= lo + 10

            data Held = { ...Common }
                invariant near = hi >= lo + 5

            behavior keep : (h: Held) -> Held

            let keep (h) = h
            """;

    @Test
    void theCounterfactualMakesNoMachineTheReadingItComesFromHolds() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        FieldDomains reading = FieldDomains.of(
                TypeSymbols.declared(new TypeKey("demo", "Held")),
                RuleReadings.of(compilation, compilation.modules().get(0)),
                ReadAs.THE_COMPILATION_DOES,
                compilation.db().readings());

        long before = StringMachineAnswers.machinesMade();
        List<TypeSymbol.AtModule> holding =
                AReadingOfAPosition.holding(reading.at(RuleKey.of("hi")), EndSide.LOWER);

        assertFalse(holding.isEmpty(),
                "the floor under `hi` is held by whoever the counterfactual says, so one was taken");
        assertEquals(before, StringMachineAnswers.machinesMade(),
                "and it answered its string questions out of the machines the reading holds");
    }

    /**
     * The same, where what there was to borrow was only part of it.
     *
     * <p>What a counterfactual is handed is what the reading it comes from came to, which is what
     * it borrowed and what it built on top. Handed instead what the lender answers afterwards, a
     * counterfactual gets what there was to borrow before the reading built anything — and builds
     * everything the reading built, one step away from where it was building it before.
     *
     * <p>The lender here answers with the plans and none of the extents, so the reading borrows
     * some of its machines and makes the rest.
     */
    @Test
    void whatTheReadingBuiltOnTopIsHandedOnToo() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        TypeKey held = new TypeKey("demo", "Held");
        StringFacts whole = compilation.db().ask(new Machines.OfDeclaration(held)).value();
        assertFalse(whole.extents().isEmpty(),
                "the declaration's machines include extents, so leaving them out leaves work to do");
        StringFacts part = new StringFacts(whole.realized(), Map.of(), Map.of());

        long beforeReading = StringMachineAnswers.machinesMade();
        FieldDomains reading = FieldDomains.of(TypeSymbols.declared(held),
                RuleReadings.of(compilation, compilation.modules().get(0)),
                ReadAs.THE_COMPILATION_DOES,
                _ -> StringMachineAnswers.borrowing(part, KnownExtents.NONE));
        assertTrue(StringMachineAnswers.machinesMade() > beforeReading,
                "the reading builds what the lender had nothing to say about");

        long before = StringMachineAnswers.machinesMade();
        assertFalse(AReadingOfAPosition.holding(reading.at(RuleKey.of("hi")), EndSide.LOWER)
                        .isEmpty(),
                "the floor under `hi` is attributed, so a counterfactual was taken");
        assertEquals(before, StringMachineAnswers.machinesMade(),
                "and it is handed what the reading borrowed and what the reading made");
    }

    /**
     * The same reading, asked for what it holds rather than for who holds it.
     *
     * <p>A negative control on the count above: machines are made when this declaration is read at
     * all, so a count that never moves would say the reading asks no string questions rather than
     * that the counterfactual borrows the answers.
     */
    @Test
    void readingTheDeclarationMakesMachines() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();

        long before = StringMachineAnswers.machinesMade();
        FieldDomains.of(TypeSymbols.declared(new TypeKey("demo", "Held")),
                RuleReadings.of(compilation, compilation.modules().get(0)),
                ReadAs.THE_COMPILATION_DOES);

        assertTrue(StringMachineAnswers.machinesMade() > before,
                "a reading with nothing to borrow from builds the machines its rules come to");
    }
}
