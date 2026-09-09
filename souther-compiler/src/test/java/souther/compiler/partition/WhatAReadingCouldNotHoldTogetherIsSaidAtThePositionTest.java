package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.PositionValuesNotSeparated;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reading that ran to the end of the rules and could not hold what they say together says so at
 * the positions it read.
 *
 * <p>Not among what went unread. Every rule arrived and every rule was taken in, and what is left is
 * a product standing for a relation it is wider than — so an author sent to look for a clause this
 * compiler could not read would be looking for one that does not exist. Issue #877, at the surface
 * a report is written from.
 *
 * <p>Said whether or not the position came back divided, which is what tells it from a stop. A stop
 * is what a position is left with when nothing answered for it; this is a qualification of the
 * classes themselves, and a position with classes is exactly where it matters — a class made out of
 * a set wider than the rules leave is a class no value can be in.
 */
class WhatAReadingCouldNotHoldTogetherIsSaidAtThePositionTest {

    private static Partitions.Partitioning of(String source, String behavior) {
        Compilation compilation = Compilation.ofSources(List.of(source),
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().getFirst();
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of(behavior,
                InputDomain.of(sigs.get(behavior), rules, souther.compiler.query.ReadAs.MERGING_WHAT_A_CHOICE_LEAVES), rules, souther.compiler.query.ReadAs.MERGING_WHAT_A_CHOICE_LEAVES);
    }

    /** The witness of issue #877: two invariants, each a choice reaching across both fields. */
    @Test
    void aChoiceAcrossTwoPositionsIsSaidAtEachOfThem() {
        Partitions.Partitioning read = of("""
                module demo

                data Taken

                data R = { a: String, b: String }
                    invariant one = (a == "5" && b == "0") || (a == "6" && b == "1")
                    invariant two = (a == "5" && b == "0") || (a == "6" && b == "0")

                behavior take : (r: R) -> Taken
                """, "take");

        assertEquals(List.of(
                        new PositionValuesNotSeparated(TermPath.of("r").then("a")),
                        new PositionValuesNotSeparated(TermPath.of("r").then("b"))),
                read.notSeparated(),
                "both positions are read from the product that stands for the two clauses");
    }

    /** And a model whose clauses the reading holds exactly says nothing of the kind. */
    @Test
    void aClauseAtOnePositionLeavesNothingToSay() {
        Partitions.Partitioning read = of("""
                module demo

                data Taken

                data R = { a: String, b: String }
                    invariant one = a == "5" || a == "6"
                    invariant two = b == "0"

                behavior take : (r: R) -> Taken
                """, "take");

        assertEquals(List.of(), read.notSeparated(),
                "each clause is written at one position, so the product is what they admit");
    }
}
