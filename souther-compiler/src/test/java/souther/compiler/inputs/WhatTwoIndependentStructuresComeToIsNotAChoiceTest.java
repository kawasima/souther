package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two structures a value has at once are a conjunction, and the cases of one of them are a choice.
 *
 * <p>The same three answers are folded two ways. Over the alternatives of one sum, one that may
 * stand is the whole answer and every one of them has to be impossible for the sum to be; over two
 * structures a row has both of, one that is impossible is the whole answer and every one of them has
 * to be possible for the row to be. Folded alike, a sum this reading never finished with hides what
 * the sum beside it proved — and which of the two a caller hears about is settled by the order the
 * fields happen to be declared in.
 */
class WhatTwoIndependentStructuresComeToIsNotAChoiceTest {

    /**
     * A sum this walk turns back at, beside one whose every case the rules refuse.
     *
     * <p>{@code Tree} is read as far as it goes and no further, so nothing is known about the sums
     * under it. {@code Q} is known: neither of its cases has a value, so no value of the input has
     * one either — whatever is or is not known about the tree beside it.
     */
    private static final String UNREAD_BESIDE_REFUSED = """
            module g

            data Leaf = { n: Int }
            data Node = { left: Tree, right: Tree }
            data Tree = Leaf | Node

            data A = { x: Int }
                invariant impossible = x >= 1 && x <= 0
            data B = { y: Int }
                invariant impossible = y >= 1 && y <= 0
            data Q = A | B

            data Holder = { tree: Tree, q: Q }

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same two fields, declared the other way round. */
    private static final String REFUSED_BESIDE_UNREAD =
            UNREAD_BESIDE_REFUSED.replace("data Holder = { tree: Tree, q: Q }",
                    "data Holder = { q: Q, tree: Tree }");

    /** A sequence that cannot be empty, of an element there is no value of, beside the same
     *  unfinished tree. */
    private static final String UNREAD_BESIDE_A_DEAD_ELEMENT = """
            module g

            data Leaf = { n: Int }
            data Node = { left: Tree, right: Tree }
            data Tree = Leaf | Node

            data Item = { charge: Int }
                invariant impossible = charge >= 1 && charge <= 0

            data Holder = { tree: Tree, items: List<Item> }
                invariant atLeastOne = List.length(items) >= 1

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    @Test
    void aSumNothingIsKnownAboutDoesNotHideWhatTheSumBesideItRefuses() {
        for (String source : List.of(UNREAD_BESIDE_REFUSED, REFUSED_BESIDE_UNREAD)) {
            assertTrue(emptinessOf(source).isPresent(),
                    "no value of Q exists, so no row does, whatever was read of the tree");
        }
    }

    @Test
    void andWhichOfTheTwoIsDeclaredFirstIsNoPartOfTheAnswer() {
        assertEquals(emptinessOf(UNREAD_BESIDE_REFUSED), emptinessOf(REFUSED_BESIDE_UNREAD),
                "the same two structures, whichever order the fields were written in");
    }

    @Test
    void andASequenceThatCannotBeFilledIsNotHiddenEither() {
        assertTrue(emptinessOf(UNREAD_BESIDE_A_DEAD_ELEMENT).isPresent(),
                "the list holds an Item and there is no Item to hold");
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
