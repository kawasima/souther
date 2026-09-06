package souther.compiler.check;

import souther.compiler.WhatWasCompiled;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who may be an algebra over a whole clause tree.
 *
 * <p>{@link ClauseReading} is the algebra of the tree: what a leaf says, what a conjunction and a
 * choice of two readings come to, and the walk that carries the environment down and the answer up.
 * A reading that interprets only the leaves is not one of these, and typing it as one leaves it
 * owing the connectives — which is not an empty obligation, because a choice is not compositional
 * under either language on its own. Where an alternative is one nobody can take turns on what the
 * values and the ranges left together, so a choice settled inside one language drops the branch that
 * language could refuse and keeps the branch the other one did.
 *
 * <p>That is how the reading of ranges came to carry a second spelling of what a choice does. It
 * ran nowhere — the whole reading asks it for leaves — and it read as available, under a comment
 * stating the refuted rule as one in force.
 *
 * <p>Read off the compiled classes and not off the source, so an interface put between a reading and
 * this one arrives here as a row. Read from what this module compiled, so a fold a test builds to
 * look at the walk is not in the population: such a fold is an algebra of the whole tree and answers
 * for its own connectives, which is the thing this permits rather than the thing it refuses.
 */
class OnlyAFoldOverAWholeClauseTreeIsOneTest {

    /**
     * The ones that read a whole clause tree, and what each of them is.
     *
     * <p>{@code StatedByClauses.Reading} is the reading of a declaration's clauses, which is where
     * both languages are held and where the choices of a rule are decided. {@code ExpansionCost}
     * counts what a clause would expand to, over the same connectives and by the same walk, which
     * is why it is this and not a walk of its own. {@code Conditions.Stating} says what a condition
     * states as relations: it composes a conjunction and takes a choice whole, since one part of a
     * choice holds and it cannot say which — which is answering for the connectives rather than
     * leaving them owed. {@code Predicates.Owing} says what a clause owes and {@code
     * Predicates.Quantifiers} says which quantifiers it states: two answers about one clause, each
     * reading it to the depth its own answer composes to, and both meeting the connectives the
     * shape already recognised rather than either recognising them again. {@code
     * Predicates.Assuming} says what taking a condition in makes known, which is the third answer
     * over that same shape; it composes a conjunction by taking its right half under what the left
     * half left, so what it reads a clause into is a state and not a value.
     *
     * <p>A row for a reading of one language — the values, the ranges, or whatever is written next
     * — is the edge this exists to refuse. Those interpret leaves, and the fold that composes them
     * is the one above.
     */
    private static final Set<String> FOLDS = Set.of(
            "souther.compiler.check.Conditions$Stating",
            "souther.compiler.check.ExpansionCost",
            "souther.compiler.check.Predicates$Assuming",
            "souther.compiler.check.Predicates$Owing",
            "souther.compiler.check.Predicates$Quantifiers",
            "souther.compiler.check.StatedByClauses$Reading");

    @Test
    void theOnlyAlgebrasOverAClauseTreeAreTheOnesThatReadOne() {
        assertEquals(FOLDS, WhatWasCompiled.implementing(ClauseReading.class));
    }
}
