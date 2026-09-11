package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.TypeView;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a position is offered, of a rule written several ways.
 *
 * <p>One rule about how much a value holds can be written out, stated through a helper, or written
 * as the denial of its opposite, and a rule about what a name wraps reaches the count through that
 * name. Those are spellings of one rule: the values they admit are the same values, so a reader
 * answering what the type admits answers the same thing for each of them. Answering less for one of
 * them offers a position a value the construction refuses.
 *
 * <p>Held at the reading that decides what is offered rather than at the readings under it. Which of
 * them saw the rule is the fix's to settle; that a position is offered the same values whichever way
 * the author wrote it is the model's, and it is what an author would notice.
 */
class WhatAPositionIsOfferedDoesNotTurnOnHowTheRuleWasSpeltTest {

    /** A model to read a position off, held to compiling. A rule the language refuses still reaches
     * these readers and still comes back a number, so a model asserted only to have symbols can pin
     * an answer no program could have written. */
    private record Model(RuleReadingSource rules, String module) {

        TypeView view(String type) {
            return TypeView.of(new Type.Ref(TypeSymbols.declared(new TypeKey(module, type))),
                    rules.symbols());
        }

        /** How many a value of the position has to hold. */
        int least(String type) {
            return Partitions.leastHeld(view(type), rules);
        }

        /** And how many it may. */
        int most(String type) {
            return DeclaredBounds.mostCountOf(view(type), rules);
        }
    }

    private static Model modelOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        assertNotNull(rules, "the model did not compile");
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model under test is a program that can be written");
        return new Model(rules, module);
    }

    /** The rule written out, which is the reading that already worked. */
    @Test
    void aFloorWrittenOutIsTheFloor() {
        assertEquals(2, modelOf("""
                module example.bag

                data Bag = List<Int>
                    invariant atLeastTwo = List.length(value) >= 2
                """).least("Bag"));
    }

    /** The same rule stated through a helper. What stands between the clause and the comparison is
     * a binding the expansion left, and a binding is how a reading reaches a rule rather than
     * anything the rule says. */
    @Test
    void aFloorStatedThroughAHelperIsTheSameFloor() {
        assertEquals(2, modelOf("""
                module example.bag

                let atLeastTwo (xs: List<Int>): Bool = List.length(xs) >= 2

                data Bag = List<Int>
                    invariant ok = atLeastTwo(value)
                """).least("Bag"));
    }

    /** And written as the denial of its opposite, which admits the same values. */
    @Test
    void aFloorWrittenUnderADenialIsTheSameFloor() {
        assertEquals(2, modelOf("""
                module example.bag

                data Bag = List<Int>
                    invariant atLeastTwo = Bool.not(List.length(value) < 2)
                """).least("Bag"));
    }

    /** A cap the name above writes about what it wraps. The count is of the same list whichever
     * name it is reached through, so the rule caps the position as a rule written on the list
     * itself does. */
    @Test
    void aCapWrittenAboveTheNameItCountsThroughIsTheCap() {
        assertEquals(5, modelOf("""
                module example.chain

                data Kids = Inner
                    invariant atMostFive = List.length(value.value) <= 5

                data Inner = List<Int>
                    invariant atLeastOne = List.length(value) >= 1
                """).most("Kids"));
    }

    /**
     * And a rule that places no floor still places none.
     *
     * <p>Here because every case above is a reading that has to see more, and a reader answering
     * with a floor wherever a rule so much as names the count would pass all of them. A disequality
     * away from the bottom of the range leaves the values starting where they started.
     */
    @Test
    void aRuleThatPlacesNoFloorPlacesNone() {
        assertEquals(0, modelOf("""
                module example.bag

                data Bag = List<Int>
                    invariant notThree = List.length(value) /= 3
                """).least("Bag"));
    }
}
