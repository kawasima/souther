package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Emptiness;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a sequence holds refuses the input only where the sequence cannot be empty.
 *
 * <p>A container that may hold none is a value whatever is true of what it would hold, so an element
 * declaration nothing can build leaves a row an author can still write. Where a rule says the
 * container holds at least one, the same declaration leaves the input nothing — and the proof says
 * which of the two it was.
 *
 * <p>The same asymmetry the cases of a sum are read with: what is quantified over is the values the
 * model has, and an alternative nobody can take is only a refusal when there is no other.
 */
class AnElementNothingCanBuildRefusesOnlyANonEmptySequenceTest {

    private static final String MAY_BE_EMPTY = """
            module g

            data Item = { charge: Int }
                invariant impossible = charge >= 1 && charge <= 0
            data Holder = { items: List<Item> }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same, with the container told to hold something. */
    private static final String HOLDS_AT_LEAST_ONE = MAY_BE_EMPTY.replace(
            "data Holder = { items: List<Item> }",
            """
                    data Holder = { items: List<Item> }
                        invariant atLeastOne = List.length(items) >= 1""");

    /** And the same again with an element there is a value of, so that what refuses the input is
     *  the pair and not the rule about the length. */
    private static final String AND_AN_ELEMENT_THAT_STANDS =
            HOLDS_AT_LEAST_ONE.replace("    invariant impossible = charge >= 1 && charge <= 0\n",
                    "");

    @Test
    void anEmptySequenceIsAValueWhateverItsElementWouldBe() {
        assertEquals(Optional.empty(), emptinessOf(MAY_BE_EMPTY),
                "an empty list is a row this behavior takes");
    }

    @Test
    void andOneThatCannotBeEmptyIsRefusedByWhatItWouldHold() {
        Optional<EmptyInput> why = emptinessOf(HOLDS_AT_LEAST_ONE);

        assertTrue(why.isPresent(), "the list holds an Item and there is no Item to hold");
        Emptiness proof = ((EmptyInput.ProvedByTheRules) why.orElseThrow()).why();
        Emptiness.AtAField at = assertInstanceOf(Emptiness.AtAField.class, proof,
                "the lack is at the container, which is the place a reader is sent to");
        assertEquals(new Emptiness.AtAField.Where.In("h.items"), at.where());
        assertInstanceOf(Emptiness.NonEmptyCollectionWithNoElement.class, at.under(),
                "and it says which of the two facts it took to refuse the input");
    }

    @Test
    void andTheRuleAboutTheLengthRefusesNothingOnItsOwn() {
        assertEquals(Optional.empty(), emptinessOf(AND_AN_ELEMENT_THAT_STANDS),
                "a list of one Item is a row this behavior takes");
    }

    private static Optional<EmptyInput> emptinessOf(String source) {
        InputDomain read = reading(source, "read");
        return read.quantities(rulesOf(source)).emptiness();
    }

    private static RuleReadingSource rulesOf(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        return RuleReadings.of(compilation, compilation.modules().get(0));
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
